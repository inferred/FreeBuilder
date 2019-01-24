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

import static org.inferred.freebuilder.processor.model.ModelUtils.asElement;
import static org.inferred.freebuilder.processor.model.ModelUtils.findAnnotationMirror;
import static org.inferred.freebuilder.processor.model.ModelUtils.needsSafeVarargs;

import static java.util.stream.Collectors.toList;

import static javax.lang.model.element.Modifier.PUBLIC;

import org.inferred.freebuilder.FreeBuilder;
import org.inferred.freebuilder.processor.source.Excerpt;
import org.inferred.freebuilder.processor.source.Excerpts;
import org.inferred.freebuilder.processor.source.Type;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Metadata about a buildable user type.
 *
 * <p>A <b>buildable</b> type is one with a Builder class providing a similar
 * API to proto or &#64;FreeBuilder:<ul>
 * <li> a public constructor, or static builder()/newBuilder() method;
 * <li> build(), buildPartial() and clear() methods; and
 * <li> a mergeWith(Value) method.
 * </ul>
 */
public abstract class BuildableType {

  /** How to merge the values from one Builder into another. */
  public enum MergeBuilderMethod {
    MERGE_DIRECTLY, BUILD_PARTIAL_AND_MERGE
  }

  /** How to convert a partial value into a Builder. */
  public enum PartialToBuilderMethod {
    MERGE_DIRECTLY, TO_BUILDER_AND_MERGE
  }

  /**
   * Returns the parameterized buildable type.
   *
   * <p>This may be parameterized with any compatible types, including concrete types, wildcards,
   * type variables, or generic types containing any combination of the above.
   */
  public abstract Type type();

  /** Returns the builder type that will build instances of {@link #type()}. */
  public abstract Type builderType();

  public abstract MergeBuilderMethod mergeBuilder();
  public abstract PartialToBuilderMethod partialToBuilder();
  public abstract BuilderFactory builderFactory();
  public abstract Excerpt suppressUnchecked();

  /** Returns an excerpt calling the Builder factory method. */
  public Excerpt newBuilder(BuilderFactory.TypeInference typeInference) {
    return builderFactory().newBuilder(builderType(), typeInference);
  }

  public static class Builder extends BuildableType_Builder {}

  public static Optional<DeclaredType> maybeBuilder(
      DeclaredType type,
      Elements elements,
      Types types) {
    TypeElement element = asElement(type);

    // Find the builder
    TypeElement builder = element.getEnclosedElements()
        .stream()
        .flatMap(TYPES)
        .filter(BuildableType::isBuilderType)
        .findAny()
        .orElse(null);
    if (builder == null) {
      return Optional.empty();
    }

    // Parameterize the builder to match the element
    if (builder.getTypeParameters().size() != type.getTypeArguments().size()) {
      return Optional.empty();
    }
    DeclaredType builderMirror =
        types.getDeclaredType(builder, type.getTypeArguments().toArray(new TypeMirror[0]));

    // Verify the builder can be constructed
    BuilderFactory builderFactory = BuilderFactory.from(builder).orElse(null);
    if (builderFactory == null) {
      return Optional.empty();
    }

    /*
     * Verify essential methods are available.
     *
     * If the element is annotated @FreeBuilder, assume the necessary methods will be added. We
     * can't check directly as the builder superclass may not have been generated yet. To be
     * strictly correct, we should delay a round if an error type leaves us unsure about this kind
     * of API-changing decision, and then we would work with _any_ Builder-generating API. We
     * would need to drop out part of our own builder superclass, to prevent chains of dependent
     * buildable types leading to quadratic compilation times (not to mention cycles), and leave a
     * dangling super-superclass to pick up next round. As an optimization, though, we would
     * probably skip this for @FreeBuilder-types anyway, to avoid extra types whenever possible,
     * which leaves a lot of complicated code supporting a currently non-existent edge case.
     */
    if (!findAnnotationMirror(element, FreeBuilder.class).isPresent()) {
      List<ExecutableElement> methods = elements.getAllMembers(builder)
          .stream()
          .flatMap(METHODS)
          .filter(BuildableType::isCallableMethod)
          .collect(toList());

      // Check there is a build() method
      if (!methods.stream().anyMatch(new IsBuildMethod("build", type, types))) {
        return Optional.empty();
      }

      // Check there is a buildPartial() method
      if (!methods.stream().anyMatch(new IsBuildMethod("buildPartial", type, types))) {
        return Optional.empty();
      }

      // Check there is a clear() method
      if (!methods.stream().anyMatch(BuildableType::isClearMethod)) {
        return Optional.empty();
      }

      // Check there is a mergeFrom(Value) method
      if (!methods.stream().anyMatch(new IsMergeFromMethod(type, builderMirror, types))) {
        return Optional.empty();
      }
    }

    return Optional.of(builderMirror);
  }

  public static BuildableType create(
      DeclaredType datatype,
      DeclaredType builder,
      Elements elements,
      Types types) {
    BuilderFactory builderFactory = BuilderFactory.from(asElement(builder)).get();
    MergeBuilderMethod mergeFromBuilderMethod =
        detectMergeFromBuilderMethod(builder, elements, types, asElement(datatype));
    PartialToBuilderMethod partialToBuilderMethod =
        detectPartialToBuilderMethod(datatype, builder, elements, types);
    Excerpt suppressUnchecked = suppressUncheckedExcerptFor(datatype);

    return new Builder()
        .type(Type.from(datatype))
        .builderType(Type.from(builder))
        .mergeBuilder(mergeFromBuilderMethod)
        .partialToBuilder(partialToBuilderMethod)
        .builderFactory(builderFactory)
        .suppressUnchecked(suppressUnchecked)
        .build();
  }

  private static MergeBuilderMethod detectMergeFromBuilderMethod(
      DeclaredType builder,
      Elements elements,
      Types types,
      TypeElement datatypeElement) {
    if (findAnnotationMirror(datatypeElement, FreeBuilder.class).isPresent()) {
      return MergeBuilderMethod.MERGE_DIRECTLY;
    } else {
      List<ExecutableElement> methods = elements.getAllMembers(asElement(builder))
          .stream()
          .flatMap(METHODS)
          .filter(BuildableType::isCallableMethod)
          .collect(toList());

      // Check whether there is a mergeFrom(Builder) method
      if (methods.stream().anyMatch(new IsMergeFromMethod(builder, builder, types))) {
        return MergeBuilderMethod.MERGE_DIRECTLY;
      } else {
        return MergeBuilderMethod.BUILD_PARTIAL_AND_MERGE;
      }
    }
  }

  private static PartialToBuilderMethod detectPartialToBuilderMethod(
      DeclaredType datatype,
      DeclaredType builder,
      Elements elements,
      Types types) {
    List<ExecutableElement> valueMethods = elements.getAllMembers(asElement(datatype))
        .stream()
        .flatMap(METHODS)
        .filter(BuildableType::isCallableMethod)
        .collect(toList());

    // Check whether there is a toBuilder() method
    if (valueMethods.stream().anyMatch(new IsToBuilderMethod(datatype, builder, types))) {
      return PartialToBuilderMethod.TO_BUILDER_AND_MERGE;
    } else {
      return PartialToBuilderMethod.MERGE_DIRECTLY;
    }
  }

  private static Excerpt suppressUncheckedExcerptFor(DeclaredType datatype) {
    if (needsSafeVarargs(datatype)) {
      return Excerpts.add("@SuppressWarnings(\"unchecked\")");
    } else {
      return Excerpts.EMPTY;
    }
  }

  private static boolean isCallableMethod(ExecutableElement element) {
    boolean isMethod = (element.getKind() == ElementKind.METHOD);
    boolean isPublic = element.getModifiers().contains(Modifier.PUBLIC);
    boolean isNotStatic = !element.getModifiers().contains(Modifier.STATIC);
    boolean declaresNoExceptions = element.getThrownTypes().isEmpty();
    return isMethod && isPublic && isNotStatic && declaresNoExceptions;
  }

  private static final class IsBuildMethod implements Predicate<ExecutableElement> {
    final String methodName;
    final TypeMirror builtType;
    final Types types;

    IsBuildMethod(String methodName, TypeMirror builtType, Types types) {
      this.methodName = methodName;
      this.builtType = builtType;
      this.types = types;
    }

    @Override public boolean test(ExecutableElement element) {
      if (!element.getParameters().isEmpty()) {
        return false;
      }
      if (!element.getSimpleName().contentEquals(methodName)) {
        return false;
      }
      if (!types.isSubtype(element.getReturnType(), builtType)) {
        return false;
      }
      return true;
    }
  }

  private static boolean isBuilderType(TypeElement element) {
    return element.getSimpleName().contentEquals("Builder")
        && element.getModifiers().contains(PUBLIC);
  }

  private static boolean isClearMethod(ExecutableElement element) {
    if (!element.getParameters().isEmpty()) {
      return false;
    }
    if (!element.getSimpleName().contentEquals("clear")) {
      return false;
    }
    return true;
  }

  private static final class IsMergeFromMethod implements Predicate<ExecutableElement> {
    final DeclaredType parameter;
    final DeclaredType builder;
    final Types types;

    IsMergeFromMethod(DeclaredType parameter, DeclaredType builder, Types types) {
      this.parameter = parameter;
      this.builder = builder;
      this.types = types;
    }

    @Override public boolean test(ExecutableElement element) {
      if (element.getParameters().size() != 1) {
        return false;
      }
      if (!element.getSimpleName().contentEquals("mergeFrom")) {
        return false;
      }
      ExecutableType method = (ExecutableType) types.asMemberOf(builder, element);
      if (!types.isSubtype(parameter, method.getParameterTypes().get(0))) {
        return false;
      }
      return true;
    }
  }

  private static final class IsToBuilderMethod implements Predicate<ExecutableElement> {
    final DeclaredType datatype;
    final TypeMirror builder;
    final Types types;

    IsToBuilderMethod(DeclaredType datatype, TypeMirror builder, Types types) {
      this.datatype = datatype;
      this.builder = builder;
      this.types = types;
    }

    @Override public boolean test(ExecutableElement element) {
      if (element.getParameters().size() != 0) {
        return false;
      }
      if (!element.getSimpleName().contentEquals("toBuilder")) {
        return false;
      }
      ExecutableType method = (ExecutableType) types.asMemberOf(datatype, element);
      if (!types.isSubtype(method.getReturnType(), builder)) {
        return false;
      }
      return true;
    }
  }

  private static final Function<Element, Stream<TypeElement>> TYPES = element ->
      (element.getKind().isClass() || element.getKind().isInterface())
          ? Stream.of((TypeElement) element)
          : Stream.of();

  private static final Function<Element, Stream<ExecutableElement>> METHODS = element ->
      (element.getKind() == ElementKind.METHOD)
          ? Stream.of((ExecutableElement) element)
          : Stream.of();
}
