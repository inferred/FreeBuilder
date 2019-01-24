package org.inferred.freebuilder.processor.util;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.getOnlyElement;

import static org.inferred.freebuilder.processor.util.MethodFinder.methodsOn;
import static org.inferred.freebuilder.processor.util.ModelUtils.asElement;
import static org.inferred.freebuilder.processor.util.ModelUtils.maybeDeclared;
import static org.inferred.freebuilder.processor.util.ModelUtils.only;

import static java.util.stream.Collectors.toList;

import static javax.lang.model.element.Modifier.ABSTRACT;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.DoubleUnaryOperator;
import java.util.function.IntUnaryOperator;
import java.util.function.LongUnaryOperator;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Metadata about a functional interface.
 */
public class FunctionalType extends ValueType {

  /**
   * Returns {@code Consumer<type>}.
   *
   * @throws IllegalArgumentException if {@code type} is primitive
   */
  public static FunctionalType consumer(TypeMirror type) {
    checkArgument(!type.getKind().isPrimitive(), "Unexpected primitive type %s", type);
    return new FunctionalType(
        QualifiedName.of(Consumer.class).withParameters(type),
        "accept",
        ImmutableList.of(type),
        null);
  }

  /**
   * Returns {@code UnaryOperator<type>}.
   *
   * @throws IllegalArgumentException if {@code type} is primitive
   */
  public static FunctionalType unaryOperator(TypeMirror type) {
    checkArgument(!type.getKind().isPrimitive(), "Unexpected primitive type %s", type);
    return new FunctionalType(
        QualifiedName.of(UnaryOperator.class).withParameters(type),
        "apply",
        ImmutableList.of(type),
        type);
  }

  /**
   * Returns one of {@link IntUnaryOperator}, {@link LongUnaryOperator} or
   * {@link DoubleUnaryOperator}, depending on {@code type}.
   *
   * @throws IllegalArgumentException if {@code type} is not one of int, long or double
   */
  public static FunctionalType primitiveUnaryOperator(PrimitiveType type) {
    switch (type.getKind()) {
      case INT:
        return new FunctionalType(
            Type.from(IntUnaryOperator.class),
            "applyAsInt",
            ImmutableList.of(type),
            type);

      case LONG:
        return new FunctionalType(
            Type.from(LongUnaryOperator.class),
            "applyAsLong",
            ImmutableList.of(type),
            type);

      case DOUBLE:
        return new FunctionalType(
            Type.from(DoubleUnaryOperator.class),
            "applyAsDouble",
            ImmutableList.of(type),
            type);

      default:
        throw new IllegalArgumentException("No primitive unary operator exists for " + type);
    }
  }

  /**
   * Returns a unary operator that will accept {@code type}, without autoboxing if possible.
   */
  public static FunctionalType unboxedUnaryOperator(TypeMirror type, Types types) {
    switch (type.getKind()) {
      case INT:
      case LONG:
      case DOUBLE:
        return primitiveUnaryOperator((PrimitiveType) type);

      case BOOLEAN:
      case BYTE:
      case CHAR:
      case SHORT:
      case FLOAT:
        return unaryOperator(types.boxedClass((PrimitiveType) type).asType());

      default:
        return unaryOperator(type);
    }
  }

  /**
   * Returns the functional type accepted by {@code methodName} on {@code type}, assignable to
   * {@code prototype}, or {@code prototype} itself if no such method has been declared.
   *
   * <p>Used to allow the user to override the functional interface used on builder methods,
   * e.g. to force boxing, or to use Guava types.
   */
  public static FunctionalType functionalTypeAcceptedByMethod(
      DeclaredType type,
      String methodName,
      FunctionalType prototype,
      Elements elements,
      Types types) {
    return functionalTypesAcceptedByMethod(type, methodName, elements, types)
        .stream()
        .filter(functionalType -> isAssignable(functionalType, prototype, types))
        .findAny()
        .orElse(prototype);
  }

  /** Returns the functional types accepted by {@code methodName} on {@code type}. */
  public static List<FunctionalType> functionalTypesAcceptedByMethod(
      DeclaredType type,
      String methodName,
      Elements elements,
      Types types) {
    TypeElement typeElement = asElement(type);
    return methodsOn(typeElement, elements, errorType -> { })
        .stream()
        .filter(method -> method.getSimpleName().contentEquals(methodName)
            && method.getParameters().size() == 1)
        .flatMap(method -> {
          ExecutableType methodType = (ExecutableType) types.asMemberOf(type, method);
          TypeMirror parameter = getOnlyElement(methodType.getParameterTypes());
          return maybeFunctionalType(parameter, elements, types)
              .map(Stream::of).orElse(Stream.of());
        })
        .collect(toList());
  }

  public static Optional<FunctionalType> maybeFunctionalType(
      TypeMirror type,
      Elements elements,
      Types types) {
    return maybeDeclared(type)
        .flatMap(declaredType -> maybeFunctionalType(declaredType, elements, types));
  }

  public static Optional<FunctionalType> maybeFunctionalType(
      DeclaredType type,
      Elements elements,
      Types types) {
    TypeElement typeElement = asElement(type);
    if (!typeElement.getKind().isInterface()) {
      return Optional.empty();
    }
    Set<ExecutableElement> abstractMethods =
        only(ABSTRACT, methodsOn(typeElement, elements, errorType -> { }));
    if (abstractMethods.size() != 1) {
      return Optional.empty();
    }
    ExecutableElement method = getOnlyElement(abstractMethods);
    ExecutableType methodType = (ExecutableType) types.asMemberOf(type, method);
    return Optional.of(new FunctionalType(
        Type.from(type),
        method.getSimpleName().toString(),
        methodType.getParameterTypes(),
        methodType.getReturnType()));
  }

  public static boolean isAssignable(FunctionalType fromType, FunctionalType toType, Types types) {
    if (toType.getParameters().size() != fromType.getParameters().size()) {
      return false;
    }
    for (int i = 0; i < toType.getParameters().size(); ++i) {
      TypeMirror fromParam = fromType.getParameters().get(i);
      TypeMirror toParam = toType.getParameters().get(i);
      if (!isAssignable(fromParam, toParam, types)) {
        return false;
      }
    }
    return isAssignable(fromType.getReturnType(), toType.getReturnType(), types);
  }

  private static boolean isAssignable(TypeMirror fromParam, TypeMirror toParam, Types types) {
    if (isVoid(fromParam) || isVoid(toParam)) {
      return isVoid(fromParam) && isVoid(toParam);
    }
    return types.isAssignable(fromParam, toParam);
  }

  private static boolean isVoid(TypeMirror type) {
    return type == null || type.getKind() == TypeKind.VOID;
  }

  private final Type functionalInterface;
  private final String methodName;
  private final List<TypeMirror> parameters;
  private final TypeMirror returnType;

  private FunctionalType(
      Type functionalInterface,
      String methodName,
      Collection<? extends TypeMirror> parameters,
      TypeMirror returnType) {
    this.functionalInterface = functionalInterface;
    this.methodName = methodName;
    this.parameters = ImmutableList.copyOf(parameters);
    this.returnType = returnType;
  }

  public Type getFunctionalInterface() {
    return functionalInterface;
  }

  public String getMethodName() {
    return methodName;
  }

  public List<TypeMirror> getParameters() {
    return parameters;
  }

  public TypeMirror getReturnType() {
    return returnType;
  }

  public boolean canReturnNull() {
    return !returnType.getKind().isPrimitive();
  }

  @Override
  protected void addFields(FieldReceiver fields) {
    fields.add("functionalInterface", functionalInterface);
    fields.add("methodName", methodName);
    List<String> parametersAsStrings = new ArrayList<>();
    for (TypeMirror parameter : parameters) {
      parametersAsStrings.add(parameter.toString());
    }
    fields.add("parameters", parametersAsStrings);
    fields.add("returnType", returnType.toString());
  }

  @Override
  public String toString() {
    return functionalInterface.toString();
  }
}
