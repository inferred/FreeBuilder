package org.inferred.freebuilder.processor.util;

import com.google.common.base.Preconditions;
import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;

/**
 * Code snippets that call or emulate Guava's {@link Preconditions} methods.
 */
public class PreconditionExcerpts {

  private static final Escaper JAVA_STRING_ESCAPER = Escapers.builder()
      .addEscape('"', "\"")
      .addEscape('\\', "\\\\")
      .addEscape('\n', "\\n")
      .build();

  /**
   * Returns an excerpt of the preamble required to emulate an inline call to Guava's
   * {@link Preconditions#checkNotNull(Object)} method.
   *
   * <p>If you use this, you <b>must</b> also use {@link #checkNotNullInline} to allow the check to
   * be performed inline when possible:
   *
   * <pre>code.add(checkNotNullPreamble("value"))
   *    .addLine("this.property = %s;", checkNotNullInline("value"));</pre>
   *
   * @param reference an excerpt containing the reference to pass to the checkNotNull method
   */
  public static Excerpt checkNotNullPreamble(final Object reference) {
    return new Excerpt() {
      @Override
      public void addTo(SourceBuilder code) {
        // No preamble needed
      }
    };
  }

  /**
   * Returns an excerpt equivalent to an inline call to Guava's
   * {@link Preconditions#checkNotNull(Object)}.
   *
   * <p>If you use this, you <b>must</b> also use {@link #checkNotNullPreamble} to allow the
   * check to be performed out-of-line if necessary:
   *
   * <pre>code.add(checkNotNullPreamble("value"))
   *    .addLine("this.property = %s;", checkNotNullInline("value"));</pre>
   *
   * @param reference an excerpt containing the reference to pass to the checkNotNull method
   */
  public static Excerpt checkNotNullInline(final Object reference) {
    return new Excerpt() {
      @Override
      public void addTo(SourceBuilder code) {
        code.add("%s.checkNotNull(%s)", Preconditions.class, reference);
      }
    };
  }

  /**
   * Returns an excerpt equivalent to Guava's {@link Preconditions#checkNotNull(Object)}:
   *
   * <pre>code.add(checkNotNull("key"))
   *    .add(checkNotNull("value"))
   *    .addLine("map.put(key, value);");</pre>
   *
   * @param reference an excerpt containing the reference to pass to the checkNotNull method
   */
  public static Excerpt checkNotNull(final Object reference) {
    return new Excerpt() {
      @Override
      public void addTo(SourceBuilder code) {
        code.addLine("%s.checkNotNull(%s);", Preconditions.class, reference);
      }
    };
  }

  /**
   * Returns an excerpt equivalent to Guava's
   * {@link Preconditions#checkArgument(boolean, String, Object...)}.
   *
   * <pre>code.add(checkArgument("age &gt;= 0", "age must be non-negative (got %s)", "age"));</pre>
   *
   * @param condition an excerpt containing the expression to pass to the checkArgument method
   * @param message the error message template to pass to the checkArgument method
   * @param args excerpts containing the error message arguments to pass to the checkArgument method
   */
  public static Excerpt checkArgument(
      final Object condition,
      final String message,
      final Object... args) {
    return guavaCheckExcerpt("checkArgument", condition, message, args);
  }

  /**
   * Returns an excerpt equivalent to Guava's
   * {@link Preconditions#checkState(boolean, String, Object...)}.
   *
   * <pre>code.add(checkState("start &lt; end",
   *         "start must be before end (got %s and %s)", "start", "end"));</pre>
   *
   * @param condition an excerpt containing the expression to pass to the checkState method
   * @param message the error message template to pass to the checkState method
   * @param args excerpts containing the error message arguments to pass to the checkState method
   */
  public static Excerpt checkState(
      final Object condition,
      final String message,
      final Object... args) {
    return guavaCheckExcerpt("checkState", condition, message, args);
  }

  private static Excerpt guavaCheckExcerpt(
      final String methodName, final Object condition, final String message, final Object... args) {
    return new Excerpt() {
      @Override
      public void addTo(SourceBuilder code) {
        code.add("%s.%s(%s, \"%s\"",
            Preconditions.class, methodName, condition, JAVA_STRING_ESCAPER.escape(message));
        for (Object arg : args) {
          code.add(", %s", arg);
        }
        code.add(");\n");
      }
    };
  }

  private PreconditionExcerpts() {}
}
