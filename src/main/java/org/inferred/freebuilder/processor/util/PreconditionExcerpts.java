package org.inferred.freebuilder.processor.util;

import static org.inferred.freebuilder.processor.util.feature.GuavaLibrary.GUAVA;

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
        for (Object arg : args) {
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
