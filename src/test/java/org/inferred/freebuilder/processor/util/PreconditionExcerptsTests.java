package org.inferred.freebuilder.processor.util;

import static org.inferred.freebuilder.processor.util.SourceLevel.JAVA_6;
import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PreconditionExcerptsTests {

  @Test
  public void testCheckNotNull() {
    String source = SourceStringBuilder.simple(JAVA_6)
        .add(PreconditionExcerpts.checkNotNull("foo"))
        .toString();
    assertEquals("Preconditions.checkNotNull(foo);\n", source);
  }

  @Test
  public void testCheckNotNullInline() {
    String source = SourceStringBuilder.simple(JAVA_6)
        .add(PreconditionExcerpts.checkNotNullPreamble("foo"))
        .addLine("this.foo = %s;", PreconditionExcerpts.checkNotNullInline("foo"))
        .toString();
    assertEquals("this.foo = Preconditions.checkNotNull(foo);\n", source);
  }

  @Test
  public void testCheckArgument_simpleMessage() {
    String source = SourceStringBuilder.simple(JAVA_6)
        .add(PreconditionExcerpts.checkArgument("foo != 0", "foo must not be zero"))
        .toString();
    assertEquals("Preconditions.checkArgument(foo != 0, \"foo must not be zero\");\n", source);
  }

  @Test
  public void testCheckArgument_singleParameter() {
    String source = SourceStringBuilder.simple(JAVA_6)
        .add(PreconditionExcerpts.checkArgument("foo > 0", "foo must be positive, but got %s", "foo"))
        .toString();
    assertEquals("Preconditions.checkArgument(foo > 0, "
        + "\"foo must be positive, but got %s\", foo);\n", source);
  }

  @Test
  public void testCheckArgument_twoParameters() {
    String source = SourceStringBuilder.simple(JAVA_6)
        .add(PreconditionExcerpts.checkArgument(
            "foo > bar", "foo must be greater than bar, but got %s <= %s", "foo", "bar"))
        .toString();
    assertEquals("Preconditions.checkArgument(foo > bar, "
        + "\"foo must be greater than bar, but got %s <= %s\", foo, bar);\n", source);
  }

  @Test
  public void testCheckArgument_doubleNegative() {
    String source = SourceStringBuilder.simple(JAVA_6)
        .add(PreconditionExcerpts.checkArgument("!foo.isEmpty()", "foo must not be empty"))
        .toString();
    assertEquals("Preconditions.checkArgument(!foo.isEmpty(), \"foo must not be empty\");\n", source);
  }

  @Test
  public void testCheckArgument_doubleQuotes() {
    String source = SourceStringBuilder.simple(JAVA_6)
        .add(PreconditionExcerpts.checkArgument(
            "foo.contains(\"\\\"\")", "foo must contain at least one double quote ('\"')"))
        .toString();
    assertEquals("Preconditions.checkArgument(foo.contains(\"\\\"\"), "
        + "\"foo must contain at least one double quote ('\"')\");\n", source);
  }

  @Test
  public void testCheckArgument_backslashes() {
    String source = SourceStringBuilder.simple(JAVA_6)
        .add(PreconditionExcerpts.checkArgument(
            "foo.contains(\"\\\")", "foo must contain at least one backslash ('\\')"))
        .toString();
    assertEquals("Preconditions.checkArgument(foo.contains(\"\\\"), "
        + "\"foo must contain at least one backslash ('\\\\')\");\n", source);
  }

  @Test
  public void testCheckArgument_newLines() {
    String source = SourceStringBuilder.simple(JAVA_6)
        .add(PreconditionExcerpts.checkArgument(
            "foo.length() <= 80", "foo should not be more than 80 characters, but got:\n%s", "foo"))
        .toString();
    assertEquals("Preconditions.checkArgument(foo.length() <= 80, "
        + "\"foo should not be more than 80 characters, but got:\\n%s\", foo);\n", source);
  }

  @Test
  public void testCheckState_simpleMessage() {
    String source = SourceStringBuilder.simple(JAVA_6)
        .add(PreconditionExcerpts.checkState("foo != 0", "foo must not be zero"))
        .toString();
    assertEquals("Preconditions.checkState(foo != 0, \"foo must not be zero\");\n", source);
  }
}
