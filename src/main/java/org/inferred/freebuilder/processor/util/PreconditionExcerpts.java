package org.inferred.freebuilder.processor.util;

import static org.inferred.freebuilder.processor.util.PreconditionExcerpts.StateCondition.IS;

import com.google.common.base.Preconditions;
import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;

/**
 * Code snippets that call or emulate Guava's {@link Preconditions} methods.
 */
public class PreconditionExcerpts {

  private static final Escaper JAVA_STRING_ESCAPER =
      Escapers.builder().addEscape('"', "\"").build();

  public static enum StateCondition {
    IS, IS_NOT
  }

  /**
   * Returns an excerpt of the preamble required to emulate an inline call to Guava's
   * {@link Preconditions#checkNotNull(Object)} method.
   *
   * <p>If you use this, you <b>must</b> also use {@link #checkNotNullInline} to perform the check
   * inline when possible.
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
   * {@link Preconditions#checkNotNull(Object)}:
   *
   * <p>If you use this, you <b>must</b> also use {@link #checkNotNullPreamble} to perform the
   * out-of-line check if necessary.
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
   * Returns an excerpt equivalent to a call to Guava's
   * {@link Preconditions#checkNotNull(Object)}:
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
   * @param isOrIsNot whether to negate {@code condition} or not
   * @param condition an excerpt containing the expression to pass to the checkArgument method
   * @param message the error message template to pass to the checkArgument method
   * @param args excerpts containing the error message arguments to pass to the checkArgument method
   */
  public static Excerpt checkArgument(
      final StateCondition isOrIsNot,
      final Object condition,
      final String message,
      final Object... args) {
    return new Excerpt() {
      @Override
      public void addTo(SourceBuilder code) {
        boolean addNewlines = excerptIsLong(code, condition);
        code.add("%s.checkArgument(%s%s%s,%s\"%s\"",
            Preconditions.class,
            addNewlines ? "\n" : "",
            isOrIsNot == IS ? "" : "!",
            condition,
            addNewlines ? "\n" : " ",
            JAVA_STRING_ESCAPER.escape(message));
        for (Object arg : args) {
          code.add(", %s", arg);
        }
        code.add(");\n");
      }
    };
  }

  /**
   * Returns an excerpt equivalent to Guava's
   * {@link Preconditions#checkState(boolean, String, Object...)}.
   *
   * @param isOrIsNot whether to negate {@code condition} or not
   * @param condition an excerpt containing the expression to pass to the checkState method
   * @param message the error message template to pass to the checkState method
   * @param args excerpts containing the error message arguments to pass to the checkState method
   */
  public static Excerpt checkState(
      final StateCondition isOrIsNot,
      final Object condition,
      final String message,
      final Object... args) {
    return new Excerpt() {
      @Override
      public void addTo(SourceBuilder code) {
        boolean addNewlines = excerptIsLong(code, condition);
        code.add("%s.checkState(%s%s%s,%s\"%s\"",
            Preconditions.class,
            addNewlines ? "\n" : "",
            isOrIsNot == IS ? "" : "!",
            condition,
            addNewlines ? "\n" : " ",
            JAVA_STRING_ESCAPER.escape(message));
        for (Object arg : args) {
          code.add(", %s", arg);
        }
        code.add(");\n");
      }
    };
  }

  private static boolean excerptIsLong(SourceBuilder code, final Object excerpt) {
    String excerptAsString = SourceStringBuilder
        .simple(code.getSourceLevel())
        .add("%s", excerpt)
        .toString();
    boolean addNewlines = (excerptAsString.length() > 50);
    return addNewlines;
  }

  private PreconditionExcerpts() {}
}
