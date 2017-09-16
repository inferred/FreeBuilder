package org.inferred.freebuilder.processor;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;

import org.inferred.freebuilder.processor.util.NameImpl;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

public enum ElementFactory {
  STRINGS(
      "String",
      "String",
      CharSequence.class,
      "!element.isEmpty()",
      "!element.toString().isEmpty()",
      "Cannot add empty string",
      "",
      new NameImpl("echo"),
      "alpha",
      "beta",
      "cappa",
      "delta",
      "echo",
      "foxtrot",
      "golf"),
  INTEGERS(
      "Integer",
      "int",
      Number.class,
      "element >= 0",
      "element.intValue() >= 0",
      "Items must be non-negative",
      -4,
      2.7,
      1,
      3,
      6,
      10,
      15,
      21,
      28);

  private final String type;
  private final String unwrappedType;
  private Class<?> supertype;
  private final String validation;
  private final String supertypeValidation;
  private final String errorMessage;
  private final Comparable<?> invalidExample;
  private final Object supertypeExample;
  private final ImmutableList<Comparable<?>> examples;

  ElementFactory(
      String type,
      String unwrappedType,
      Class<?> supertype,
      String validation,
      String supertypeValidation,
      String errorMessage,
      Comparable<?> invalidExample,
      Object supertypeExample,
      Comparable<?>... examples) {
    this.type = type;
    this.unwrappedType = unwrappedType;
    this.supertype = supertype;
    this.validation = validation;
    this.supertypeValidation = supertypeValidation;
    this.errorMessage = errorMessage;
    this.invalidExample = invalidExample;
    this.supertypeExample = supertypeExample;
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

  public Class<?> supertype() {
    return supertype;
  }

  public boolean canRepresentSingleNullElement() {
    return type.equals(unwrappedType);
  }

  public String validation() {
    return validation;
  }

  public String supertypeValidation() {
    return supertypeValidation;
  }

  public String errorMessage() {
    return errorMessage;
  }

  public String invalidExample() {
    return toSource(invalidExample);
  }

  public String supertypeExample() {
    return toSource(supertypeExample);
  }

  public String example(int id) {
    return toSource(examples.get(id));
  }

  public String examples(int... ids) {
    return IntStream.of(ids).mapToObj(this::example).collect(Collectors.joining(", "));
  }

  private static String toSource(Object example) {
    if (example instanceof String) {
      return "\"" + example + "\"";
    } else if (example instanceof NameImpl) {
      return "new " + NameImpl.class.getName() + "(\"" + example + "\")";
    } else {
      return example.toString();
    }
  }

  @Override
  public String toString() {
    return type;
  }
}