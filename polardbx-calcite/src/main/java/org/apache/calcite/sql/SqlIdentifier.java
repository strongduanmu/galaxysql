/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.calcite.sql;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.apache.calcite.rel.type.DynamicRecordType;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.pretty.SqlPrettyWriter;
import org.apache.calcite.sql.util.SqlVisitor;
import org.apache.calcite.sql.validate.SqlMonotonicity;
import org.apache.calcite.sql.validate.SqlQualified;
import org.apache.calcite.sql.validate.SqlValidator;
import org.apache.calcite.sql.validate.SqlValidatorScope;
import org.apache.calcite.util.EqualsContext;
import org.apache.calcite.util.Litmus;
import org.apache.calcite.util.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * A <code>SqlIdentifier</code> is an identifier, possibly compound.
 */
public class SqlIdentifier extends SqlNode {
  private static final Function<String, String> STAR_TO_EMPTY =
      new Function<String, String>() {
        public String apply(String s) {
          return s.equals("*") ? "" : s;
        }
      };

  private static final Function<String, String> EMPTY_TO_STAR =
      new Function<String, String>() {
        public String apply(String s) {
          return s.equals("") ? "*" : s.equals("*") ? "\"*\"" : s;
        }
      };

  //~ Instance fields --------------------------------------------------------

  /**
   * Array of the components of this compound identifier.
   *
   * <p>The empty string represents the wildcard "*",
   * to distinguish it from a real "*" (presumably specified using quotes).
   *
   * <p>It's convenient to have this member public, and it's convenient to
   * have this member not-final, but it's a shame it's public and not-final.
   * If you assign to this member, please use
   * {@link #setNames(java.util.List, java.util.List)}.
   * And yes, we'd like to make identifiers immutable one day.
   */
  public ImmutableList<String> names;

  /**
   * This identifier's force index hint
   */
  public SqlNode indexNode;

  /**
   * This tableName identifier's partitions of mysql partition selection syntax
   * <pre>
   *     For example,
   *     A query sql is "SELECT * FROM employees PARTITION (p1,p2)",
   *     then the partitions is the info of "PARTITION (p1)".
   *     It is a SqlNodeList of "p1,p2,...".
   * </pre>
   */
  public SqlNode partitions;

  /**
   * This is the timestamp expression for flashback query
   * <pre>
   *     table_factor: {
   *         tbl_name [{PARTITION (partition_names) | AS OF expr}]
   *             [[AS] alias] [index_hint_list]
   *       | table_subquery [AS] alias
   *       | ( table_references )
   *     }
   *
   *     MySQL only support syntax showed above.
   *     So that we must put AS OF in front of alias and index_hint_list behind alias
   * </pre>
   */
  public SqlNode flashback;

  /**
   * 记录AS OF 种类：AS_OF/AS_OF_80/AS_OF_57
   */
  public SqlOperator flashbackOperator;

  /**
   * This identifier's collation (if any).
   */
  final SqlCollation collation;

  /**
   * A list of the positions of the components of compound identifiers.
   */
  protected ImmutableList<SqlParserPos> componentPositions;

  //~ Constructors -----------------------------------------------------------
  /**
   * Creates a simple identifier, for example <code>foo</code>.
   */
  public SqlIdentifier(
      String name,
      SqlParserPos pos) {
    this(ImmutableList.of(name), null, pos, null);
  }

  public SqlIdentifier(List<String> names, SqlParserPos pos) {
    this(names, null, pos, null);
  }

  /**
   * Creates a simple identifier, for example <code>foo</code>, with a
   * collation.
   */
  public SqlIdentifier(
      String name,
      SqlCollation collation,
      SqlParserPos pos) {
    this(ImmutableList.of(name), collation, pos, null);
  }

  /**
   * Creates a compound identifier, for example <code>foo.bar</code>.
   *
   * @param names Parts of the identifier, length &ge; 1
   */
  public SqlIdentifier(
      List<String> names,
      SqlCollation collation,
      SqlParserPos pos,
      List<SqlParserPos> componentPositions) {
//    super(pos);
//    this.names = ImmutableList.copyOf(names);
//    this.collation = collation;
//    this.componentPositions = componentPositions == null ? null
//        : ImmutableList.copyOf(componentPositions);
//    for (String name : names) {
//      assert name != null;
//    }
    this(names, collation, pos, componentPositions, null, null, null, null);
  }

  public SqlIdentifier(
      List<String> names,
      SqlCollation collation,
      SqlParserPos pos,
      List<SqlParserPos> componentPositions, SqlNode indexNode) {
    this(names, collation, pos, componentPositions, indexNode, null, null, null);
  }

  public SqlIdentifier(
      String name,
      SqlParserPos pos,
      SqlNode indexNode) {
    this(ImmutableList.of(name), null, pos, null, indexNode, null, null, null);
  }

  /**
   * Creates a compound identifier, for example <code>foo.bar</code>.
   *
   * @param names Parts of the identifier, length &ge; 1
   */
  public SqlIdentifier(
      List<String> names,
      SqlCollation collation,
      SqlParserPos pos,
      List<SqlParserPos> componentPositions, SqlNode indexNode, SqlNode partitions, SqlNode flashback) {
    this(ImmutableList.copyOf(names), collation, pos, componentPositions, indexNode, partitions, flashback, null);
  }

  public SqlIdentifier(
      List<String> names,
      SqlCollation collation,
      SqlParserPos pos,
      List<SqlParserPos> componentPositions, SqlNode indexNode, SqlNode partitions, SqlNode flashback,
      SqlOperator flashbackOperator) {
    super(pos);
    this.names = ImmutableList.copyOf(names);
    this.collation = collation;
    this.componentPositions = componentPositions == null ? null
        : ImmutableList.copyOf(componentPositions);
    for (String name : names) {
      assert name != null;
    }
    this.indexNode = indexNode;
    this.partitions = partitions;
    this.flashback = flashback;
    this.flashbackOperator = flashbackOperator;
  }

  /**
   * Creates an identifier that is a singleton wildcard star.
   */
  public static SqlIdentifier star(SqlParserPos pos) {
    return star(ImmutableList.of(""), pos, ImmutableList.of(pos));
  }

  /**
   * Creates an identifier that ends in a wildcard star.
   */
  public static SqlIdentifier star(List<String> names, SqlParserPos pos,
                                   List<SqlParserPos> componentPositions) {
    return new SqlIdentifier(Lists.transform(names, STAR_TO_EMPTY), null, pos,
        componentPositions);
  }

  //~ Methods ----------------------------------------------------------------

  public SqlKind getKind() {
    return SqlKind.IDENTIFIER;
  }

  @Override public SqlNode clone(SqlParserPos pos) {
    return new SqlIdentifier(names, collation, pos, componentPositions, indexNode, partitions, flashback, flashbackOperator);
  }

  public String toStringWithBacktick() {
    return surroundWithBacktick(toString());
  }

  public static String surroundWithBacktick(String identifier) {
    if (identifier.contains("`")) {
      return "`" + identifier.replaceAll("`", "``") + "`";
    }
    return "`" + identifier + "`";
  }

  @Override public String toString() {
    return getString(names);
  }

  /** Converts a list of strings to a qualified identifier. */
  public static String getString(List<String> names) {
    return Util.sepList(toStar(names), ".");
  }

  /** Converts empty strings in a list of names to stars. */
  public static List<String> toStar(List<String> names) {
    return Lists.transform(names, EMPTY_TO_STAR);
  }

  /**
   * Modifies the components of this identifier and their positions.
   *
   * @param names Names of components
   * @param poses Positions of components
   */
  public void setNames(List<String> names, List<SqlParserPos> poses) {
    this.names = ImmutableList.copyOf(names);
    this.componentPositions = poses == null ? null
        : ImmutableList.copyOf(poses);
  }

  /** Returns an identifier that is the same as this except one modified name.
   * Does not modify this identifier. */
  public SqlIdentifier setName(int i, String name) {
    if (!names.get(i).equals(name)) {
      String[] nameArray = names.toArray(new String[names.size()]);
      nameArray[i] = name;
      return new SqlIdentifier(ImmutableList.copyOf(nameArray), collation, pos,
          componentPositions);
    } else {
      return this;
    }
  }

  /** Returns an identifier that is the same as this except with a component
   * added at a given position. Does not modify this identifier. */
  public SqlIdentifier add(int i, String name, SqlParserPos pos) {
    final List<String> names2 = new ArrayList<>(names);
    names2.add(i, name);
    final List<SqlParserPos> pos2;
    if (componentPositions == null) {
      pos2 = null;
    } else {
      pos2 = new ArrayList<>(componentPositions);
      pos2.add(i, pos);
    }
    return new SqlIdentifier(names2, collation, pos, pos2);
  }

  /**
   * Returns the position of the <code>i</code>th component of a compound
   * identifier, or the position of the whole identifier if that information
   * is not present.
   *
   * @param i Ordinal of component.
   * @return Position of i'th component
   */
  public SqlParserPos getComponentParserPosition(int i) {
    assert (i >= 0) && (i < names.size());
    return (componentPositions == null) ? getParserPosition()
        : componentPositions.get(i);
  }

  /**
   * Copies names and components from another identifier. Does not modify the
   * cross-component parser position.
   *
   * @param other identifier from which to copy
   */
  public void assignNamesFrom(SqlIdentifier other) {
    setNames(other.names, other.componentPositions);
  }

  /**
   * Creates an identifier which contains only the <code>ordinal</code>th
   * component of this compound identifier. It will have the correct
   * {@link SqlParserPos}, provided that detailed position information is
   * available.
   */
  public SqlIdentifier getComponent(int ordinal) {
    return getComponent(ordinal, ordinal + 1);
  }

  public SqlIdentifier getComponent(int from, int to) {
    final SqlParserPos pos;
    final ImmutableList<SqlParserPos> pos2;
    if (componentPositions == null) {
      pos2 = null;
      pos = this.pos;
    } else {
      pos2 = componentPositions.subList(from, to);
      pos = SqlParserPos.sum(pos2);
    }
    return new SqlIdentifier(names.subList(from, to), collation, pos, pos2);
  }

  /**
   * Creates an identifier that consists of this identifier plus a name segment.
   * Does not modify this identifier.
   */
  public SqlIdentifier plus(String name, SqlParserPos pos) {
    final ImmutableList<String> names =
        ImmutableList.<String>builder().addAll(this.names).add(name).build();
    final ImmutableList<SqlParserPos> componentPositions;
    final SqlParserPos pos2;
    if (this.componentPositions != null) {
      final ImmutableList.Builder<SqlParserPos> builder =
          ImmutableList.builder();
      componentPositions =
          builder.addAll(this.componentPositions).add(pos).build();
      pos2 = SqlParserPos.sum(builder.add(this.pos).build());
    } else {
      componentPositions = null;
      pos2 = pos;
    }
    return new SqlIdentifier(names, collation, pos2, componentPositions);
  }

  /**
   * Creates an identifier that consists of this identifier plus a wildcard star.
   * Does not modify this identifier.
   */
  public SqlIdentifier plusStar() {
    final SqlIdentifier id = this.plus("*", SqlParserPos.ZERO);
    return new SqlIdentifier(Lists.transform(id.names, STAR_TO_EMPTY), null, id.pos,
        id.componentPositions);
  }

  /** Creates an identifier that consists of all but the last {@code n}
   * name segments of this one. */
  public SqlIdentifier skipLast(int n) {
    return getComponent(0, names.size() - n);
  }

  public void unparse(
      SqlWriter writer,
      int leftPrec,
      int rightPrec) {
    final SqlWriter.Frame frame =
        writer.startList(SqlWriter.FrameTypeEnum.IDENTIFIER);
    for (int i = 0; i < names.size(); i++) {
      writer.sep(".");
      if (names.get(i).equals("")) {
        writer.print("*");
      } else {
        writer.identifier(names.get(i));
        // Write partitions;
        SqlWriter.FrameTypeEnum frame1 = ((SqlPrettyWriter) writer).peekOptStack();
        // Parse index node only for the last name.
        if (indexNode != null && frame1 == SqlWriter.FrameTypeEnum.FROM_LIST && i == names.size() - 1) {
          if (indexNode instanceof SqlNodeList) {
            SqlNodeList list = (SqlNodeList) indexNode;
            for (SqlNode node : list) {
              node.unparse(writer, leftPrec, rightPrec);
            }
          } else {
            indexNode.unparse(writer, leftPrec, rightPrec);
          }
        }
      }
    }

    if (null != collation) {
      collation.unparse(writer, leftPrec, rightPrec);
    }
    writer.endList(frame);
  }

  public void validate(SqlValidator validator, SqlValidatorScope scope) {
    validator.validateIdentifier(this, scope);
  }

  public void validateExpr(SqlValidator validator, SqlValidatorScope scope) {
    // First check for builtin functions which don't have parentheses,
    // like "LOCALTIME".
    SqlCall call =
        SqlUtil.makeCall(
            validator.getOperatorTable(),
            this);
    if (call != null) {
      validator.validateCall(call, scope);
      return;
    }

    validator.validateIdentifier(this, scope);
  }

  public boolean equalsDeep(SqlNode node, Litmus litmus, EqualsContext context) {
    if (!(node instanceof SqlIdentifier)) {
      return litmus.fail("{} != {}", this, node);
    }
    SqlIdentifier that = (SqlIdentifier) node;

    if (context.isGenColSubstitute()) {
      if (this.names.size() == 2 && this.getLastName().equals(that.getLastName()) && this.names.get(0)
          .equals(context.getIdTableName())) {
        return litmus.succeed();
      } else {
        return litmus.fail("{} != {}", this, node);
      }
    }

    if (this.names.size() != that.names.size()) {
      return litmus.fail("{} != {}", this, node);
    }
    for (int i = 0; i < names.size(); i++) {
      if (!this.names.get(i).equals(that.names.get(i))) {
        return litmus.fail("{} != {}", this, node);
      }
    }
    return litmus.succeed();
  }

  public <R> R accept(SqlVisitor<R> visitor) {
    return visitor.visit(this);
  }

  public SqlCollation getCollation() {
    return collation;
  }

  public String getSimple() {
    assert names.size() == 1;
    return names.get(0);
  }

  public String getLastName() {
    assert names.size() != 0;
    return names.get(names.size() - 1);
  }

  /**
   * Returns whether this identifier is a star, such as "*" or "foo.bar.*".
   */
  public boolean isStar() {
    return Util.last(names).equals("");
  }

  /**
   * Returns whether this is a simple identifier. "FOO" is simple; "*",
   * "FOO.*" and "FOO.BAR" are not.
   */
  public boolean isSimple() {
    return names.size() == 1 && !isStar();
  }

  public SqlMonotonicity getMonotonicity(SqlValidatorScope scope) {
    // for "star" column, whether it's static or dynamic return not_monotonic directly.
    if (Util.last(names).equals("") || DynamicRecordType.isDynamicStarColName(Util.last(names))) {
      return SqlMonotonicity.NOT_MONOTONIC;
    }

    // First check for builtin functions which don't have parentheses,
    // like "LOCALTIME".
    final SqlValidator validator = scope.getValidator();
    SqlCall call =
        SqlUtil.makeCall(
            validator.getOperatorTable(),
            this);
    if (call != null) {
      return call.getMonotonicity(scope);
    }
    final SqlQualified qualified = scope.fullyQualify(this);
    final SqlIdentifier fqId = qualified.identifier;
    return qualified.namespace.resolve().getMonotonicity(Util.last(fqId.names));
  }

  public SqlOperator getFlashbackOperator() {
    return flashbackOperator;
  }
}

// End SqlIdentifier.java
