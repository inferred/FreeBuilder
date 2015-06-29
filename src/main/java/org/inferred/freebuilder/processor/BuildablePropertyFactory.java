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

import static com.google.common.collect.Iterables.any;
import static com.google.common.collect.Iterables.tryFind;
import static javax.lang.model.util.ElementFilter.typesIn;
import static org.inferred.freebuilder.processor.util.ModelUtils.findAnnotationMirror;

import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.PropertyCodeGenerator.Config;
import org.inferred.freebuilder.processor.util.SourceBuilder;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;

/**
 * {@link PropertyCodeGenerator.Factory} for <b>buildable</b> types: that is, types with a Builder
 * class providing a similar API to proto or &#64;FreeBuilder:<ul>
 * <li> a public constructor, or static builder()/newBuilder() method;
 * <li> build(), buildPartial() and clear() methods; and
 * <li> a mergeWith(Value) method.
 * </ul>
 */
public class BuildablePropertyFactory implements PropertyCodeGenerator.Factory {

  private static final String SET_PREFIX = "set";
  private static final String GET_BUILDER_PREFIX = "get";
  private static final String GET_BUILDER_SUFFIX = "Builder";

  /** How to merge the values from one Builder into another. */
  private enum MergeBuilderMethod {
    MERGE_DIRECTLY, BUILD_PARTIAL_AND_MERGE
  }

  @Override
  public Optional<? extends PropertyCodeGenerator> create(Config config) {
    // No @Nullable properties
    if (!config.getProperty().getNullableAnnotations().isEmpty()) {
      return Optional.absent();
    }

    // Check this is a declared type
    TypeMirror type = config.getProperty().getType();
    if (type.getKind() != TypeKind.DECLARED) {
      return Optional.absent();
    }
    TypeElement element = (TypeElement) ((DeclaredType) type).asElement();

    // Find the builder
    Optional<TypeElement> builder =
        tryFind(typesIn(element.getEnclosedElements()), IS_BUILDER_TYPE);
    if (!builder.isPresent()) {
      return Optional.absent();
    }

    // Verify the builder can be constructed
    Optional<BuilderFactory> builderFactory = BuilderFactory.from(builder.get());
    if (!builderFactory.isPresent()) {
      return Optional.absent();
    }

    MergeBuilderMethod mergeFromBuilderMethod;
    if (findAnnotationMirror(element, "org.inferred.freebuilder.FreeBuilder").isPresent()) {
      /*
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
      mergeFromBuilderMethod = MergeBuilderMethod.MERGE_DIRECTLY;
    } else {
      List<ExecutableElement> methods = FluentIterable
          .from(config.getElements().getAllMembers(builder.get()))
          .filter(ExecutableElement.class)
          .filter(new IsCallableMethod())
          .toList();

      // Check there is a build() method
      if (!any(methods, new IsBuildMethod("build", type, config.getTypes()))) {
        return Optional.absent();
      }

      // Check there is a buildPartial() method
      if (!any(methods, new IsBuildMethod("buildPartial", type, config.getTypes()))) {
        return Optional.absent();
      }

      // Check there is a clear() method
      if (!any(methods, new IsClearMethod())) {
        return Optional.absent();
      }

      // Check there is a mergeFrom(Value) method
      if (!any(methods, new IsMergeFromMethod(type, config.getTypes()))) {
        return Optional.absent();
      }

      // Check whether there is a mergeFrom(Builder) method
      if (any(methods, new IsMergeFromMethod(builder.get().asType(), config.getTypes()))) {
        mergeFromBuilderMethod = MergeBuilderMethod.MERGE_DIRECTLY;
      } else {
        mergeFromBuilderMethod = MergeBuilderMethod.BUILD_PARTIAL_AND_MERGE;
      }
    }

    String setterName = SET_PREFIX + config.getProperty().getCapitalizedName();
    String getBuilderName =
        GET_BUILDER_PREFIX + config.getProperty().getCapitalizedName() + GET_BUILDER_SUFFIX;
    return Optional.of(new CodeGenerator(
        config.getProperty(),
        builder.get(),
        builderFactory.get(),
        setterName,
        getBuilderName,
        mergeFromBuilderMethod));
  }

  @VisibleForTesting static class CodeGenerator extends PropertyCodeGenerator {

    final TypeElement builderType;
    final BuilderFactory builderFactory;
    final String setterName;
    final String getBuilderName;
    final MergeBuilderMethod mergeFromBuilderMethod;

    CodeGenerator(
        Property property,
        TypeElement builderType,
        BuilderFactory builderFactory,
        String setterName,
        String getBuilderName,
        MergeBuilderMethod mergeFromBuilderMethod) {
      super(property);
      this.builderType = builderType;
      this.builderFactory = builderFactory;
      this.setterName = setterName;
      this.getBuilderName = getBuilderName;
      this.mergeFromBuilderMethod = mergeFromBuilderMethod;
    }

    @Override
    public void addBuilderFieldDeclaration(SourceBuilder code) {
      code.add("private final %s %s = ", builderType, property.getName());
      builderFactory.addNewBuilder(code, builderType);
      code.add(";\n");
    }

    @Override
    public void addBuilderFieldAccessors(SourceBuilder code, Metadata metadata) {
      // set(T)
      code.addLine("")
          .addLine("/**")
          .addLine(" * Sets the value to be returned by {@link %s#%s()}.",
              metadata.getType(), property.getGetterName())
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
          .addLine(" * @throws NullPointerException if {@code %s} is null", property.getName())
          .addLine(" */")
          .addLine("public %s %s(%s %s) {",
              metadata.getBuilder(),
              setterName,
              property.getType(),
              property.getName())
          .addLine("  this.%s.clear();", property.getName())
          .addLine("  this.%1$s.mergeFrom(%2$s.checkNotNull(%1$s));",
              property.getName(), Preconditions.class)
          .addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");

      // set(T.Builder)
      code.addLine("")
          .addLine("/**")
          .addLine(" * Sets the value to be returned by {@link %s#%s()}.",
              metadata.getType(), property.getGetterName())
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
          .addLine(" * @throws NullPointerException if {@code builder} is null")
          .addLine(" */")
          .addLine("public %s %s(%s.Builder builder) {",
              metadata.getBuilder(),
              setterName,
              property.getType())
          .addLine("  return %s(builder.build());", setterName)
          .addLine("}");

      // getBuilder()
      code.addLine("")
          .addLine("/**")
          .addLine(" * Returns a builder for the value that will be returned by {@link %s#%s()}.",
              metadata.getType(), property.getGetterName())
          .addLine(" */")
          .addLine("public %s %s() {", builderType, getBuilderName)
          .addLine("  return %s;", property.getName())
          .addLine("}");
    }

    @Override
    public void addFinalFieldAssignment(SourceBuilder code, String finalField, String builder) {
      code.addLine("%s = %s.%s.build();", finalField, builder, property.getName());
    }

    @Override
    public void addPartialFieldAssignment(SourceBuilder code, String finalField, String builder) {
      code.addLine("%s = %s.%s.buildPartial();", finalField, builder, property.getName());
    }

    @Override
    public void addMergeFromValue(SourceBuilder code, String value) {
      code.addLine("%s.mergeFrom(%s.%s());", property.getName(), value, property.getGetterName());
    }

    @Override
    public void addMergeFromBuilder(SourceBuilder code, Metadata metadata, String builder) {
      code.add("%s.mergeFrom(%s.%s()", property.getName(), builder, getBuilderName);
      if (mergeFromBuilderMethod == MergeBuilderMethod.BUILD_PARTIAL_AND_MERGE) {
        code.add(".buildPartial()");
      }
      code.add(");\n");
    }

    @Override
    public void addSetFromResult(SourceBuilder code, String builder, String variable) {
      code.addLine("%s.%s(%s);", builder, setterName, variable);
    }

    @Override
    public boolean isTemplateRequiredInClear() {
      return false;
    }

    @Override
    public void addClear(SourceBuilder code, String template) {
      code.addLine("%s.clear();", property.getName());
    }

    @Override
    public void addPartialClear(SourceBuilder code) {
      code.addLine("%s.clear();", property.getName());
    }
  }

  private static final class IsCallableMethod implements Predicate<ExecutableElement> {
    @Override
    public boolean apply(ExecutableElement element) {
      boolean isMethod = (element.getKind() == ElementKind.METHOD);
      boolean isPublic = element.getModifiers().contains(Modifier.PUBLIC);
      boolean isNotStatic = !element.getModifiers().contains(Modifier.STATIC);
      boolean declaresNoExceptions = element.getThrownTypes().isEmpty();
      return isMethod && isPublic && isNotStatic && declaresNoExceptions;
    }
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

    @Override public boolean apply(ExecutableElement element) {
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

  private static final class IsClearMethod implements Predicate<ExecutableElement> {
    @Override public boolean apply(ExecutableElement element) {
      if (!element.getParameters().isEmpty()) {
        return false;
      }
      if (!element.getSimpleName().contentEquals("clear")) {
        return false;
      }
      return true;
    }
  }

  private static final class IsMergeFromMethod implements Predicate<ExecutableElement> {
    final TypeMirror builderType;
    final Types types;

    IsMergeFromMethod(TypeMirror sourceType, Types types) {
      this.builderType = sourceType;
      this.types = types;
    }

    @Override public boolean apply(ExecutableElement element) {
      if (element.getParameters().size() != 1) {
        return false;
      }
      if (!element.getSimpleName().contentEquals("mergeFrom")) {
        return false;
      }
      if (!types.isSubtype(builderType, element.getParameters().get(0).asType())) {
        return false;
      }
      return true;
    }
  }

  private static final Predicate<Element> IS_BUILDER_TYPE = new Predicate<Element>() {
    @Override public boolean apply(Element element) {
      return element.getSimpleName().contentEquals("Builder");
    }
  };

}
