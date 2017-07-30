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
import static com.google.common.collect.Maps.newLinkedHashMap;
import static javax.lang.model.element.ElementKind.INTERFACE;
import static javax.lang.model.util.ElementFilter.constructorsIn;
import static javax.lang.model.util.ElementFilter.typesIn;
import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.NOTE;
import static org.inferred.freebuilder.processor.BuilderFactory.NO_ARGS_CONSTRUCTOR;
import static org.inferred.freebuilder.processor.GwtSupport.gwtMetadata;
import static org.inferred.freebuilder.processor.MethodFinder.methodsOn;
import static org.inferred.freebuilder.processor.naming.NamingConventions.determineNamingConvention;
import static org.inferred.freebuilder.processor.util.ModelUtils.asElement;
import static org.inferred.freebuilder.processor.util.ModelUtils.getReturnType;
import static org.inferred.freebuilder.processor.util.ModelUtils.maybeAsTypeElement;
import static org.inferred.freebuilder.processor.util.ModelUtils.maybeType;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.Metadata.StandardMethod;
import org.inferred.freebuilder.processor.Metadata.UnderrideLevel;
import org.inferred.freebuilder.processor.PropertyCodeGenerator.Config;
import org.inferred.freebuilder.processor.naming.NamingConvention;
import org.inferred.freebuilder.processor.util.ModelUtils;
import org.inferred.freebuilder.processor.util.ParameterizedType;
import org.inferred.freebuilder.processor.util.QualifiedName;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor6;
import javax.lang.model.util.Types;

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
      new NullableProperty.Factory(), // Must be first, as no other factory supports nulls
      new ListProperty.Factory(),
      new SetPropertyFactory(),
      new SortedSetPropertyFactory(),
      new MapProperty.Factory(),
      new MultisetProperty.Factory(),
      new ListMultimapProperty.Factory(),
      new SetMultimapProperty.Factory(),
      new OptionalProperty.Factory(),
      new BuildableProperty.Factory(),
      new DefaultProperty.Factory()); // Must be last, as it will always return a CodeGenerator

  private static final String BUILDER_SIMPLE_NAME_TEMPLATE = "%s_Builder";
  private static final String USER_BUILDER_NAME = "Builder";

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
    PackageElement pkg = elements.getPackageOf(type);
    verifyType(type, pkg);
    ImmutableSet<ExecutableElement> methods = methodsOn(type, elements);
    QualifiedName generatedBuilder = QualifiedName.of(
        pkg.getQualifiedName().toString(), generatedBuilderSimpleName(type));
    Optional<TypeElement> builder = tryFindBuilder(generatedBuilder, type);
    Optional<BuilderFactory> builderFactory = builderFactory(builder);
    QualifiedName valueType = generatedBuilder.nestedType("Value");
    QualifiedName partialType = generatedBuilder.nestedType("Partial");
    QualifiedName propertyType = generatedBuilder.nestedType("Property");
    List<? extends TypeParameterElement> typeParameters = type.getTypeParameters();
    Map<ExecutableElement, Property> properties =
        findProperties(type, removeNonGetterMethods(builder, methods));
    Metadata.Builder metadataBuilder = new Metadata.Builder()
        .setType(QualifiedName.of(type).withParameters(typeParameters))
        .setInterfaceType(type.getKind().isInterface())
        .setBuilder(parameterized(builder, typeParameters))
        .setBuilderFactory(builderFactory)
        .setGeneratedBuilder(generatedBuilder.withParameters(typeParameters))
        .setValueType(valueType.withParameters(typeParameters))
        .setPartialType(partialType.withParameters(typeParameters))
        .setPropertyEnum(propertyType.withParameters())
        .addVisibleNestedTypes(valueType)
        .addVisibleNestedTypes(partialType)
        .addVisibleNestedTypes(propertyType)
        .addAllVisibleNestedTypes(visibleTypesIn(type))  // Because we inherit from type
        .putAllStandardMethodUnderrides(findUnderriddenMethods(methods))
        .setHasToBuilderMethod(hasToBuilderMethod(builder, builderFactory, methods))
        .setBuilderSerializable(shouldBuilderBeSerializable(builder))
        .addAllProperties(properties.values());
    Metadata baseMetadata = metadataBuilder.build();
    metadataBuilder.mergeFrom(gwtMetadata(type, baseMetadata));
    if (builder.isPresent()) {
      metadataBuilder
          .clearProperties()
          .addAllProperties(codeGenerators(properties, baseMetadata, builder.get()));
    }
    return metadataBuilder.build();
  }

  private static Set<QualifiedName> visibleTypesIn(TypeElement type) {
    ImmutableSet.Builder<QualifiedName> visibleTypes = ImmutableSet.builder();
    for (TypeElement nestedType : typesIn(type.getEnclosedElements())) {
      visibleTypes.add(QualifiedName.of(nestedType));
    }
    visibleTypes.addAll(visibleTypesIn(maybeType(type.getEnclosingElement())));
    visibleTypes.addAll(visibleTypesIn(maybeAsTypeElement(type.getSuperclass())));
    return visibleTypes.build();
  }

  private static Set<QualifiedName> visibleTypesIn(Optional<TypeElement> type) {
    if (!type.isPresent()) {
      return ImmutableSet.of();
    } else {
      return visibleTypesIn(type.get());
    }
  }

  /** Basic sanity-checking to ensure we can fulfil the &#64;FreeBuilder contract for this type. */
  private void verifyType(TypeElement type, PackageElement pkg) throws CannotGenerateCodeException {
    if (pkg.isUnnamed()) {
      messager.printMessage(ERROR, "@FreeBuilder does not support types in unnamed packages", type);
      throw new CannotGenerateCodeException();
    }
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

  /** Find a toBuilder method, if the user has provided one.
   * @param builderFactory */
  private boolean hasToBuilderMethod(
      Optional<TypeElement> builder,
      Optional<BuilderFactory> builderFactory,
      Iterable<ExecutableElement> methods) {
    if (!builder.isPresent()) {
      return false;
    }
    for (ExecutableElement method : methods) {
      if (isToBuilderMethod(builder.get(), method)) {
        if (!builderFactory.isPresent()) {
          messager.printMessage(ERROR,
              "No accessible no-args Builder constructor available to implement toBuilder",
              method);
        }
        return true;
      }
    }
    return false;
  }

  private static boolean isToBuilderMethod(TypeElement builder, ExecutableElement method) {
      if (method.getSimpleName().contentEquals("toBuilder")
          && method.getModifiers().contains(Modifier.ABSTRACT)
          && method.getParameters().isEmpty()) {
        Optional<TypeElement> returnType = ModelUtils.maybeAsTypeElement(method.getReturnType());
        if (returnType.isPresent() && returnType.get().equals(builder)) {
          return true;
        }
      }
      return false;
  }

  private static Set<ExecutableElement> removeNonGetterMethods(
      Optional<TypeElement> builder, Iterable<ExecutableElement> methods) {
    ImmutableSet.Builder<ExecutableElement> nonUnderriddenMethods = ImmutableSet.builder();
    for (ExecutableElement method : methods) {
      boolean isAbstract = method.getModifiers().contains(Modifier.ABSTRACT);
      boolean isStandardMethod = maybeStandardMethod(method).isPresent();
      boolean isToBuilderMethod = builder.isPresent() && isToBuilderMethod(builder.get(), method);
      if (isAbstract && !isStandardMethod && !isToBuilderMethod) {
        nonUnderriddenMethods.add(method);
      }
    }
    return nonUnderriddenMethods.build();
  }

  private static boolean isUnderride(ExecutableElement method) {
    return !method.getModifiers().contains(Modifier.ABSTRACT);
  }

  /**
   * Looks for a type called Builder, and verifies it extends the autogenerated superclass. Issues
   * an error if the wrong type is being subclassed&mdash;a typical copy-and-paste error when
   * renaming an existing &#64;FreeBuilder type, or using one as a template.
   */
  private Optional<TypeElement> tryFindBuilder(
      final QualifiedName generatedBuilder, TypeElement type) {
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
                + generatedBuilder.getSimpleName()
                + " {}\" to your interface to enable the @FreeBuilder API",
            type);
      } else {
        messager.printMessage(
            NOTE,
            "Add \"public static class Builder extends "
                + generatedBuilder.getSimpleName()
                + " {}\" to your class to enable the @FreeBuilder API",
            type);
      }
      return Optional.absent();
    }

    boolean extendsSuperclass =
        new IsSubclassOfGeneratedTypeVisitor(generatedBuilder, type.getTypeParameters())
            .visit(userClass.get().getSuperclass());
    if (!extendsSuperclass) {
      messager.printMessage(
          ERROR,
          "Builder extends the wrong type (should be " + generatedBuilder.getSimpleName() + ")",
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

  private Map<ExecutableElement, Property> findProperties(
      TypeElement type, Iterable<ExecutableElement> methods) {
    NamingConvention namingConvention = determineNamingConvention(type, methods, messager, types);
    Map<ExecutableElement, Property> propertiesByMethod = newLinkedHashMap();
    Optional<JacksonSupport> jacksonSupport = JacksonSupport.create(type);
    for (ExecutableElement method : methods) {
      Property.Builder propertyBuilder = namingConvention.getPropertyNames(type, method).orNull();
      if (propertyBuilder != null) {
        addPropertyData(propertyBuilder, type, method, jacksonSupport);
        propertiesByMethod.put(method, propertyBuilder.build());
      }
    }
    return propertiesByMethod;
  }

  private List<Property> codeGenerators(
      Map<ExecutableElement, Property> properties,
      Metadata metadata,
      TypeElement builder) {
    ImmutableList.Builder<Property> codeGenerators = ImmutableList.builder();
    Set<String> methodsInvokedInBuilderConstructor = getMethodsInvokedInBuilderConstructor(builder);
    for (Map.Entry<ExecutableElement, Property> entry : properties.entrySet()) {
      Config config = new ConfigImpl(
          builder,
          metadata,
          entry.getValue(),
          entry.getKey(),
          methodsInvokedInBuilderConstructor);
      codeGenerators.add(new Property.Builder()
          .mergeFrom(entry.getValue())
          .setCodeGenerator(createCodeGenerator(config))
          .build());
    }
    return codeGenerators.build();
  }

  private Set<String> getMethodsInvokedInBuilderConstructor(TypeElement builder) {
    List<ExecutableElement> constructors = constructorsIn(builder.getEnclosedElements());
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
   */
  private void addPropertyData(
      Property.Builder propertyBuilder,
      TypeElement valueType,
      ExecutableElement method,
      Optional<JacksonSupport> jacksonSupport) {
    TypeMirror propertyType = getReturnType(valueType, method, types);
    propertyBuilder
        .setAllCapsName(camelCaseToAllCaps(propertyBuilder.getName()))
        .setType(propertyType)
        .setFullyCheckedCast(CAST_IS_FULLY_CHECKED.visit(propertyType));
    if (jacksonSupport.isPresent()) {
      jacksonSupport.get().addJacksonAnnotations(propertyBuilder, method);
    }
    if (propertyType.getKind().isPrimitive()) {
      PrimitiveType unboxedType = types.getPrimitiveType(propertyType.getKind());
      TypeMirror boxedType = types.erasure(types.boxedClass(unboxedType).asType());
      propertyBuilder.setBoxedType(boxedType);
    }
  }

  private static PropertyCodeGenerator createCodeGenerator(Config config) {
    for (PropertyCodeGenerator.Factory factory : PROPERTY_FACTORIES) {
      Optional<? extends PropertyCodeGenerator> codeGenerator = factory.create(config);
      if (codeGenerator.isPresent()) {
        return codeGenerator.get();
      }
    }
    throw new AssertionError("DefaultPropertyFactory not registered");
  }

  private class ConfigImpl implements Config {

    private final TypeElement builder;
    private final Metadata metadata;
    private final Property property;
    private final ExecutableElement getterMethod;
    private final Set<String> methodsInvokedInBuilderConstructor;

    ConfigImpl(
        TypeElement builder,
        Metadata metadata,
        Property property,
        ExecutableElement getterMethod,
        Set<String> methodsInvokedInBuilderConstructor) {
      this.builder = builder;
      this.metadata = metadata;
      this.property = property;
      this.getterMethod = getterMethod;
      this.methodsInvokedInBuilderConstructor = methodsInvokedInBuilderConstructor;
    }

    @Override
    public TypeElement getBuilder() {
      return builder;
    }

    @Override
    public Metadata getMetadata() {
      return metadata;
    }

    @Override
    public Property getProperty() {
      return property;
    }

    @Override
    public List<? extends AnnotationMirror> getAnnotations() {
      return getterMethod.getAnnotationMirrors();
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
   * Returns the simple name of the builder class that should be generated for the given type.
   *
   * <p>This is simply the {@link #BUILDER_SIMPLE_NAME_TEMPLATE} with the original type name
   * substituted in. (If the original type is nested, its enclosing classes will be included,
   * separated with underscores, to ensure uniqueness.)
   */
  private String generatedBuilderSimpleName(TypeElement type) {
    String packageName = elements.getPackageOf(type).getQualifiedName().toString();
    String originalName = type.getQualifiedName().toString();
    checkState(originalName.startsWith(packageName + "."));
    String nameWithoutPackage = originalName.substring(packageName.length() + 1);
    return String.format(BUILDER_SIMPLE_NAME_TEMPLATE, nameWithoutPackage.replaceAll("\\.", "_"));
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
    private final QualifiedName superclass;
    private final List<? extends TypeParameterElement> typeParameters;

    private IsSubclassOfGeneratedTypeVisitor(
        QualifiedName superclass, List<? extends TypeParameterElement> typeParameters) {
      super(false);
      this.superclass = superclass;
      this.typeParameters = typeParameters;
    }

    /**
     * Any reference to the as-yet-ungenerated builder should be an unresolved ERROR.
     * Similarly for many copy-and-paste errors
     */
    @Override
    public Boolean visitError(ErrorType t, Void p) {
      if (typeParameters.isEmpty()) {
        // For non-generic types, the ErrorType will have the correct name.
        String simpleName = t.toString();
        return equal(simpleName, superclass.getSimpleName());
      }
      // For generic types, we'll just have to hope for the best.
      // TODO: Revalidate in a subsequent round?
      return true;
    }

    /**
     * However, with some setups (e.g. Eclipse+blaze), the builder may have already been
     * generated and provided via a jar, in which case the reference will be DECLARED and
     * qualified. We still want to generate it.
     */
    @Override
    public Boolean visitDeclared(DeclaredType t, Void p) {
      return asElement(t).getQualifiedName().contentEquals(superclass.toString());
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

  private static Optional<ParameterizedType> parameterized(
      Optional<TypeElement> type, List<? extends TypeParameterElement> typeParameters) {
    if (!type.isPresent()) {
      return Optional.absent();
    }
    return Optional.of(QualifiedName.of(type.get()).withParameters(typeParameters));
  }
}
