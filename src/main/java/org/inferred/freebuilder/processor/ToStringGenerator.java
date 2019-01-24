package org.inferred.freebuilder.processor;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getLast;

import static org.inferred.freebuilder.processor.property.DefaultProperty.UNSET_PROPERTIES;

import org.inferred.freebuilder.processor.property.Property;
import org.inferred.freebuilder.processor.property.PropertyCodeGenerator;
import org.inferred.freebuilder.processor.property.PropertyCodeGenerator.Initially;
import org.inferred.freebuilder.processor.source.Excerpt;
import org.inferred.freebuilder.processor.source.SourceBuilder;
import org.inferred.freebuilder.processor.source.Variable;

import java.util.Map;
import java.util.function.Predicate;

class ToStringGenerator {

  /**
   * Generates a toString method using concatenation or a StringBuilder.
   */
  public static void addToString(
      SourceBuilder code,
      Datatype datatype,
      Map<Property, PropertyCodeGenerator> generatorsByProperty,
      boolean forPartial) {
    String typename = (forPartial ? "partial " : "") + datatype.getType().getSimpleName();
    Predicate<PropertyCodeGenerator> isOptional = generator -> {
      Initially initially = generator.initialState();
      return (initially == Initially.OPTIONAL || (initially == Initially.REQUIRED && forPartial));
    };
    boolean anyOptional = generatorsByProperty.values().stream().anyMatch(isOptional);
    boolean allOptional = generatorsByProperty.values().stream().allMatch(isOptional)
        && !generatorsByProperty.isEmpty();

    code.addLine("")
        .addLine("@%s", Override.class)
        .addLine("public %s toString() {", String.class);
    if (allOptional) {
      bodyWithBuilderAndSeparator(code, datatype, generatorsByProperty, typename);
    } else if (anyOptional) {
      bodyWithBuilder(code, datatype, generatorsByProperty, typename, isOptional);
    } else {
      bodyWithConcatenation(code, generatorsByProperty, typename);
    }
    code.addLine("}");
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
      SourceBuilder code,
      Map<Property, PropertyCodeGenerator> generatorsByProperty,
      String typename) {
    code.add("  return \"%s{", typename);
    String prefix = "";
    for (Property property : generatorsByProperty.keySet()) {
      PropertyCodeGenerator generator = generatorsByProperty.get(property);
      code.add("%s%s=\" + %s + \"",
          prefix, property.getName(), (Excerpt) generator::addToStringValue);
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
      SourceBuilder code,
      Datatype datatype,
      Map<Property, PropertyCodeGenerator> generatorsByProperty,
      String typename,
      Predicate<PropertyCodeGenerator> isOptional) {
    Variable result = new Variable("result");

    code.add("  %1$s %2$s = new %1$s(\"%3$s{", StringBuilder.class, result, typename);
    boolean midStringLiteral = true;
    boolean midAppends = true;
    boolean prependCommas = false;

    PropertyCodeGenerator lastOptionalGenerator = generatorsByProperty.values()
        .stream()
        .filter(isOptional)
        .reduce((first, second) -> second)
        .get();

    for (Property property : generatorsByProperty.keySet()) {
      PropertyCodeGenerator generator = generatorsByProperty.get(property);
      if (isOptional.test(generator)) {
        if (midStringLiteral) {
          code.add("\")");
        }
        if (midAppends) {
          code.add(";%n  ");
        }
        code.add("if (");
        if (generator.initialState() == Initially.OPTIONAL) {
          generator.addToStringCondition(code);
        } else {
          code.add("!%s.contains(%s.%s)",
              UNSET_PROPERTIES, datatype.getPropertyEnum(), property.getAllCapsName());
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
        if (generator.equals(lastOptionalGenerator)) {
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
        code.add("%s=\").append(%s)", property.getName(), (Excerpt) generator::addToStringValue);
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
      SourceBuilder code,
      Datatype datatype,
      Map<Property, PropertyCodeGenerator> generatorsByProperty,
      String typename) {
    Variable result = new Variable("result");
    Variable separator = new Variable("separator");

    code.addLine("  %1$s %2$s = new %1$s(\"%3$s{\");", StringBuilder.class, result, typename);
    if (generatorsByProperty.size() > 1) {
      // If there's a single property, we don't actually use the separator variable
      code.addLine("  %s %s = \"\";", String.class, separator);
    }

    Property first = generatorsByProperty.keySet().iterator().next();
    Property last = getLast(generatorsByProperty.keySet());
    for (Property property : generatorsByProperty.keySet()) {
      PropertyCodeGenerator generator = generatorsByProperty.get(property);
      switch (generator.initialState()) {
        case HAS_DEFAULT:
          throw new RuntimeException("Internal error: unexpected default field");

        case OPTIONAL:
          code.addLine("  if (%s) {", (Excerpt) generator::addToStringCondition);
          break;

        case REQUIRED:
          code.addLine("  if (!%s.contains(%s.%s)) {",
              UNSET_PROPERTIES, datatype.getPropertyEnum(), property.getAllCapsName());
          break;
      }
      code.add("    ").add(result);
      if (property != first) {
        code.add(".append(%s)", separator);
      }
      code.add(".append(\"%s=\").append(%s)",
          property.getName(), (Excerpt) generator::addToStringValue);
      if (property != last) {
        code.add(";%n    %s = \", \"", separator);
      }
      code.add(";%n  }%n");
    }
    code.addLine("  return %s.append(\"}\").toString();", result);
  }

  private ToStringGenerator() { }
}
