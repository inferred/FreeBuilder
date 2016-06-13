package org.inferred.freebuilder.processor.util;

import static org.inferred.freebuilder.processor.util.feature.GuavaLibrary.GUAVA;
import static org.inferred.freebuilder.processor.util.feature.SourceLevel.SOURCE_LEVEL;

import com.google.common.base.Preconditions;
import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Code snippets that call or emulate Guava's {@link Preconditions} methods.
 */
public class PreconditionExcerpts {

  private static final class GuavaCheckExcerpt extends Excerpt {
    private final Object[] args;
    private final Object condition;
    private final String message;
    private final String methodName;
    private final Class<? extends RuntimeException> exceptionType;

    private GuavaCheckExcerpt(Object[] args, Object condition, String message, String methodName,
        Class<? extends RuntimeException> exceptionType) {
      this.args = args;
      this.condition = condition;
      this.message = message;
      this.methodName = methodName;
      this.exceptionType = exceptionType;
    }

    @Override
    public void addTo(SourceBuilder code) {
      if (code.feature(GUAVA).isAvailable()) {
        code.add("%s.%s(%s, \"%s\"",
            Preconditions.class,
            methodName,
            condition,
            JAVA_STRING_ESCAPER.escape(message));
        for (Object arg : args) {
          code.add(", %s", arg);
        }
        code.add(");\n");
      } else {
        List<Excerpt> escapedArgs = new ArrayList<Excerpt>();
        for (final Object arg : args) {
          escapedArgs.add(Excerpts.add("\" + %s + \"", arg));
        }
        String messageConcatenated = code.subBuilder()
            .add("\"" + JAVA_STRING_ESCAPER.escape(message) + "\"", escapedArgs.toArray())
            .toString()
            .replace("\"\" + ", "")
            .replace(" + \"\"", "");
        code.addLine("if (%s) {", negate(code, condition))
            .addLine("  throw new %s(%s);", exceptionType, messageConcatenated)
            .addLine("}");
      }
    }

    @Override
    protected void addFields(FieldReceiver fields) {
      fields.add("methodName", methodName);
      fields.add("exceptionType", exceptionType);
      fields.add("condition", condition);
      fields.add("message", message);
      fields.add("args", Arrays.asList(args));
    }
  }

  private static final class CheckNotNullPreambleExcerpt extends Excerpt {
    private final Object reference;

    private CheckNotNullPreambleExcerpt(Object reference) {
      this.reference = reference;
    }

    @Override
    public void addTo(SourceBuilder code) {
      if (code.feature(GUAVA).isAvailable()) {
        // No preamble needed
      } else if (code.feature(SOURCE_LEVEL).javaUtilObjects().isPresent()) {
        // No preamble needed
      } else {
        code.addLine("if (%s == null) {", reference)
            .addLine("  throw new NullPointerException();")
            .addLine("}");
      }
    }

    @Override
    protected void addFields(FieldReceiver fields) {
      fields.add("reference", reference);
    }
  }

  private static final class CheckNotNullInlineExcerpt extends Excerpt {
    private final Object reference;

    private CheckNotNullInlineExcerpt(Object reference) {
      this.reference = reference;
    }

    @Override
    public void addTo(SourceBuilder code) {
      if (code.feature(GUAVA).isAvailable()) {
        code.add("%s.checkNotNull(%s)", Preconditions.class, reference);
      } else if (code.feature(SOURCE_LEVEL).javaUtilObjects().isPresent()) {
        code.add("%s.requireNonNull(%s)",
            code.feature(SOURCE_LEVEL).javaUtilObjects().get(), reference);
      } else {
        code.add("%s", reference);
      }
    }

    @Override
    protected void addFields(FieldReceiver fields) {
      fields.add("reference", reference);
    }
  }

  private static final class CheckNotNullExcerpt extends Excerpt {
    private final Object reference;

    private CheckNotNullExcerpt(Object reference) {
      this.reference = reference;
    }

    @Override
    public void addTo(SourceBuilder code) {
      if (code.feature(GUAVA).isAvailable()) {
        code.addLine("%s.checkNotNull(%s);", Preconditions.class, reference);
      } else if (code.feature(SOURCE_LEVEL).javaUtilObjects().isPresent()) {
        code.addLine("%s.requireNonNull(%s);",
            code.feature(SOURCE_LEVEL).javaUtilObjects().get(), reference);
      } else {
        code.addLine("if (%s == null) {", reference)
            .addLine("  throw new NullPointerException();")
            .addLine("}");
      }
    }

    @Override
    protected void addFields(FieldReceiver fields) {
      fields.add("reference", reference);
    }
  }

  private static final Escaper JAVA_STRING_ESCAPER = Escapers.builder()
      .addEscape('"', "\"")
      .addEscape('\\', "\\\\")
      .addEscape('\n', "\\n")
      .build();
  /**
   * Matches all operators with a lower precedence than unary negation (!).
   *
   * <p>False positives are acceptable, as the only downside is putting unnecessary brackets around
   * the condition when Guava is not available, so a simple check for offending characters is fine,
   * even though they might actually be in a string.
   */
  private static final Pattern ANY_OPERATOR = Pattern.compile("[+=<>!&^|?:]|\\binstanceof\\b");

  /**
   * Returns an excerpt of the preamble required to emulate an inline call to Guava's
   * {@link Preconditions#checkNotNull(Object)} method.
   *
   * <p>If Guava or Java 7's Objects are available, no preamble will be generated. Otherwise, the
   * check will be done out-of-line with an if block in this excerpt.
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
    return new CheckNotNullPreambleExcerpt(reference);
  }

  /**
   * Returns an excerpt equivalent to an inline call to Guava's
   * {@link Preconditions#checkNotNull(Object)}.
   * <ul>
   * <li>If Guava is available, Preconditions.checkNotNull will be used.
   * <li>If Objects.requireNonNull is available, it will be used if Guava cannot be.
   * <li>If neither are available, the check will be done out-of-line with an if block.
   * </ul>
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
    return new CheckNotNullInlineExcerpt(reference);
  }

  /**
   * Returns an excerpt equivalent to Guava's {@link Preconditions#checkNotNull(Object)}.
   * <ul>
   * <li>If Guava is available, Preconditions.checkNotNull will be used.
   * <li>If Objects.requireNonNull is available, it will be used if Guava cannot be.
   * <li>If neither are available, the check will be done with an if block.
   * </ul>
   *
   * <pre>code.add(checkNotNull("key"))
   *    .add(checkNotNull("value"))
   *    .addLine("map.put(key, value);");</pre>
   *
   * @param reference an excerpt containing the reference to pass to the checkNotNull method
   */
  public static Excerpt checkNotNull(final Object reference) {
    return new CheckNotNullExcerpt(reference);
  }

  /**
   * Returns an excerpt equivalent to Guava's
   * {@link Preconditions#checkArgument(boolean, String, Object...)}.
   * <ul>
   * <li>If Guava is available, Preconditions.checkArgument will be used.
   * <li>Otherwise, the check will be done with an if block.
   * </ul>
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
    return new GuavaCheckExcerpt(
        args, condition, message, "checkArgument", IllegalArgumentException.class);
  }

  /**
   * Returns an excerpt equivalent to Guava's
   * {@link Preconditions#checkState(boolean, String, Object...)}.
   * <ul>
   * <li>If Guava is available, Preconditions.checkState will be used.
   * <li>Otherwise, the check will be done with an if block.
   * </ul>
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
    return new GuavaCheckExcerpt(
        args, condition, message, "checkState", IllegalStateException.class);
  }

  /**
   * Negates {@code condition}, removing unnecessary brackets and double-negatives if possible.
   */
  private static String negate(SourceBuilder code, Object condition) {
    SourceStringBuilder subBuilder = code.subBuilder();
    subBuilder.add("%s", condition);
    String conditionText = subBuilder.toString();
    if (conditionText.startsWith("!")) {
      return conditionText.substring(1);
    } else if (ANY_OPERATOR.matcher(conditionText).find()) {
      // The condition might already enclosed in a bracket, but we can't simply check for opening
      // and closing brackets at the start and end of the string, as that misses cases like
      // (a || b) && (c || d). Attempting to determine if the initial and closing bracket are paired
      // requires understanding character constants and strings constants, so for simplicity we
      // just add unnecessary brackets.
      return "!(" + conditionText + ")";
    } else {
      return "!" + conditionText;
    }
  }

  private PreconditionExcerpts() {}
}
