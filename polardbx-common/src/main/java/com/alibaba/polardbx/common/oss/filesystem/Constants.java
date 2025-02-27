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

package com.alibaba.polardbx.common.oss.filesystem;

import com.aliyun.oss.common.utils.VersionInfoUtils;

/**
 * ALL configuration constants for OSS filesystem.
 */
public final class Constants {

    private Constants() {
    }

    // User agent
    public static final String USER_AGENT_PREFIX = "fs.oss.user.agent.prefix";
    public static final String USER_AGENT_PREFIX_DEFAULT =
        VersionInfoUtils.getDefaultUserAgent();

    // Class of credential provider
    public static final String CREDENTIALS_PROVIDER_KEY =
        "fs.oss.credentials.provider";

    public static final int OSS_DEFAULT_PORT = -1;

    // OSS access verification
    public static final String ACCESS_KEY_ID = "fs.oss.accessKeyId";
    public static final String ACCESS_KEY_SECRET = "fs.oss.accessKeySecret";
    public static final String SECURITY_TOKEN = "fs.oss.securityToken";

    // Number of simultaneous connections to oss
    public static final String MAXIMUM_CONNECTIONS_KEY =
        "fs.oss.connection.maximum";
    public static final int MAXIMUM_CONNECTIONS_DEFAULT = 32;

    // Connect to oss over ssl
    public static final String SECURE_CONNECTIONS_KEY =
        "fs.oss.connection.secure.enabled";
    public static final boolean SECURE_CONNECTIONS_DEFAULT = true;

    // Use a custom endpoint
    public static final String ENDPOINT_KEY = "fs.oss.endpoint";

    // Connect to oss through a proxy server
    public static final String PROXY_HOST_KEY = "fs.oss.proxy.host";
    public static final String PROXY_PORT_KEY = "fs.oss.proxy.port";
    public static final String PROXY_USERNAME_KEY = "fs.oss.proxy.username";
    public static final String PROXY_PASSWORD_KEY = "fs.oss.proxy.password";
    public static final String PROXY_DOMAIN_KEY = "fs.oss.proxy.domain";
    public static final String PROXY_WORKSTATION_KEY =
        "fs.oss.proxy.workstation";

    // Number of times we should retry errors
    public static final String MAX_ERROR_RETRIES_KEY = "fs.oss.attempts.maximum";
    public static final int MAX_ERROR_RETRIES_DEFAULT = 10;

    // Time until we give up trying to establish a connection to oss
    public static final String ESTABLISH_TIMEOUT_KEY =
        "fs.oss.connection.establish.timeout";
    public static final int ESTABLISH_TIMEOUT_DEFAULT = 50000;

    // Time until we give up on a connection to oss
    public static final String SOCKET_TIMEOUT_KEY = "fs.oss.connection.timeout";
    public static final int SOCKET_TIMEOUT_DEFAULT = 200000;

    // the policy of fetching oss data.
    public static final String OSS_FETCH_POLICY = "fs.oss.io.fetch.policy";

    // Number of records to get while paging through a directory listing
    public static final String MAX_PAGING_KEYS_KEY = "fs.oss.paging.maximum";
    public static final int MAX_PAGING_KEYS_DEFAULT = 1000;

    // Size of each of or multipart pieces in bytes
    public static final String MULTIPART_UPLOAD_PART_SIZE_KEY =
        "fs.oss.multipart.upload.size";
    public static final long MULTIPART_UPLOAD_PART_SIZE_DEFAULT =
        104857600; // 100 MB

    /**
     * The minimum multipart size which OSS supports.
     */
    public static final int MULTIPART_MIN_SIZE = 100 * 1024;

    public static final int MULTIPART_UPLOAD_PART_NUM_LIMIT = 10000;

    // Minimum size in bytes before we start a multipart uploads or copy
    public static final String MIN_MULTIPART_UPLOAD_THRESHOLD_KEY =
        "fs.oss.multipart.upload.threshold";
    public static final long MIN_MULTIPART_UPLOAD_THRESHOLD_DEFAULT =
        20 * 1024 * 1024;

    public static final String MULTIPART_DOWNLOAD_SIZE_KEY =
        "fs.oss.multipart.download.size";
    public static final long MULTIPART_DOWNLOAD_SIZE_DEFAULT = 512 * 1024;

    public static final String MULTIPART_DOWNLOAD_THREAD_NUMBER_KEY =
        "fs.oss.multipart.download.threads";
    public static final int MULTIPART_DOWNLOAD_THREAD_NUMBER_DEFAULT = 10;

    public static final String MAX_TOTAL_TASKS_KEY = "fs.oss.max.total.tasks";
    public static final int MAX_TOTAL_TASKS_DEFAULT = 128;

    public static final String MULTIPART_DOWNLOAD_AHEAD_PART_MAX_NUM_KEY =
        "fs.oss.multipart.download.ahead.part.max.number";
    public static final int MULTIPART_DOWNLOAD_AHEAD_PART_MAX_NUM_DEFAULT = 4;

    // The maximum queue number for copies
    // New copies will be blocked when queue is full
    public static final String MAX_COPY_TASKS_KEY = "fs.oss.max.copy.tasks";
    public static final int MAX_COPY_TASKS_DEFAULT = 1024 * 10240;

    // The maximum number of threads allowed in the pool for copies
    public static final String MAX_COPY_THREADS_NUM_KEY =
        "fs.oss.max.copy.threads";
    public static final int MAX_COPY_THREADS_DEFAULT = 25;

    // The maximum number of concurrent tasks allowed to copy one directory.
    // So we will not block other copies
    public static final String MAX_CONCURRENT_COPY_TASKS_PER_DIR_KEY =
        "fs.oss.max.copy.tasks.per.dir";
    public static final int MAX_CONCURRENT_COPY_TASKS_PER_DIR_DEFAULT = 5;

    // Comma separated list of directories
    public static final String BUFFER_DIR_KEY = "fs.oss.buffer.dir";

    // private | public-read | public-read-write
    public static final String CANNED_ACL_KEY = "fs.oss.acl.default";
    public static final String CANNED_ACL_DEFAULT = "";

    // OSS server-side encryption
    public static final String SERVER_SIDE_ENCRYPTION_ALGORITHM_KEY =
        "fs.oss.server-side-encryption-algorithm";

    public static final String FS_OSS_BLOCK_SIZE_KEY = "fs.oss.block.size";
    public static final int FS_OSS_BLOCK_SIZE_DEFAULT = 64 * 1024 * 1024;

    public static final String FS_OSS = "oss";

    public static final String KEEPALIVE_TIME_KEY =
        "fs.oss.threads.keepalivetime";
    public static final int KEEPALIVE_TIME_DEFAULT = 60;

    public static final String UPLOAD_ACTIVE_BLOCKS_KEY =
        "fs.oss.upload.active.blocks";
    public static final int UPLOAD_ACTIVE_BLOCKS_DEFAULT = 4;

    // S3 access verification
    public static final String S3_ACCESS_KEY = "fs.s3a.access.key";
    public static final String S3_SECRET_KEY = "fs.s3a.secret.key";

    // ABS access verification
    // fs.azure.account.key.<AccountName>.blob.<EndpointSuffix>
    public static final String ABS_URI_SUFFIX_PATTERN = "%s.blob.%s";
    public static final String ABS_ACCOUNT_KEY_PATTERN = "fs.azure.account.key." + ABS_URI_SUFFIX_PATTERN;

    public static final String AZURE_WASBS_SCHEME = "wasbs";
    public static final String AZURE_WASB_SCHEME = "wasb";
}