package org.inferred.freebuilder.processor.property;

import static com.google.common.base.Preconditions.checkState;
import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Primitives;
import com.google.common.reflect.TypeToken;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import org.inferred.freebuilder.processor.model.NameImpl;
import org.inferred.freebuilder.processor.testtype.AbstractNonComparable;
import org.inferred.freebuilder.processor.testtype.NonComparable;
import org.inferred.freebuilder.processor.testtype.OtherNonComparable;

public enum ElementFactory {
  STRINGS(
      String.class,
      new TypeToken<UnaryOperator<String>>() {},
      null,
      CharSequence.class,
      true,
      "%s.intern()",
      "!%s.isEmpty()",
      "!element.toString().isEmpty()",
      "%s cannot be empty",
      "",
      new NameImpl("echo"),
      i -> Arrays.asList("alpha", "beta", "cappa", "delta", "echo", "foxtrot", "golf").get(i)),
  SHORTS(
      Short.class,
      new TypeToken<UnaryOperator<Short>>() {},
      null,
      Number.class,
      true,
      "Short.valueOf((short) %s)",
      "%s >= 0",
      "element.shortValue() >= 0",
      "%s must be non-negative",
      (short) -4,
      3.1,
      i -> (short) (i * (i + 1) / 2)),
  INTEGERS(
      Integer.class,
      new TypeToken<IntUnaryOperator>() {},
      null,
      Number.class,
      true,
      "Integer.valueOf((int) %s)",
      "%s >= 0",
      "element.intValue() >= 0",
      "%s must be non-negative",
      -4,
      2.7,
      i -> (int) (i * (i + 1) / 2)),
  NON_COMPARABLES(
      NonComparable.class,
      new TypeToken<UnaryOperator<NonComparable>>() {},
      AbstractNonComparable.ReverseIdComparator.class,
      AbstractNonComparable.class,
      false,
      "%s.intern()",
      "%s.id() >= 0",
      "element.id() >= 0",
      "%s must be non-negative",
      NonComparable.of(-2, "broken"),
      new OtherNonComparable(88, "other"),
      i -> NonComparable.of(10 - i, Integer.toString(i)));

  /**
   * A couple of basic types, one primitive and one not, to test type handling without excessive
   * code generation.
   */
  public static final List<ElementFactory> TYPES = ImmutableList.of(INTEGERS, STRINGS);

  /**
   * Three types, one primitive, one comparable and one not, to test type handling of lookup-based
   * collections (like OrderedSet) where it's easy to mistakenly rely on the default comparator and
   * get runtime exceptions.
   */
  public static final List<ElementFactory> TYPES_WITH_NON_COMPARABLE =
      ImmutableList.of(INTEGERS, STRINGS, NON_COMPARABLES);

  /** An extended set of types to test primitive-handing edge cases. */
  public static final List<ElementFactory> TYPES_WITH_EXTRA_PRIMITIVES =
      ImmutableList.of(SHORTS, INTEGERS, STRINGS);

  private final Class<?> type;
  private final TypeToken<?> unaryOperator;
  @Nullable private final Class<?> comparator;
  private Class<?> supertype;
  private final boolean serializableAsMapKey;
  private final String intern;
  private final String validation;
  private final String supertypeValidation;
  private final String errorMessage;
  private final Object invalidExample;
  private final Object supertypeExample;
  private final IntFunction<?> exampleGenerator;

  <S, T extends S> ElementFactory(
      Class<T> type,
      TypeToken<?> unaryOperator,
      @Nullable Class<? extends Comparator<S>> comparator,
      Class<S> supertype,
      boolean serializableAsMapKey,
      String intern,
      String validation,
      String supertypeValidation,
      String errorMessage,
      Object invalidExample,
      Object supertypeExample,
      IntFunction<T> exampleGenerator) {
    this.type = type;
    this.unaryOperator = unaryOperator;
    this.comparator = comparator;
    this.supertype = supertype;
    this.serializableAsMapKey = serializableAsMapKey;
    this.intern = intern;
    this.validation = validation;
    this.supertypeValidation = supertypeValidation;
    this.errorMessage = errorMessage;
    this.invalidExample = invalidExample;
    this.supertypeExample = supertypeExample;
    this.exampleGenerator = exampleGenerator;
    checkExamplesOrdered(comparator, exampleGenerator);
  }

  public Class<?> type() {
    return type;
  }

  public Class<?> unwrappedType() {
    return Primitives.unwrap(type);
  }

  public TypeToken<?> unboxedUnaryOperator() {
    return unaryOperator;
  }

  public Optional<Class<?>> comparator() {
    return Optional.ofNullable(comparator);
  }

  public Class<?> supertype() {
    return supertype;
  }

  public boolean isSerializableAsMapKey() {
    return serializableAsMapKey;
  }

  public boolean canRepresentSingleNullElement() {
    return !Primitives.isWrapperType(type);
  }

  public String validation() {
    return validation("element");
  }

  public String validation(String variableName) {
    return String.format(validation, variableName);
  }

  public String supertypeValidation() {
    return supertypeValidation;
  }

  public String errorMessage() {
    return errorMessage("element");
  }

  public String errorMessage(String variableName) {
    return String.format(errorMessage, variableName);
  }

  public String invalidExample() {
    return toSource(invalidExample);
  }

  public String supertypeExample() {
    return toSource(supertypeExample);
  }

  public String example(int id) {
    return toSource(exampleGenerator.apply(id));
  }

  public String examples(int... ids) {
    return IntStream.of(ids).mapToObj(this::example).collect(Collectors.joining(", "));
  }

  public String exampleToString(int id) {
    return exampleGenerator.apply(id).toString();
  }

  public Object intern(String variableName) {
    return String.format(intern, variableName);
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
      return NonComparable.class.getName()
          + ".of("
          + nonComparable.id()
          + ", \""
          + nonComparable.name()
          + "\")";
    } else if (example instanceof OtherNonComparable) {
      OtherNonComparable otherNonComparable = (OtherNonComparable) example;
      return "new "
          + OtherNonComparable.class.getName()
          + "("
          + otherNonComparable.id()
          + ", \""
          + otherNonComparable.name()
          + "\")";
    } else if (example instanceof Short) {
      short value = (short) example;
      return "((short) " + value + ")";
    } else {
      return example.toString();
    }
  }

  private static void checkExamplesOrdered(
      @Nullable Class<? extends Comparator<?>> comparator, IntFunction<?> exampleGenerator) {
    if (comparator == null) {
      List<Comparable<?>> comparables =
          IntStream.range(0, 7)
              .mapToObj(exampleGenerator)
              .map(v -> (Comparable<?>) v)
              .collect(toList());
      checkState(
          Ordering.natural().isOrdered(comparables),
          "Examples must be in natural order (got %s)",
          comparables);
    } else {
      try {
        @SuppressWarnings("rawtypes")
        Ordering ordering = Ordering.from(comparator.newInstance());
        List<?> comparables = range(0, 7).mapToObj(exampleGenerator).collect(toList());
        @SuppressWarnings("unchecked")
        boolean isOrdered = ordering.isOrdered(comparables);
        checkState(isOrdered, "Examples must be in natural order (got %s)", comparables);
      } catch (ReflectiveOperationException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
