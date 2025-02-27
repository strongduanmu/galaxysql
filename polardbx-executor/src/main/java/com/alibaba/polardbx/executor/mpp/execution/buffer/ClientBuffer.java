/*
 * Copyright [2013-2021], Alibaba Group Holding Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.polardbx.executor.mpp.execution.buffer;

import com.alibaba.polardbx.common.utils.logger.Logger;
import com.alibaba.polardbx.common.utils.logger.LoggerFactory;
import com.alibaba.polardbx.executor.mpp.OutputBuffers;
import com.alibaba.polardbx.executor.mpp.Threads;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.airlift.units.DataSize;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import static com.alibaba.polardbx.executor.mpp.execution.buffer.BufferResult.emptyResults;
import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static java.lang.Math.toIntExact;
import static java.util.Objects.requireNonNull;

@ThreadSafe
public class ClientBuffer {

    private static final Logger log = LoggerFactory.getLogger(ClientBuffer.class);

    private static final AtomicLongFieldUpdater<ClientBuffer> bufferedBytesUpdater =
        AtomicLongFieldUpdater.newUpdater(ClientBuffer.class, "bufferedBytesLong");
    private static final AtomicLongFieldUpdater<ClientBuffer> currentSequenceIdUpdater =
        AtomicLongFieldUpdater.newUpdater(ClientBuffer.class, "currentSequenceIdLong");
    private final String taskInstanceId;
    private final OutputBuffers.OutputBufferId bufferId;

    private volatile long bufferedBytesLong = 0L;

    boolean preferLocal = false;

    @GuardedBy("this")
    private volatile long currentSequenceIdLong = 0L;

    @GuardedBy("this")
    private final LinkedList<SerializedChunkReference> pages = new LinkedList<>();

    @GuardedBy("this")
    private boolean noMorePages;

    @GuardedBy("this")
    private boolean isForceDestroy;

    // destroyed is set when the client sends a DELETE to the buffer
    // this is an acknowledgement that the client has observed the end of the buffer
    @GuardedBy("this")
    private final AtomicBoolean destroyed = new AtomicBoolean();

    @GuardedBy("this")
    private PendingRead pendingRead;

    public ClientBuffer(String taskInstanceId, OutputBuffers.OutputBufferId bufferId) {
        this.taskInstanceId = requireNonNull(taskInstanceId, "taskInstanceId is null");
        this.bufferId = requireNonNull(bufferId, "bufferId is null");
    }

    public BufferInfo getInfo() {
        //
        // NOTE: this code must be lock free so state machine updates do not hang
        //

        @SuppressWarnings("FieldAccessNotGuarded")
        boolean destroyed = this.destroyed.get();

        @SuppressWarnings("FieldAccessNotGuarded")
        long sequenceId = currentSequenceIdUpdater.get(this);

        PageBufferInfo pageBufferInfo =
            new PageBufferInfo(bufferId.getId(), bufferedBytesUpdater.get(this));
        return new BufferInfo(bufferId, destroyed, pageBufferInfo);
    }

    public boolean isDestroyed() {
        //
        // NOTE: this code must be lock free so state machine updates do not hang
        //
        @SuppressWarnings("FieldAccessNotGuarded")
        boolean destroyed = this.destroyed.get();
        return destroyed;
    }

    public void destroy() {
        List<SerializedChunkReference> removedPages;
        PendingRead pendingRead;
        synchronized (this) {
            removedPages = ImmutableList.copyOf(pages);
            pages.clear();

            log.debug(taskInstanceId + " is destoryed.");
            bufferedBytesUpdater.getAndSet(this, 0);

            noMorePages = true;
            destroyed.set(true);

            pendingRead = this.pendingRead;
            this.pendingRead = null;
        }

        removedPages.forEach(SerializedChunkReference::dereferencePage);

        if (pendingRead != null) {
            pendingRead.completeResultFutureWithEmpty();
        }
    }

    /**
     * force destroy pages for system pool memory leak
     */
    public void forceDestroy() {
        List<SerializedChunkReference> removedPages;
        synchronized (this) {
            removedPages = ImmutableList.copyOf(pages);
            pages.clear();

            log.debug(taskInstanceId + " is forceDestroyed.");
            bufferedBytesUpdater.getAndSet(this, 0);

            isForceDestroy = true;
            destroyed.set(true);
        }
        removedPages.forEach(SerializedChunkReference::dereferencePage);
    }

    public void enqueuePages(Collection<SerializedChunkReference> pages) {
        PendingRead pendingRead;
        synchronized (this) {
            // ignore pages after no more pages is set
            // this can happen with limit queries
            if (noMorePages) {
                return;
            }

            if (isForceDestroy) {
                return;
            }

            pages.forEach(SerializedChunkReference::addReference);
            this.pages.addAll(pages);

            long rowCount = 0L;
            for (SerializedChunkReference page : pages) {
                rowCount += page.getPositionCount();
            }

            long bytesAdded = 0L;
            for (SerializedChunkReference page : pages) {
                bytesAdded += page.getRetainedSizeInBytes();
            }
            bufferedBytesUpdater.addAndGet(this, bytesAdded);

            pendingRead = this.pendingRead;
            this.pendingRead = null;
        }

        // we just added a page, so process the pending read
        if (pendingRead != null) {
            processRead(pendingRead);
        }
    }

    public ListenableFuture<BufferResult> getPages(long sequenceId, DataSize maxSize) {
        checkArgument(sequenceId >= 0, "Invalid sequence id");

        // acknowledge pages first, out side of locks to not trigger callbacks while holding the lock
        acknowledgePages(sequenceId);

        PendingRead oldPendingRead = null;
        try {
            synchronized (this) {
                // save off the old pending read so we can abort it out side of the lock
                oldPendingRead = this.pendingRead;
                this.pendingRead = null;

                // Return results immediately if we have data, there will be no more data, or this is
                // an out of order request
                if (!pages.isEmpty() || noMorePages || sequenceId != currentSequenceIdUpdater.get(this)) {
                    return immediateFuture(processRead(sequenceId, maxSize));
                }

                // otherwise, wait for more data to arrive
                pendingRead = new PendingRead(taskInstanceId, sequenceId, maxSize);
                return pendingRead.getResultFuture();
            }
        } finally {
            if (oldPendingRead != null) {
                // Each buffer is private to a single client, and each client should only have one outstanding
                // read.  Therefore, we abort the existing read since it was most likely abandoned by the client.
                oldPendingRead.completeResultFutureWithEmpty();
            }
        }
    }

    public void setNoMorePages() {
        PendingRead pendingRead;
        synchronized (this) {
            // ignore duplicate calls
            if (noMorePages) {
                return;
            }

            noMorePages = true;

            pendingRead = this.pendingRead;
            this.pendingRead = null;
        }

        // there will be no more pages, so process the pending read
        if (pendingRead != null) {
            processRead(pendingRead);
        }
    }

    private void processRead(PendingRead pendingRead) {
        if (pendingRead.getResultFuture().isDone()) {
            return;
        }

        BufferResult bufferResult = processRead(pendingRead.getSequenceId(), pendingRead.getMaxSize());
        pendingRead.getResultFuture().set(bufferResult);
    }

    /**
     * @return a result with at least one page if we have pages in buffer, empty result otherwise
     */
    private synchronized BufferResult processRead(long sequenceId, DataSize maxSize) {
        // When pages are added to the partition buffer they are effectively
        // assigned an id starting from zero. When a read is processed, the
        // "token" is the id of the page to start the read from, so the first
        // step of the read is to acknowledge, and drop all pages up to the
        // provided sequenceId.  Then pages starting from the sequenceId are
        // returned with the sequenceId of the next page to read.
        //
        // Since the buffer API is asynchronous there are a number of problems
        // that can occur our of order request (typically from retries due to
        // request failures):
        // - Request to read pages that have already been acknowledged.
        //   Simply, send an result with no pages and the requested sequenceId,
        //   and since the client has already acknowledge the pages, it will
        //   ignore the out of order response.
        // - Request to read after the buffer has been destroyed.  When the
        //   buffer is destroyed all pages are dropped, so the read sequenceId
        //   appears to be off the end of the queue.  Normally a read past the
        //   end of the queue would be be an error, but this specific case is
        //   detected and handled.  The client is sent an empty response with
        //   the finished flag set and next token is the max acknowledged page
        //   when the buffer is destroyed.
        //

        // if request is for pages before the current position, just return an empty result
        if (sequenceId < currentSequenceIdUpdater.get(this)) {
            return emptyResults(taskInstanceId, sequenceId, false);
        }

        // if this buffer is finished, notify the client of this, so the client
        // will destroy this buffer
        if (pages.isEmpty() && noMorePages) {
            return emptyResults(taskInstanceId, currentSequenceIdUpdater.get(this), true);
        }

        // if request is for pages after the current position, there is a bug somewhere
        // a read call is always proceeded by acknowledge pages, which
        // will advance the sequence id to at least the request position, unless
        // the buffer is destroyed, and in that case the buffer will be empty with
        // no more pages set, which is checked above
        verify(sequenceId == currentSequenceIdUpdater.get(this), "Invalid sequence id");

        // read the new pages
        long maxBytes = maxSize.toBytes();
        List<SerializedChunk> result = new ArrayList<>();
        long bytes = 0;

        for (SerializedChunkReference page : pages) {
            bytes += page.getRetainedSizeInBytes();
            // break (and don't add) if this page would exceed the limit
            if (!result.isEmpty() && bytes > maxBytes) {
                break;
            }
            result.add(page.getSerializedPage());
        }
        return new BufferResult(taskInstanceId, sequenceId, sequenceId + result.size(), false, result);
    }

    /**
     * Drops pages up to the specified sequence id
     */
    private void acknowledgePages(long sequenceId) {
        if (!Threads.ENABLE_WISP) {
            checkState(!Thread.holdsLock(this), "Can not acknowledge pages while holding a lock on this");
        }

        List<SerializedChunkReference> removedPages = new ArrayList<>();
        synchronized (this) {
            if (destroyed.get()) {
                return;
            }

            // if pages have already been acknowledged, just ignore this
            long oldCurrentSequenceId = currentSequenceIdUpdater.get(this);
            if (sequenceId < oldCurrentSequenceId) {
                return;
            }

            int pagesToRemove = toIntExact(sequenceId - oldCurrentSequenceId);
            checkArgument(pagesToRemove <= pages.size(), "Invalid sequence id");

            long bytesRemoved = 0;
            for (int i = 0; i < pagesToRemove; i++) {
                SerializedChunkReference removedPage = pages.removeFirst();
                removedPages.add(removedPage);
                bytesRemoved += removedPage.getRetainedSizeInBytes();
            }

            // update current sequence id
            verify(currentSequenceIdUpdater
                .compareAndSet(this, oldCurrentSequenceId, oldCurrentSequenceId + pagesToRemove));

            // update memory tracking
            verify(bufferedBytesUpdater.addAndGet(this, -bytesRemoved) >= 0);
        }

        // dereference outside of synchronized to avoid making a callback while holding a lock
        removedPages.forEach(SerializedChunkReference::dereferencePage);
    }

    @Override
    public String toString() {
        @SuppressWarnings("FieldAccessNotGuarded")
        long sequenceId = currentSequenceIdUpdater.get(this);

        @SuppressWarnings("FieldAccessNotGuarded")
        boolean destroyed = this.destroyed.get();

        return toStringHelper(this)
            .add("bufferId", bufferId)
            .add("sequenceId", sequenceId)
            .add("destroyed", destroyed)
            .toString();
    }

    public boolean isPreferLocal() {
        return preferLocal;
    }

    public void setPreferLocal(boolean preferLocal) {
        this.preferLocal = preferLocal;
    }

    @Immutable
    private static class PendingRead {
        private final String taskInstanceId;
        private final long sequenceId;
        private final DataSize maxSize;
        private final SettableFuture<BufferResult> resultFuture = SettableFuture.create();

        private PendingRead(String taskInstanceId, long sequenceId, DataSize maxSize) {
            this.taskInstanceId = requireNonNull(taskInstanceId, "taskInstanceId is null");
            this.sequenceId = sequenceId;
            this.maxSize = maxSize;
        }

        public long getSequenceId() {
            return sequenceId;
        }

        public DataSize getMaxSize() {
            return maxSize;
        }

        public SettableFuture<BufferResult> getResultFuture() {
            return resultFuture;
        }

        public void completeResultFutureWithEmpty() {
            resultFuture.set(emptyResults(taskInstanceId, sequenceId, false));
        }
    }

    @ThreadSafe
    static class SerializedChunkReference {
        private final SerializedChunk serializedPage;
        private final AtomicInteger referenceCount;
        private final Runnable onDereference;

        public SerializedChunkReference(SerializedChunk serializedPage, int referenceCount, Runnable onDereference) {
            this.serializedPage = requireNonNull(serializedPage, "page is null");
            checkArgument(referenceCount > 0, "referenceCount must be at least 1");
            this.referenceCount = new AtomicInteger(referenceCount);
            this.onDereference = requireNonNull(onDereference, "onDereference is null");
        }

        public void addReference() {
            int oldReferences = referenceCount.getAndIncrement();
            checkState(oldReferences > 0, "Page has already been dereferenced");
        }

        public SerializedChunk getSerializedPage() {
            return serializedPage;
        }

        public int getPositionCount() {
            return serializedPage.getPositionCount();
        }

        public long getRetainedSizeInBytes() {
            return serializedPage.getRetainedSizeInBytes();
        }

        public void dereferencePage() {
            int remainingReferences = referenceCount.decrementAndGet();
            checkState(remainingReferences >= 0, "Page reference count is negative");

            if (remainingReferences == 0) {
                onDereference.run();
            }
        }

        @Override
        public String toString() {
            return toStringHelper(this)
                .add("referenceCount", referenceCount)
                .toString();
        }
    }
}