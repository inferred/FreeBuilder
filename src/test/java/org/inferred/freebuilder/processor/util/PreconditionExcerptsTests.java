/*
 * Copyright 2016 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.inferred.freebuilder.processor.util;

import static org.junit.Assert.assertEquals;

import org.inferred.freebuilder.processor.util.feature.GuavaLibrary;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PreconditionExcerptsTests {

  @Test
  public void testCheckNotNull_guava() {
    String source = SourceStringBuilder.simple(GuavaLibrary.AVAILABLE)
        .add(PreconditionExcerpts.checkNotNull("foo"))
        .toString();
    assertEquals("Preconditions.checkNotNull(foo);\n", source);
  }

  @Test
  public void testCheckNotNull_j8() {
    String source = SourceStringBuilder.simple()
        .add(PreconditionExcerpts.checkNotNull("foo"))
        .toString();
    assertEquals("Objects.requireNonNull(foo);\n", source);
  }

  @Test
  public void testCheckNotNullInline_guava() {
    String source = SourceStringBuilder.simple(GuavaLibrary.AVAILABLE)
        .add(PreconditionExcerpts.checkNotNullPreamble("foo"))
        .addLine("this.foo = %s;", PreconditionExcerpts.checkNotNullInline("foo"))
        .toString();
    assertEquals("this.foo = Preconditions.checkNotNull(foo);\n", source);
  }

  @Test
  public void testCheckNotNullInline_j8() {
    String source = SourceStringBuilder.simple()
        .add(PreconditionExcerpts.checkNotNullPreamble("foo"))
        .addLine("this.foo = %s;", PreconditionExcerpts.checkNotNullInline("foo"))
        .toString();
    assertEquals("this.foo = Objects.requireNonNull(foo);\n", source);
  }

  @Test
  public void testCheckArgument_guava_simpleMessage() {
    String source = SourceStringBuilder.simple(GuavaLibrary.AVAILABLE)
        .add(PreconditionExcerpts.checkArgument("foo != 0", "foo must not be zero"))
        .toString();
    assertEquals("Preconditions.checkArgument(foo != 0, \"foo must not be zero\");\n", source);
  }

  @Test
  public void testCheckArgument_guava_singleParameter() {
    String source = SourceStringBuilder.simple(GuavaLibrary.AVAILABLE)
        .add(PreconditionExcerpts.checkArgument(
            "foo > 0", "foo must be positive, but got %s", "foo"))
        .toString();
    assertEquals("Preconditions.checkArgument(foo > 0, "
        + "\"foo must be positive, but got %s\", foo);\n", source);
  }

  @Test
  public void testCheckArgument_guava_twoParameters() {
    String source = SourceStringBuilder.simple(GuavaLibrary.AVAILABLE)
        .add(PreconditionExcerpts.checkArgument(
            "foo > bar", "foo must be greater than bar, but got %s <= %s", "foo", "bar"))
        .toString();
    assertEquals("Preconditions.checkArgument(foo > bar, "
        + "\"foo must be greater than bar, but got %s <= %s\", foo, bar);\n", source);
  }

  @Test
  public void testCheckArgument_guava_doubleNegative() {
    String source = SourceStringBuilder.simple(GuavaLibrary.AVAILABLE)
        .add(PreconditionExcerpts.checkArgument("!foo.isEmpty()", "foo must not be empty"))
        .toString();
    assertEquals(
        "Preconditions.checkArgument(!foo.isEmpty(), \"foo must not be empty\");\n", source);
  }

  @Test
  public void testCheckArgument_guava_doubleQuotes() {
    String source = SourceStringBuilder.simple(GuavaLibrary.AVAILABLE)
        .add(PreconditionExcerpts.checkArgument(
            "foo.contains(\"\\\"\")", "foo must contain at least one double quote ('\"')"))
        .toString();
    assertEquals("Preconditions.checkArgument(foo.contains(\"\\\"\"), "
        + "\"foo must contain at least one double quote ('\"')\");\n", source);
  }

  @Test
  public void testCheckArgument_guava_backslashes() {
    String source = SourceStringBuilder.simple(GuavaLibrary.AVAILABLE)
        .add(PreconditionExcerpts.checkArgument(
            "foo.contains(\"\\\")", "foo must contain at least one backslash ('\\')"))
        .toString();
    assertEquals("Preconditions.checkArgument(foo.contains(\"\\\"), "
        + "\"foo must contain at least one backslash ('\\\\')\");\n", source);
  }

  @Test
  public void testCheckArgument_guava_newLines() {
    String source = SourceStringBuilder.simple(GuavaLibrary.AVAILABLE)
        .add(PreconditionExcerpts.checkArgument(
            "foo.length() <= 80", "foo should not be more than 80 characters, but got:\n%s", "foo"))
        .toString();
    assertEquals("Preconditions.checkArgument(foo.length() <= 80, "
        + "\"foo should not be more than 80 characters, but got:\\n%s\", foo);\n", source);
  }

  @Test
  public void testCheckArgument_j6_simpleMessage() {
    String source = SourceStringBuilder.simple()
        .add(PreconditionExcerpts.checkArgument("condition", "message"))
        .toString();
    assertEquals(
        "if (!condition) {\n  throw new IllegalArgumentException(\"message\");\n}\n", source);
  }

  @Test
  public void testCheckArgument_j6_singleParameterAtEnd() {
    String source = SourceStringBuilder.simple()
        .add(PreconditionExcerpts.checkArgument("condition", "message about %s", "foo"))
        .toString();
    assertEquals(
        "if (!condition) {\n  throw new IllegalArgumentException(\"message about \" + foo);\n}\n",
        source);
  }

  @Test
  public void testCheckArgument_j6_singleParameterInMiddle() {
    String source = SourceStringBuilder.simple()
        .add(PreconditionExcerpts.checkArgument("condition", "bar %s baz", "foo"))
        .toString();
    assertEquals(
        "if (!condition) {\n  throw new IllegalArgumentException(\"bar \" + foo + \" baz\");\n}\n",
        source);
  }

  @Test
  public void testCheckArgument_j6_singleParameterAtStart() {
    String source = SourceStringBuilder.simple()
        .add(PreconditionExcerpts.checkArgument("condition", "%s is wrong", "foo"))
        .toString();
    assertEquals(
        "if (!condition) {\n  throw new IllegalArgumentException(foo + \" is wrong\");\n}\n",
        source);
  }

  @Test
  public void testCheckArgument_j6_twoParametersInMiddle() {
    String source = SourceStringBuilder.simple()
        .add(PreconditionExcerpts.checkArgument("condition", "a %s c %s e", "b", "d"))
        .toString();
    assertEquals(
        "if (!condition) {\n  throw new IllegalArgumentException("
            + "\"a \" + b + \" c \" + d + \" e\");\n}\n",
        source);
  }

  @Test
  public void testCheckArgument_j6_doubleQuotes() {
    String source = SourceStringBuilder.simple()
        .add(PreconditionExcerpts.checkArgument(
            "foo.contains(\"\\\"\")", "foo must contain at least one double quote ('\"')"))
        .toString();
    assertEquals(
        "if (!foo.contains(\"\\\"\")) {\n  throw new IllegalArgumentException("
                + "\"foo must contain at least one double quote ('\"')\");\n}\n",
        source);
  }

  @Test
  public void testCheckArgument_j6_backslashes() {
    String source = SourceStringBuilder.simple()
        .add(PreconditionExcerpts.checkArgument(
            "foo.contains(\"\\\\\")", "foo must contain at least one backslash ('\\')"))
        .toString();
    assertEquals(
        "if (!foo.contains(\"\\\\\")) {\n  throw new IllegalArgumentException("
                + "\"foo must contain at least one backslash ('\\\\')\");\n}\n",
        source);
  }

  @Test
  public void testCheckArgument_j6_newLines() {
    String source = SourceStringBuilder.simple()
        .add(PreconditionExcerpts.checkArgument(
            "foo.contains(\"\\n\")", "foo must contain at least one newline ('\n')"))
        .toString();
    assertEquals(
        "if (!foo.contains(\"\\n\")) {\n  throw new IllegalArgumentException("
                + "\"foo must contain at least one newline ('\\n')\");\n}\n",
        source);
  }

  @Test
  public void testCheckArgument_j6_doubleNegative() {
    String source = SourceStringBuilder.simple()
        .add(PreconditionExcerpts.checkArgument("!foo.isEmpty()", "foo must not be empty"))
        .toString();
    assertEquals(
        "if (foo.isEmpty()) {\n  throw new IllegalArgumentException("
                + "\"foo must not be empty\");\n}\n",
        source);
  }

  @Test
  public void testCheckArgument_j6_complexCondition() {
    String source = SourceStringBuilder.simple()
        .add(PreconditionExcerpts.checkArgument("a % 3 > 0", "message"))
        .toString();
    assertEquals(
        "if (!(a % 3 > 0)) {\n  throw new IllegalArgumentException(\"message\");\n}\n", source);
  }

  @Test
  public void testCheckArgument_j6_complexConditionWithMultipleBrackets() {
    String source = SourceStringBuilder.simple()
        .add(PreconditionExcerpts.checkArgument("(a || b) && (c || d)", "message"))
        .toString();
    assertEquals(
        "if (!((a || b) && (c || d))) {\n  throw new IllegalArgumentException(\"message\");\n}\n",
        source);
  }

  @Test
  public void testCheckArgument_j6_instanceOf() {
    String source = SourceStringBuilder.simple()
        .add(PreconditionExcerpts.checkArgument("a instanceof Integer", "message"))
        .toString();
    assertEquals(
        "if (!(a instanceof Integer)) {\n  throw new IllegalArgumentException(\"message\");\n}\n",
        source);
  }

  @Test
  public void testCheckState_guava_simpleMessage() {
    String source = SourceStringBuilder.simple(GuavaLibrary.AVAILABLE)
        .add(PreconditionExcerpts.checkState("foo != 0", "foo must not be zero"))
        .toString();
    assertEquals("Preconditions.checkState(foo != 0, \"foo must not be zero\");\n", source);
  }

  @Test
  public void testCheckState_j6_simpleMessage() {
    String source = SourceStringBuilder.simple()
        .add(PreconditionExcerpts.checkState("foo != 0", "foo must not be zero"))
        .toString();
    assertEquals(
        "if (!(foo != 0)) {\n  throw new IllegalStateException("
                + "\"foo must not be zero\");\n}\n",
        source);
  }
}
