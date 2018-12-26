package org.inferred.freebuilder.processor;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.all;
import static com.google.common.collect.Iterables.any;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.getLast;
import static org.inferred.freebuilder.processor.CodeGenerator.UNSET_PROPERTIES;
import static org.inferred.freebuilder.processor.util.Block.methodBody;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.PropertyCodeGenerator.Type;
import org.inferred.freebuilder.processor.util.Block;
import org.inferred.freebuilder.processor.util.SourceBuilder;
import org.inferred.freebuilder.processor.util.Variable;

class ToStringGenerator {

  /**
   * Generates a toString method using concatenation or a StringBuilder.
   */
  public static void addToString(SourceBuilder code, Metadata metadata, final boolean forPartial) {
    String typename = (forPartial ? "partial " : "") + metadata.getType().getSimpleName();
    Predicate<Property> isOptional = new Predicate<Property>() {
      @Override
      public boolean apply(Property property) {
        Type type = property.getCodeGenerator().getType();
        return (type == Type.OPTIONAL || (type == Type.REQUIRED && forPartial));
      }
    };
    boolean anyOptional = any(metadata.getProperties(), isOptional);
    boolean allOptional = all(metadata.getProperties(), isOptional)
        && !metadata.getProperties().isEmpty();

    code.addLine("")
        .addLine("@%s", Override.class)
        .addLine("public %s toString() {", String.class);
    Block body = methodBody(code);
    if (allOptional) {
      bodyWithBuilderAndSeparator(body, metadata, typename);
    } else if (anyOptional) {
      bodyWithBuilder(body, metadata, typename, isOptional);
    } else {
      bodyWithConcatenation(body, metadata, typename);
    }
    code.add(body)
        .addLine("}");
  }

  /**
   * Generate the body of a toString method that uses plain concatenation.
   *
   * <p>Conventionally, we join properties with comma separators. If all of the properties are
   * always present, this can be done with a long block of unconditional code. We could use a
   * StringBuilder for this, but in fact the Java compiler will do this for us under the hood
   * if we use simple string concatenation, so we use the more readable approach.
   */
  private static void bodyWithConcatenation(
      Block code,
      Metadata metadata,
      String typename) {
    code.add("  return \"%s{", typename);
    String prefix = "";
    for (Property property : metadata.getProperties()) {
      code.add("%s%s=\" + %s + \"", prefix, property.getName(), property.getField());
      prefix = ", ";
    }
    code.add("}\";%n");
  }

  /**
   * Generates the body of a toString method that uses a StringBuilder.
   *
   * <p>Conventionally, we join properties with comma separators. If all of the properties are
   * optional, we have no choice but to track the separators at runtime, but if any of them will
   * always be present, we can actually do the hard work at compile time. Specifically, we can pick
   * the first such and output it without a comma; any property before it will have a comma
   * <em>appended</em>, and any property after it will have a comma <em>prepended</em>. This gives
   * us the right number of commas in the right places in all circumstances.
   *
   * <p>As well as keeping track of whether we are <b>prepending commas</b> yet (initially false),
   * we also keep track of whether we have just finished an if-then block for an optional property,
   * or if we are in the <b>middle of an append chain</b>, and if so, whether we are in the
   * <b>middle of a string literal</b>. This lets us output the fewest literals and statements,
   * much as a mildly compulsive programmer would when writing the same code.
   */
  private static void bodyWithBuilder(
      Block code,
      Metadata metadata,
      String typename,
      Predicate<Property> isOptional) {
    Variable result = new Variable("result");

    code.add("  %1$s %2$s = new %1$s(\"%3$s{", StringBuilder.class, result, typename);
    boolean midStringLiteral = true;
    boolean midAppends = true;
    boolean prependCommas = false;

    Property lastOptionalProperty = getLast(filter(metadata.getProperties(), isOptional));

    for (Property property : metadata.getProperties()) {
      if (isOptional.apply(property)) {
        if (midStringLiteral) {
          code.add("\")");
        }
        if (midAppends) {
          code.add(";%n  ");
        }
        code.add("if (");
        if (property.getCodeGenerator().getType() == Type.OPTIONAL) {
          code.add("%s != null", property.getField());
        } else {
          code.add("!%s.contains(%s.%s)",
              UNSET_PROPERTIES, metadata.getPropertyEnum(), property.getAllCapsName());
        }
        code.add(") {%n    %s.append(\"", result);
        if (prependCommas) {
          code.add(", ");
        }
        code.add("%s=\").append(%s)", property.getName(), property.getField());
        if (!prependCommas) {
          code.add(".append(\", \")");
        }
        code.add(";%n  }%n  ");
        if (property.equals(lastOptionalProperty)) {
          code.add("return %s.append(\"", result);
          midStringLiteral = true;
          midAppends = true;
        } else {
          midStringLiteral = false;
          midAppends = false;
        }
      } else {
        if (!midAppends) {
          code.add("%s", result);
        }
        if (!midStringLiteral) {
          code.add(".append(\"");
        }
        if (prependCommas) {
          code.add(", ");
        }
        code.add("%s=\").append(%s)", property.getName(), property.getField());
        midStringLiteral = false;
        midAppends = true;
        prependCommas = true;
      }
    }

    checkState(prependCommas, "Unexpected state at end of toString method");
    checkState(midAppends, "Unexpected state at end of toString method");
    if (!midStringLiteral) {
      code.add(".append(\"");
    }
    code.add("}\").toString();%n", result);
  }

  /**
   * Generates the body of a toString method that uses a StringBuilder and a separator variable.
   *
   * <p>Conventionally, we join properties with comma separators. If all of the properties are
   * optional, we have no choice but to track the separators at runtime, as apart from the first
   * one, all properties will need to have a comma prepended. We could do this with a boolean,
   * maybe called "separatorNeeded", or "firstValueOutput", but then we need either a ternary
   * operator or an extra nested if block. More readable is to use an initially-empty "separator"
   * string, which has a comma placed in it once the first value is written.
   *
   * <p>For extra tidiness, we note that the first if block need not try writing the separator
   * (it is always empty), and the last one need not update it (it will not be used again).
   */
  private static void bodyWithBuilderAndSeparator(
      Block code,
      Metadata metadata,
      String typename) {
    Variable result = new Variable("result");
    Variable separator = new Variable("separator");

    code.addLine("  %1$s %2$s = new %1$s(\"%3$s{\");", StringBuilder.class, result, typename);
    if (metadata.getProperties().size() > 1) {
      // If there's a single property, we don't actually use the separator variable
      code.addLine("  %s %s = \"\";", String.class, separator);
    }

    Property first = metadata.getProperties().get(0);
    Property last = Iterables.getLast(metadata.getProperties());
    for (Property property : metadata.getProperties()) {
      switch (property.getCodeGenerator().getType()) {
        case HAS_DEFAULT:
          throw new RuntimeException("Internal error: unexpected default field");

        case OPTIONAL:
          code.addLine("  if (%s != null) {", property.getField());
          break;

        case REQUIRED:
          code.addLine("  if (!%s.contains(%s.%s)) {",
              UNSET_PROPERTIES, metadata.getPropertyEnum(), property.getAllCapsName());
          break;
      }
      code.add("    ").add(result);
      if (property != first) {
        code.add(".append(%s)", separator);
      }
      code.add(".append(\"%s=\").append(%s)", property.getName(), property.getField());
      if (property != last) {
        code.add(";%n    %s = \", \"", separator);
      }
      code.add(";%n  }%n");
    }
    code.addLine("  return %s.append(\"}\").toString();", result);
  }

  private ToStringGenerator() { }
}
