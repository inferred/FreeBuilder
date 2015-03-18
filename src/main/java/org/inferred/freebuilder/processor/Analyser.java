/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.inferred.freebuilder.processor;

import static com.google.common.base.Functions.toStringFunction;
import static com.google.common.base.Objects.equal;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.any;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Iterables.tryFind;
import static javax.lang.model.element.ElementKind.INTERFACE;
import static javax.lang.model.util.ElementFilter.constructorsIn;
import static javax.lang.model.util.ElementFilter.typesIn;
import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.NOTE;
import static org.inferred.freebuilder.processor.BuilderFactory.NO_ARGS_CONSTRUCTOR;
import static org.inferred.freebuilder.processor.MethodFinder.methodsOn;
import static org.inferred.freebuilder.processor.util.ModelUtils.findAnnotationMirror;

import java.beans.Introspector;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor6;
import javax.lang.model.util.Types;

import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.Metadata.StandardMethod;
import org.inferred.freebuilder.processor.Metadata.UnderrideLevel;
import org.inferred.freebuilder.processor.PropertyCodeGenerator.Config;
import org.inferred.freebuilder.processor.util.ImpliedClass;
import org.inferred.freebuilder.processor.util.IsInvalidTypeVisitor;

import com.google.common.annotations.GwtCompatible;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

/**
 * Analyses a {@link org.inferred.freebuilder.FreeBuilder FreeBuilder}
 * type, returning metadata about it in a format amenable to code generation.
 *
 * <p>Any deviations from the FreeBuilder spec in the user's class will result in errors being
 * issued, but unless code generation is totally impossible, metadata will still be returned.
 * This allows the user to extend an existing type without worrying that a mistake will cause
 * compiler errors in all dependent code&mdash;which would make it very hard to find the real
 * error.
 */
class Analyser {

  /**
   * Thrown when a @FreeBuilder type cannot have a Builder type generated, for instance if
   * it is private.
   */
  public static class CannotGenerateCodeException extends Exception { }

  /**
   * Factories of {@link PropertyCodeGenerator} instances. Note: order is important; the default
   * factory should always be last.
   */
  private static final List<PropertyCodeGenerator.Factory> PROPERTY_FACTORIES = ImmutableList.of(
      new ListPropertyFactory(),
      new SetPropertyFactory(),
      new MapPropertyFactory(),
      new MultisetPropertyFactory(),
      new ListMultimapPropertyFactory(),
      new SetMultimapPropertyFactory(),
      new OptionalPropertyFactory(),
      new BuildablePropertyFactory(),
      new DefaultPropertyFactory());

  private static final String BUILDER_SIMPLE_NAME_TEMPLATE = "%s_Builder";
  private static final String USER_BUILDER_NAME = "Builder";

  private static final Pattern GETTER_PATTERN = Pattern.compile("^(get|is)(.+)");
  private static final String GET_PREFIX = "get";
  private static final String IS_PREFIX = "is";

  private final Elements elements;
  private final Messager messager;
  private final MethodIntrospector methodIntrospector;
  private final Types types;

  Analyser(
      Elements elements, Messager messager, MethodIntrospector methodIntrospector, Types types) {
    this.elements = elements;
    this.messager = messager;
    this.methodIntrospector = methodIntrospector;
    this.types = types;
  }

  /**
   * Returns a {@link Metadata} metadata object for {@code type}.
   *
   * @throws CannotGenerateCodeException if code cannot be generated, e.g. if the type is private
   */
  Metadata analyse(TypeElement type) throws CannotGenerateCodeException {
    verifyType(type);
    PackageElement pkg = elements.getPackageOf(type);
    ImmutableSet<ExecutableElement> methods = methodsOn(type, elements);
    ImpliedClass generatedBuilder =
        new ImpliedClass(pkg, generatedBuilderSimpleName(type), type, elements);
    Optional<TypeElement> builder = tryFindBuilder(generatedBuilder, type);
    return new Metadata.Builder(elements)
        .setType(type)
        .setBuilder(builder)
        .setBuilderFactory(builderFactory(builder))
        .setGeneratedBuilder(generatedBuilder)
        .setValueType(generatedBuilder.createNestedClass("Value"))
        .setPartialType(generatedBuilder.createNestedClass("Partial"))
        .setPropertyEnum(generatedBuilder.createNestedClass("Property"))
        .putAllStandardMethodUnderrides(findUnderriddenMethods(methods))
        .setBuilderSerializable(shouldBuilderBeSerializable(builder))
        .setGwtCompatible(isGwtCompatible(type))
        .setGwtSerializable(isGwtSerializable(type))
        .addAllProperties(findProperties(type, methods, builder).values())
        .build();
  }

  /** Basic sanity-checking to ensure we can fulfil the &#64;FreeBuilder contract for this type. */
  private void verifyType(TypeElement type) throws CannotGenerateCodeException {
    switch (type.getNestingKind()) {
      case TOP_LEVEL:
        break;

      case MEMBER:
        if (!type.getModifiers().contains(Modifier.STATIC)) {
          messager.printMessage(
              ERROR,
              "Inner classes cannot be @FreeBuilder types (did you forget the static keyword?)",
              type);
          throw new CannotGenerateCodeException();
        }

        if (type.getModifiers().contains(Modifier.PRIVATE)) {
          messager.printMessage(ERROR, "@FreeBuilder types cannot be private", type);
          throw new CannotGenerateCodeException();
        }

        for (Element e = type.getEnclosingElement(); e != null; e = e.getEnclosingElement()) {
          if (e.getModifiers().contains(Modifier.PRIVATE)) {
            messager.printMessage(
                ERROR,
                "@FreeBuilder types cannot be private, but enclosing type "
                    + e.getSimpleName() + " is inaccessible",
                type);
            throw new CannotGenerateCodeException();
          }
        }
        break;

      default:
        messager.printMessage(
            ERROR, "Only top-level or static nested types can be @FreeBuilder types", type);
        throw new CannotGenerateCodeException();
    }
    if (!type.getTypeParameters().isEmpty()) {
      messager.printMessage(
          ERROR, "Generic @FreeBuilder types not yet supported (b/17278322)", type);
      throw new CannotGenerateCodeException();
    }
    switch (type.getKind()) {
      case ANNOTATION_TYPE:
        messager.printMessage(ERROR, "@FreeBuilder does not support annotation types", type);
        throw new CannotGenerateCodeException();

      case CLASS:
        verifyTypeIsConstructible(type);
        break;

      case ENUM:
        messager.printMessage(ERROR, "@FreeBuilder does not support enum types", type);
        throw new CannotGenerateCodeException();

      case INTERFACE:
        // Nothing extra needs to be checked on an interface
        break;

      default:
        throw new AssertionError("Unexpected element kind " + type.getKind());
    }
  }

  /** Issues an error if {@code type} does not have a package-visible no-args constructor. */
  private void verifyTypeIsConstructible(TypeElement type)
      throws CannotGenerateCodeException {
    List<ExecutableElement> constructors = constructorsIn(type.getEnclosedElements());
    if (constructors.isEmpty()) {
      return;
    }
    for (ExecutableElement constructor : constructors) {
      if (constructor.getParameters().isEmpty()) {
        if (constructor.getModifiers().contains(Modifier.PRIVATE)) {
          messager.printMessage(
              ERROR,
              "@FreeBuilder types must have a package-visible no-args constructor",
              constructor);
          throw new CannotGenerateCodeException();
        }
        return;
      }
    }
    messager.printMessage(
        ERROR, "@FreeBuilder types must have a package-visible no-args constructor", type);
    throw new CannotGenerateCodeException();
  }

  /** Find any standard methods the user has 'underridden' in their type. */
  private Map<StandardMethod, UnderrideLevel> findUnderriddenMethods(
      Iterable<ExecutableElement> methods) {
    Map<StandardMethod, ExecutableElement> standardMethods =
        new LinkedHashMap<StandardMethod, ExecutableElement>();
    for (ExecutableElement method : methods) {
      Optional<StandardMethod> standardMethod = maybeStandardMethod(method);
      if (standardMethod.isPresent() && isUnderride(method)) {
        standardMethods.put(standardMethod.get(), method);
      }
    }
    if (standardMethods.containsKey(StandardMethod.EQUALS)
        != standardMethods.containsKey(StandardMethod.HASH_CODE)) {
      ExecutableElement underriddenMethod = standardMethods.containsKey(StandardMethod.EQUALS)
          ? standardMethods.get(StandardMethod.EQUALS)
          : standardMethods.get(StandardMethod.HASH_CODE);
      messager.printMessage(ERROR,
          "hashCode and equals must be implemented together on @FreeBuilder types",
          underriddenMethod);
    }
    ImmutableMap.Builder<StandardMethod, UnderrideLevel> result = ImmutableMap.builder();
    for (StandardMethod standardMethod : standardMethods.keySet()) {
      if (standardMethods.get(standardMethod).getModifiers().contains(Modifier.FINAL)) {
        result.put(standardMethod, UnderrideLevel.FINAL);
      } else {
        result.put(standardMethod, UnderrideLevel.OVERRIDEABLE);
      }
    }
    return result.build();
  }

  private static boolean isUnderride(ExecutableElement method) {
    return !method.getModifiers().contains(Modifier.ABSTRACT);
  }

  /**
   * Looks for a type called Builder, and verifies it extends the autogenerated superclass. Issues
   * an error if the wrong type is being subclassed&mdash;a typical copy-and-paste error when
   * renaming an existing &#64;FreeBuilder type, or using one as a template.
   */
  private Optional<TypeElement> tryFindBuilder(final ImpliedClass superclass, TypeElement type) {
    Optional<TypeElement> userClass =
        tryFind(typesIn(type.getEnclosedElements()), new Predicate<Element>() {
          @Override public boolean apply(Element input) {
            return input.getSimpleName().contentEquals(USER_BUILDER_NAME);
          }
        });
    if (!userClass.isPresent()) {
      if (type.getKind() == INTERFACE) {
        messager.printMessage(
            NOTE,
            "Add \"class Builder extends "
                + superclass.getSimpleName()
                + " {}\" to your interface to enable the @FreeBuilder API",
            type);
      } else {
        messager.printMessage(
            NOTE,
            "Add \"public static class Builder extends "
                + superclass.getSimpleName()
                + " {}\" to your class to enable the @FreeBuilder API",
            type);
      }
      return Optional.absent();
    }

    boolean extendsSuperclass =
        new IsSubclassOfGeneratedTypeVisitor(superclass).visit(userClass.get().getSuperclass());
    if (!extendsSuperclass) {
      messager.printMessage(
          ERROR,
          "Builder extends the wrong type (should be "
              + superclass.getSimpleName()
              + ")",
          userClass.get());
      return Optional.absent();
    }

    return userClass;
  }

  private Optional<BuilderFactory> builderFactory(Optional<TypeElement> builder) {
    if (!builder.isPresent()) {
      return Optional.of(NO_ARGS_CONSTRUCTOR);
    }
    if (!builder.get().getModifiers().contains(Modifier.STATIC)) {
      messager.printMessage(ERROR, "Builder must be static on @FreeBuilder types", builder.get());
      return Optional.absent();
    }
    return BuilderFactory.from(builder.get());
  }

  private Map<String, Property> findProperties(
      TypeElement type, Iterable<ExecutableElement> methods, Optional<TypeElement> builder) {
    Set<String> methodsInvokedInBuilderConstructor = getMethodsInvokedInBuilderConstructor(builder);
    Map<String, Property> propertiesByName = new LinkedHashMap<String, Property>();
    for (ExecutableElement method : methods) {
      Property property = asPropertyOrNull(type, method, methodsInvokedInBuilderConstructor);
      if (property != null) {
        propertiesByName.put(property.getName(), property);
      }
    }
    return propertiesByName;
  }

  private Set<String> getMethodsInvokedInBuilderConstructor(Optional<TypeElement> builder) {
    if (!builder.isPresent()) {
      return ImmutableSet.of();
    }
    List<ExecutableElement> constructors = constructorsIn(builder.get().getEnclosedElements());
    if (constructors.isEmpty()) {
      return ImmutableSet.of();
    }
    Set<Name> result = null;
    for (ExecutableElement constructor : constructors) {
      if (result == null) {
        result = methodIntrospector.getOwnMethodInvocations(constructor);
      } else {
        result = Sets.intersection(result, methodIntrospector.getOwnMethodInvocations(constructor));
      }
    }
    return ImmutableSet.copyOf(transform(result, toStringFunction()));
  }

  /**
   * Introspects {@code method}, as found on {@code valueType}.
   *
   * @return a {@link Property} metadata object, or null if the method is not a valid getter
   */
  private Property asPropertyOrNull(
      TypeElement valueType,
      ExecutableElement method,
      Set<String> methodsInvokedInBuilderConstructor) {
    MatchResult getterNameMatchResult = getterNameMatchResult(valueType, method);
    if (getterNameMatchResult == null) {
      return null;
    }
    verifyNotNullable(valueType, method);
    String getterName = getterNameMatchResult.group(0);

    TypeMirror propertyType = getReturnType(valueType, method);
    String camelCaseName = Introspector.decapitalize(getterNameMatchResult.group(2));
    Property.Builder resultBuilder = new Property.Builder()
            .setType(propertyType)
            .setName(camelCaseName)
            .setCapitalizedName(getterNameMatchResult.group(2))
            .setAllCapsName(camelCaseToAllCaps(camelCaseName))
            .setGetterName(getterName)
            .setFullyCheckedCast(CAST_IS_FULLY_CHECKED.visit(propertyType));
    if (propertyType.getKind().isPrimitive()) {
      PrimitiveType unboxedType = types.getPrimitiveType(propertyType.getKind());
      TypeMirror boxedType = types.erasure(types.boxedClass(unboxedType).asType());
      resultBuilder.setBoxedType(boxedType);
    }
    Property propertyWithoutCodeGenerator = resultBuilder.build();
    resultBuilder.setCodeGenerator(createCodeGenerator(
        propertyWithoutCodeGenerator, methodsInvokedInBuilderConstructor));
    return resultBuilder.build();
  }

  /**
   * Determines the return type of {@code method}, if called on an instance of type {@code type}.
   *
   * <p>For instance, in this example, myY.getProperty() returns List&lt;T&gt;, not T:<pre><code>
   *    interface X&lt;T&gt; {
   *      T getProperty();
   *    }
   *    &#64;FreeBuilder interface Y&lt;T&gt; extends X&lt;List&lt;T&gt;&gt; { }</pre></code>
   *
   * <p>(Unfortunately, a bug in Eclipse prevents us handling these cases correctly at the moment.
   * javac works fine.)
   */
  private TypeMirror getReturnType(TypeElement type, ExecutableElement method) {
    try {
      ExecutableType executableType = (ExecutableType)
          types.asMemberOf((DeclaredType) type.asType(), method);
      return executableType.getReturnType();
    } catch (IllegalArgumentException e) {
      // Eclipse incorrectly throws an IllegalArgumentException here:
      //    "element is not valid for the containing declared type"
      // As a workaround for the common case, fall back to the declared return type.
      return method.getReturnType();
    }
  }

  private void verifyNotNullable(TypeElement valueType, ExecutableElement getterMethod) {
    Optional<AnnotationMirror> nullableAnnotation =
        findAnnotationMirror(getterMethod, "javax.annotation.Nullable");
    if (nullableAnnotation.isPresent()) {
      if (getterMethod.getEnclosingElement().equals(valueType)) {
        messager.printMessage(
            ERROR,
            "Nullable properties not supported on @FreeBuilder types (b/16057590)",
            getterMethod,
            nullableAnnotation.get());
      } else {
        messager.printMessage(
            ERROR,
            "Method '" + getterMethod + "' declared @Nullable, but Nullable properties are not "
                + "supported on @FreeBuilder types (b/16057590)",
            valueType);
      }
    }
  }

  private PropertyCodeGenerator createCodeGenerator(
      Property propertyWithoutCodeGenerator,
      Set<String> methodsInvokedInBuilderConstructor) {
    Config config = new ConfigImpl(
        propertyWithoutCodeGenerator, methodsInvokedInBuilderConstructor);
    for (PropertyCodeGenerator.Factory factory : PROPERTY_FACTORIES) {
      Optional<? extends PropertyCodeGenerator> codeGenerator = factory.create(config);
      if (codeGenerator.isPresent()) {
        return codeGenerator.get();
      }
    }
    throw new AssertionError("DefaultPropertyFactory not registered");
  }

  private class ConfigImpl implements Config {

    final Property property;
    final Set<String> methodsInvokedInBuilderConstructor;

    ConfigImpl(
        Property property,
        Set<String> methodsInvokedInBuilderConstructor) {
      this.property = property;
      this.methodsInvokedInBuilderConstructor = methodsInvokedInBuilderConstructor;
    }

    @Override
    public Property getProperty() {
      return property;
    }

    @Override
    public Set<String> getMethodsInvokedInBuilderConstructor() {
      return methodsInvokedInBuilderConstructor;
    }

    @Override
    public Elements getElements() {
      return elements;
    }

    @Override
    public Types getTypes() {
      return types;
    }
  }

  /**
   * Visitor that returns true if a cast to the visited type is guaranteed to be fully checked at
   * runtime. This is true for any type that is non-generic, raw, or parameterized with unbounded
   * wildcards, such as {@code Integer}, {@code List} or {@code Map<?, ?>}.
   */
  private static final SimpleTypeVisitor6<Boolean, ?> CAST_IS_FULLY_CHECKED =
      new SimpleTypeVisitor6<Boolean, Void>() {
        @Override
        public Boolean visitArray(ArrayType t, Void p) {
          return visit(t.getComponentType());
        }

        @Override
        public Boolean visitDeclared(DeclaredType t, Void p) {
          for (TypeMirror argument : t.getTypeArguments()) {
            if (!IS_UNBOUNDED_WILDCARD.visit(argument)) {
              return false;
            }
          }
          return true;
        }

        @Override protected Boolean defaultAction(TypeMirror e, Void p) {
          return true;
        }
      };

  /**
   * Visitor that returns true if the visited type is an unbounded wildcard, i.e. {@code <?>}.
   */
  private static final SimpleTypeVisitor6<Boolean, ?> IS_UNBOUNDED_WILDCARD =
      new SimpleTypeVisitor6<Boolean, Void>() {
        @Override public Boolean visitWildcard(WildcardType t, Void p) {
          return t.getExtendsBound() == null
              || t.getExtendsBound().toString().equals("java.lang.Object");
        }

        @Override protected Boolean defaultAction(TypeMirror e, Void p) {
          return false;
        }
      };

  /**
   * Verifies {@code method} is an abstract getter following the JavaBean convention. Any
   * deviations will be logged as an error.
   *
   * <p>We deviate slightly from the JavaBean convention by insisting that there must be a
   * non-lowercase character immediately following the get/is prefix; this prevents ugly cases like
   * 'get()' or 'getter()'.
   *
   * @return a {@link Matcher} with the getter prefix in group 1 and the property name suffix
   *     in group 2, or {@code null} if {@code method} is not a valid abstract getter method
   */
  private MatchResult getterNameMatchResult(TypeElement valueType, ExecutableElement method) {
    if (maybeStandardMethod(method).isPresent()) {
      return null;
    }
    Set<Modifier> modifiers = method.getModifiers();
    if (!modifiers.contains(Modifier.ABSTRACT)) {
      return null;
    }
    boolean declaredOnValueType = method.getEnclosingElement().equals(valueType);
    String name = method.getSimpleName().toString();
    Matcher getterMatcher = GETTER_PATTERN.matcher(name);
    if (!getterMatcher.matches()) {
      if (declaredOnValueType) {
        messager.printMessage(
            ERROR,
            "Only getter methods (starting with '" + GET_PREFIX
                + "' or '" + IS_PREFIX + "') may be declared abstract on @FreeBuilder types",
            method);
      } else {
        printNoImplementationMessage(valueType, method);
      }
      return null;
    }
    String prefix = getterMatcher.group(1);
    String suffix = getterMatcher.group(2);
    if (hasUpperCase(suffix.codePointAt(0))) {
      if (declaredOnValueType) {
        String message = new StringBuilder()
            .append("Getter methods cannot have a lowercase character immediately after the '")
            .append(prefix)
            .append("' prefix on @FreeBuilder types (did you mean '")
            .append(prefix)
            .appendCodePoint(Character.toUpperCase(suffix.codePointAt(0)))
            .append(suffix.substring(suffix.offsetByCodePoints(0, 1)))
            .append("'?)")
            .toString();
        messager.printMessage(ERROR, message, method);
      } else {
        printNoImplementationMessage(valueType, method);
      }
      return null;
    }
    TypeMirror returnType = getReturnType(valueType, method);
    if (returnType.getKind() == TypeKind.VOID) {
      if (declaredOnValueType) {
        messager.printMessage(
            ERROR, "Getter methods must not be void on @FreeBuilder types", method);
      } else {
        printNoImplementationMessage(valueType, method);
      }
      return null;
    }
    if (prefix.equals(IS_PREFIX) && (returnType.getKind() != TypeKind.BOOLEAN)) {
      if (declaredOnValueType) {
        messager.printMessage(
            ERROR,
            "Getter methods starting with '" + IS_PREFIX
                + "' must return a boolean on @FreeBuilder types",
            method);
      } else {
        printNoImplementationMessage(valueType, method);
      }
      return null;
    }
    if (!method.getParameters().isEmpty()) {
      if (declaredOnValueType) {
        messager.printMessage(
            ERROR, "Getter methods cannot take parameters on @FreeBuilder types", method);
      } else {
        printNoImplementationMessage(valueType, method);
      }
      return null;
    }
    if (new IsInvalidTypeVisitor().visit(returnType)) {
      // The compiler should already have issued an error.
      return null;
    }
    return getterMatcher.toMatchResult();
  }

  private void printNoImplementationMessage(TypeElement valueType, ExecutableElement method) {
    messager.printMessage(
        ERROR,
        "No implementation found for non-getter method '" + method + "'; "
            + "cannot generate @FreeBuilder implementation",
        valueType);
  }

  /**
   * Returns the simple name of the builder class that should be generated for the given type.
   *
   * <p>This is simply the {@link #BUILDER_SIMPLE_NAME_TEMPLATE} with the original type name
   * substituted in. (If the original type is nested, its enclosing classes will be included,
   * separated with underscores, to ensure uniqueness.)
   */
  private Name generatedBuilderSimpleName(TypeElement type) {
    String packageName = elements.getPackageOf(type).getQualifiedName().toString();
    String originalName = type.getQualifiedName().toString();
    checkState(originalName.startsWith(packageName + "."));
    String nameWithoutPackage = originalName.substring(packageName.length() + 1);
    return elements.getName(String.format(
        BUILDER_SIMPLE_NAME_TEMPLATE, nameWithoutPackage.replaceAll("\\.", "_")));
  }

  private boolean shouldBuilderBeSerializable(Optional<TypeElement> builder) {
    if (!builder.isPresent()) {
      // If there's no user-provided subclass, make the builder serializable.
      return true;
    }
    // If there is a user-provided subclass, only make its generated superclass serializable if
    // it is itself; otherwise, tools may complain about missing a serialVersionUID field.
    return any(builder.get().getInterfaces(), isEqualTo(Serializable.class));
  }

  private static final boolean hasUpperCase(int codepoint) {
    return Character.toUpperCase(codepoint) != codepoint;
  }

  private static boolean isGwtCompatible(TypeElement type) {
    GwtCompatible gwtCompatible = type.getAnnotation(GwtCompatible.class);
    return (gwtCompatible != null);
  }

  private static boolean isGwtSerializable(TypeElement type) {
    GwtCompatible gwtCompatible = type.getAnnotation(GwtCompatible.class);
    return ((gwtCompatible != null) && (gwtCompatible.serializable()));
  }

  /** Returns whether a method is one of the {@link StandardMethod}s, and if so, which. */
  private static Optional<StandardMethod> maybeStandardMethod(ExecutableElement method) {
    String methodName = method.getSimpleName().toString();
    if (methodName.equals("equals")) {
      if (method.getParameters().size() == 1
          && method.getParameters().get(0).asType().toString().equals("java.lang.Object")) {
        return Optional.of(StandardMethod.EQUALS);
      } else {
        return Optional.absent();
      }
    } else if (methodName.equals("hashCode")) {
      if (method.getParameters().isEmpty()) {
        return Optional.of(StandardMethod.HASH_CODE);
      } else {
        return Optional.absent();
      }
    } else if (methodName.equals("toString")) {
      if (method.getParameters().isEmpty()) {
        return Optional.of(StandardMethod.TO_STRING);
      } else {
        return Optional.absent();
      }
    } else {
      return Optional.absent();
    }
  }

  /**
   * Visitor that returns true if the visited type extends a generated {@code superclass} in the
   * same package.
   */
  private static final class IsSubclassOfGeneratedTypeVisitor extends
      SimpleTypeVisitor6<Boolean, Void> {
    private final ImpliedClass superclass;

    private IsSubclassOfGeneratedTypeVisitor(ImpliedClass superclass) {
      super(false);
      this.superclass = superclass;
    }

    /**
     * Any reference to the as-yet-ungenerated builder should be an unresolved ERROR.
     * Similarly for many copy-and-paste errors
     */
    @Override
    public Boolean visitError(ErrorType t, Void p) {
      String simpleName = t.toString();
      return equal(simpleName, superclass.getSimpleName().toString());
    }

    /**
     * However, with some setups (e.g. Eclipse+blaze), the builder may have already been
     * generated and provided via a jar, in which case the reference will be DECLARED and
     * qualified. We still want to generate it.
     */
    @Override
    public Boolean visitDeclared(DeclaredType t, Void p) {
      String qualifiedName = t.toString();
      return equal(qualifiedName, superclass.getQualifiedName().toString());
    }
  }

  /** Converts camelCaseConvention to ALL_CAPS_CONVENTION. */
  private static String camelCaseToAllCaps(String camelCase) {
    // The first half of the pattern spots lowercase to uppercase boundaries.
    // The second half spots the end of uppercase sequences, like "URL" in "myURLShortener".
    return camelCase.replaceAll("(?<=[^A-Z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][^A-Z])", "_")
        .toUpperCase();
  }

  private Predicate<TypeMirror> isEqualTo(Class<?> cls) {
    final TypeMirror typeMirror = elements.getTypeElement(cls.getCanonicalName()).asType();
    return new Predicate<TypeMirror>() {
      @Override public boolean apply(TypeMirror input) {
        return types.isSameType(input, typeMirror);
      }
    };
  }
}
