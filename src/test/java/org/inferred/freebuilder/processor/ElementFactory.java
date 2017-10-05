package org.inferred.freebuilder.processor;

import static com.google.common.base.Preconditions.checkState;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;

import org.inferred.freebuilder.processor.testtype.AbstractNonComparable;
import org.inferred.freebuilder.processor.testtype.NonComparable;
import org.inferred.freebuilder.processor.testtype.OtherNonComparable;
import org.inferred.freebuilder.processor.util.NameImpl;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.annotation.Nullable;

public enum ElementFactory {
  STRINGS(
      String.class,
      "String",
      null,
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
      Integer.class,
      "int",
      null,
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
      28),
  NON_COMPARABLES(
      NonComparable.class,
      NonComparable.class.getName(),
      AbstractNonComparable.ReverseIdComparator.class,
      AbstractNonComparable.class,
      "element.id() >= 0",
      "element.id() >= 0",
      "ID must be non-negative",
      new NonComparable(-2, "broken"),
      new OtherNonComparable(88, "other"),
      new NonComparable(10, "alpha"),
      new NonComparable(9, "cappa"),
      new NonComparable(8, "echo"),
      new NonComparable(7, "golf"),
      new NonComparable(6, "beta"),
      new NonComparable(5, "delta"),
      new NonComparable(4, "foxtrot"));

  private final Class<?> type;
  private final String unwrappedType;
  @Nullable private final Class<?> comparator;
  private Class<?> supertype;
  private final String validation;
  private final String supertypeValidation;
  private final String errorMessage;
  private final Object invalidExample;
  private final Object supertypeExample;
  private final ImmutableList<Object> examples;

  ElementFactory(
      Class<?> type,
      String unwrappedType,
      @Nullable Class<? extends Comparator<?>> comparator,
      Class<?> supertype,
      String validation,
      String supertypeValidation,
      String errorMessage,
      Object invalidExample,
      Object supertypeExample,
      Object... examples) {
    this.type = type;
    this.unwrappedType = unwrappedType;
    this.comparator = comparator;
    this.supertype = supertype;
    this.validation = validation;
    this.supertypeValidation = supertypeValidation;
    this.errorMessage = errorMessage;
    this.invalidExample = invalidExample;
    this.supertypeExample = supertypeExample;
    this.examples = ImmutableList.copyOf(examples);
    checkExamplesOrdered(comparator, this.examples);
  }

  public Class<?> type() {
    return type;
  }

  public String unwrappedType() {
    return unwrappedType;
  }

  public Optional<Class<?>> comparator() {
    return Optional.ofNullable(comparator);
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

  @Override
  public String toString() {
    return type.getSimpleName();
  }

  private static String toSource(Object example) {
    if (example instanceof String) {
      return "\"" + example + "\"";
    } else if (example instanceof NameImpl) {
      return "new " + NameImpl.class.getName() + "(\"" + example + "\")";
    } else if (example instanceof NonComparable) {
      NonComparable nonComparable = (NonComparable) example;
      return "new " + NonComparable.class.getName() + "(" + nonComparable.id() + ", \""
          + nonComparable.name() + "\")";
    } else if (example instanceof OtherNonComparable) {
      OtherNonComparable otherNonComparable = (OtherNonComparable) example;
      return "new " + OtherNonComparable.class.getName() + "(" + otherNonComparable.id() + ", \""
          + otherNonComparable.name() + "\")";
    } else {
      return example.toString();
    }
  }

  private static void checkExamplesOrdered(
      @Nullable Class<? extends Comparator<?>> comparator,
      Collection<?> examples) {
    if (comparator == null) {
      List<Comparable<?>> comparables =
          examples.stream().map(v -> (Comparable<?>) v).collect(toList());
      checkState(Ordering.natural().isOrdered(comparables),
          "Examples must be in natural order (got %s)", examples);
    } else {
      try {
        @SuppressWarnings("rawtypes")
        Ordering ordering = Ordering.from(comparator.newInstance());
        @SuppressWarnings("unchecked")
        boolean isOrdered = ordering.isOrdered(examples);
        checkState(isOrdered);
      } catch (ReflectiveOperationException e) {
        throw new RuntimeException(e);
      }
    }
  }
}