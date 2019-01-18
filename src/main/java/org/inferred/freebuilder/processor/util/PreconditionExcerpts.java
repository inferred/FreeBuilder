package org.inferred.freebuilder.processor.util;

import static org.inferred.freebuilder.processor.util.Quotes.escapeJava;
import static org.inferred.freebuilder.processor.util.feature.GuavaLibrary.GUAVA;

import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Code snippets that call or emulate Guava's {@link Preconditions} methods.
 */
public class PreconditionExcerpts {

  /**
   * Matches all operators with a lower precedence than unary negation (!).
   *
   * <p>False positives are acceptable, as the only downside is putting unnecessary brackets around
   * the condition when Guava is not available, so a simple check for offending characters is fine,
   * even though they might actually be in a string.
   */
  private static final Pattern ANY_OPERATOR = Pattern.compile("[+=<>!&^|?:]|\\binstanceof\\b");
  private static final Pattern BOOLEAN_BINARY_OPERATOR = Pattern.compile("&&|\\|\\|");

  /**
   * Returns an excerpt equivalent to Guava's
   * {@link Preconditions#checkArgument(boolean, String, Object...)}.
   * <ul>
   * <li>If Guava is available, Preconditions.checkArgument will be used.
   * <li>Otherwise, the check will be done with an if block.
   * </ul>
   *
   * <pre>code.add(checkArgument("%1$s &gt;= 0", "age must be &gt;= 0 (got %1$s)", "age"));</pre>
   *
   * @param condition a code template to pass to the checkArgument method
   * @param message the error message template to pass to the checkArgument method
   * @param args excerpts containing the error message arguments to pass to the checkArgument method
   */
  public static Excerpt checkArgument(
      String conditionTemplate,
      String messageTemplate,
      Object... args) {
    return new GuavaCheckExcerpt(
        "checkArgument", conditionTemplate, messageTemplate, args, IllegalArgumentException.class);
  }

  /**
   * Returns an excerpt equivalent to Guava's
   * {@link Preconditions#checkState(boolean, String, Object...)}.
   * <ul>
   * <li>If Guava is available, Preconditions.checkState will be used.
   * <li>Otherwise, the check will be done with an if block.
   * </ul>
   *
   * <pre>code.add(checkState("%1$s &lt; %2$s",
   *         "start must be before end (got %1$s and %2$s)", "start", "end"));</pre>
   *
   * @param condition an excerpt containing the expression to pass to the checkState method
   * @param message the error message template to pass to the checkState method
   * @param args excerpts containing the error message arguments to pass to the checkState method
   */
  public static Excerpt checkState(
      String conditionTemplate,
      String messageTemplate,
      Object... args) {
    return new GuavaCheckExcerpt(
        "checkState", conditionTemplate, messageTemplate, args, IllegalStateException.class);
  }

  /**
   * Negates {@code conditionTemplate}, removing unnecessary brackets and double-negatives if
   * possible.
   */
  private static String negate(String conditionTemplate) {
    if (conditionTemplate.startsWith("!")
        && !BOOLEAN_BINARY_OPERATOR.matcher(conditionTemplate).find()) {
      return conditionTemplate.substring(1);
    } else if (ANY_OPERATOR.matcher(conditionTemplate).find()) {
      // The condition might already enclosed in a bracket, but we can't simply check for opening
      // and closing brackets at the start and end of the string, as that misses cases like
      // (a || b) && (c || d). Attempting to determine if the initial and closing bracket are paired
      // requires understanding character constants and strings constants, so for simplicity we
      // just add unnecessary brackets.
      return "!(" + conditionTemplate + ")";
    } else {
      return "!" + conditionTemplate;
    }
  }

  private static final class GuavaCheckExcerpt implements Excerpt {
    private final String methodName;
    private final String condition;
    private final String message;
    private final Object[] args;
    private final Class<? extends RuntimeException> exceptionType;

    private GuavaCheckExcerpt(
        String methodName,
        String condition,
        String message,
        Object[] args,
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
        addGuavaTo(code);
      } else {
        addIfBlockTo(code);
      }
    }

    private void addIfBlockTo(SourceBuilder code) {
      TemplateApplier templateApplier = TemplateApplier.withParams(args);
      code.add("if (");
      templateApplier
          .onText((csq, start, end) -> code.add("%s", csq.subSequence(start, end)))
          .onParam(param -> code.add("%s", param))
          .parse(negate(condition));
      code.addLine(") {")
          .add("  throw new %s(", exceptionType);
      AtomicBoolean inString = new AtomicBoolean(false);
      AtomicBoolean needsSeparator = new AtomicBoolean(false);
      templateApplier
          .onText((csq, start, end) -> {
            if (!inString.getAndSet(true)) {
              if (needsSeparator.getAndSet(true)) {
                code.add(" + ");
              }
              code.add("\"");
            }
            code.add("%s", escapeJava(csq.subSequence(start, end).toString()));
          })
          .onParam(param -> {
            if (inString.getAndSet(false)) {
              code.add("\"");
            }
            if (needsSeparator.getAndSet(true)) {
              code.add(" + ");
            }
            code.add("%s", param);
          })
          .parse(message);
      if (inString.get()) {
        code.add("\"");
      }
      code.addLine(");")
          .addLine("}");
    }

    private void addGuavaTo(SourceBuilder code) {
      TemplateApplier templateApplier = TemplateApplier.withParams(args);
      code.add("%s.%s(", Preconditions.class, methodName);
      templateApplier
          .onText((csq, start, end) -> code.add("%s", csq.subSequence(start, end)))
          .onParam(param -> code.add("%s", param))
          .parse(condition);
      code.add(", \"");
      List<Object> templateArgs = new ArrayList<>();
      templateApplier
          .onText((csq, start, end) -> code.add("%s",
              escapeJava(csq.subSequence(start, end).toString().replaceAll("%", "%%"))))
          .onParam(param -> {
            templateArgs.add(param);
            code.add("%%s");
          })
          .parse(message);
      code.add("\"");
      for (Object arg : templateArgs) {
        code.add(", %s", arg);
      }
      code.add(");\n");
    }
  }

  private PreconditionExcerpts() {}
}
