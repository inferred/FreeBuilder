package org.inferred.freebuilder.processor.util;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static org.inferred.freebuilder.processor.util.MethodFinder.methodsOn;
import static org.inferred.freebuilder.processor.util.ModelUtils.asElement;
import static org.inferred.freebuilder.processor.util.ModelUtils.maybeDeclared;
import static org.inferred.freebuilder.processor.util.ModelUtils.only;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

import org.inferred.freebuilder.processor.util.MethodFinder.ErrorTypeHandling;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Metadata about a functional interface.
 */
public class FunctionalType extends ValueType {

  private static final String FUNCTION_PACKAGE = "java.util.function";

  public static final QualifiedName CONSUMER =
      QualifiedName.of(FUNCTION_PACKAGE, "Consumer");
  public static final QualifiedName BI_CONSUMER =
      QualifiedName.of(FUNCTION_PACKAGE, "BiConsumer");
  public static final QualifiedName UNARY_OPERATOR =
      QualifiedName.of(FUNCTION_PACKAGE, "UnaryOperator");

  private static final ErrorTypeHandling<RuntimeException> IGNORE_ERROR_TYPES =
      new ErrorTypeHandling<RuntimeException>() {
        @Override public void handleErrorType(ErrorType type) { }
      };

  public static FunctionalType consumer(TypeMirror type) {
    checkState(!type.getKind().isPrimitive(), "Unexpected primitive type %s", type);
    return new FunctionalType(
        CONSUMER.withParameters(type),
        "accept",
        ImmutableList.of(type),
        null);
  }

  public static FunctionalType unaryOperator(TypeMirror type) {
    checkState(!type.getKind().isPrimitive(), "Unexpected primitive type %s", type);
    return new FunctionalType(
        UNARY_OPERATOR.withParameters(type),
        "apply",
        ImmutableList.of(type),
        type);
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
    TypeElement typeElement = asElement(type);
    for (ExecutableElement method : methodsOn(typeElement, elements, IGNORE_ERROR_TYPES)) {
      if (!method.getSimpleName().contentEquals(methodName)
          || method.getParameters().size() != 1) {
        continue;
      }
      ExecutableType methodType = (ExecutableType) types.asMemberOf(type, method);
      TypeMirror parameter = getOnlyElement(methodType.getParameterTypes());
      FunctionalType functionalType = maybeFunctionalType(parameter, elements, types).orNull();
      if (functionalType == null || !isAssignable(functionalType, prototype, types)) {
        continue;
      }
      return functionalType;
    }
    return prototype;
  }

  public static Optional<FunctionalType> maybeFunctionalType(
      TypeMirror type,
      Elements elements,
      Types types) {
    DeclaredType declaredType = maybeDeclared(type).orNull();
    if (declaredType == null) {
      return Optional.absent();
    }
    return maybeFunctionalType(declaredType, elements, types);
  }

  public static Optional<FunctionalType> maybeFunctionalType(
      DeclaredType type,
      Elements elements,
      Types types) {
    TypeElement typeElement = asElement(type);
    if (!typeElement.getKind().isInterface()) {
      return Optional.absent();
    }
    Set<ExecutableElement> abstractMethods =
        only(ABSTRACT, methodsOn(typeElement, elements, IGNORE_ERROR_TYPES));
    if (abstractMethods.size() != 1) {
      return Optional.absent();
    }
    ExecutableElement method = getOnlyElement(abstractMethods);
    ExecutableType methodType = (ExecutableType) types.asMemberOf(type, method);
    return Optional.of(new FunctionalType(
        Type.from(type),
        method.getSimpleName().toString(),
        methodType.getParameterTypes(),
        methodType.getReturnType()));
  }

  @VisibleForTesting static boolean isAssignable(
      FunctionalType fromType,
      FunctionalType toType,
      Types types) {
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

  private static boolean isAssignable(
      @Nullable TypeMirror fromParam, @Nullable TypeMirror toParam, Types types) {
    if (isVoid(fromParam) || isVoid(toParam)) {
      return isVoid(fromParam) && isVoid(toParam);
    }
    return types.isAssignable(fromParam, toParam);
  }

  private static boolean isVoid(@Nullable TypeMirror type) {
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
    List<String> parametersAsStrings = new ArrayList<String>();
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
