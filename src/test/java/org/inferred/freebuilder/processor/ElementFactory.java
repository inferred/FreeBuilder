package org.inferred.freebuilder.processor;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

public enum ElementFactory {
  STRINGS(
      "String",
      "String",
      "!element.isEmpty()",
      "Cannot add empty string",
      "",
      "alpha",
      "beta",
      "cappa",
      "delta"),
  INTEGERS(
      "Integer",
      "int",
      "element >= 0",
      "Items must be non-negative",
      -4,
      1,
      3,
      6,
      10);

  private final String type;
  private final String unwrappedType;
  private final String validation;
  private final String errorMessage;
  private final Comparable<?> invalidExample;
  private final ImmutableList<Comparable<?>> examples;

  ElementFactory(
      String type,
      String unwrappedType,
      String validation,
      String errorMessage,
      Comparable<?> invalidExample,
      Comparable<?>... examples) {
    this.type = type;
    this.unwrappedType = unwrappedType;
    this.validation = validation;
    this.errorMessage = errorMessage;
    this.invalidExample = invalidExample;
    this.examples = ImmutableList.copyOf(examples);
    checkState(Ordering.natural().isOrdered(this.examples),
        "Examples must be in natural order (got %s)", this.examples);
  }

  public String type() {
    return type;
  }

  public String unwrappedType() {
    return unwrappedType;
  }

  public boolean canRepresentSingleNullElement() {
    return type.equals(unwrappedType);
  }

  public String validation() {
    return validation;
  }

  public String errorMessage() {
    return errorMessage;
  }

  public String invalidExample() {
    return toSource(invalidExample);
  }

  public String example(int id) {
    return toSource(examples.get(id));
  }

  public String examples(int... ids) {
    return IntStream.of(ids).mapToObj(this::example).collect(Collectors.joining(", "));
  }

  private static String toSource(Comparable<?> example) {
    if (example instanceof String) {
      return "\"" + example + "\"";
    } else {
      return example.toString();
    }
  }

  @Override
  public String toString() {
    return type;
  }
}