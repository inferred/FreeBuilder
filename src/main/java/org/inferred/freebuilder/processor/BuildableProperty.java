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
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.util.ElementFilter.typesIn;
import static org.inferred.freebuilder.processor.BuilderFactory.TypeInference.EXPLICIT_TYPES;
import static org.inferred.freebuilder.processor.BuilderMethods.getBuilderMethod;
import static org.inferred.freebuilder.processor.BuilderMethods.mutator;
import static org.inferred.freebuilder.processor.BuilderMethods.setter;
import static org.inferred.freebuilder.processor.util.Block.methodBody;
import static org.inferred.freebuilder.processor.util.ModelUtils.asElement;
import static org.inferred.freebuilder.processor.util.ModelUtils.findAnnotationMirror;
import static org.inferred.freebuilder.processor.util.ModelUtils.maybeDeclared;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;

import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.util.Block;
import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.Excerpts;
import org.inferred.freebuilder.processor.util.ModelUtils;
import org.inferred.freebuilder.processor.util.ParameterizedType;
import org.inferred.freebuilder.processor.util.SourceBuilder;
import org.inferred.freebuilder.processor.util.Variable;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

/**
 * {@link PropertyCodeGenerator} for <b>buildable</b> types: that is, types with a Builder class
 * providing a similar API to proto or &#64;FreeBuilder:<ul>
 * <li> a public constructor, or static builder()/newBuilder() method;
 * <li> build(), buildPartial() and clear() methods; and
 * <li> a mergeWith(Value) method.
 * </ul>
 */
class BuildableProperty extends PropertyCodeGenerator {

  /** How to merge the values from one Builder into another. */
  private enum MergeBuilderMethod {
    MERGE_DIRECTLY, BUILD_PARTIAL_AND_MERGE
  }

  /** How to convert a partial value into a Builder. */
  private enum PartialToBuilderMethod {
    MERGE_DIRECTLY, TO_BUILDER_AND_MERGE
  }

  static class Factory implements PropertyCodeGenerator.Factory {

    @Override
    public Optional<BuildableProperty> create(Config config) {
      DeclaredType type = maybeDeclared(config.getProperty().getType()).orNull();
      if (type == null) {
        return Optional.absent();
      }
      TypeElement element = asElement(type);

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
         * strictly correct, we should delay a round if an error type leaves us unsure about this
         * kind of API-changing decision, and then we would work with _any_ Builder-generating API.
         * We would need to drop out part of our own builder superclass, to prevent chains of
         * dependent buildable types leading to quadratic compilation times (not to mention cycles),
         * and leave a dangling super-superclass to pick up next round. As an optimization, though,
         * we would probably skip this for @FreeBuilder-types anyway, to avoid extra types whenever
         * possible, which leaves a lot of complicated code supporting a currently non-existent edge
         * case.
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

      List<ExecutableElement> valueMethods = FluentIterable
          .from(config.getElements().getAllMembers(element))
          .filter(ExecutableElement.class)
          .filter(new IsCallableMethod())
          .toList();

      // Check whether there is a toBuilder() method
      PartialToBuilderMethod partialToBuilderMethod;
      if (any(valueMethods, new IsToBuilderMethod(builder.get().asType(), config.getTypes()))) {
        partialToBuilderMethod = PartialToBuilderMethod.TO_BUILDER_AND_MERGE;
      } else {
        partialToBuilderMethod = PartialToBuilderMethod.MERGE_DIRECTLY;
      }

      return Optional.of(new BuildableProperty(
          config.getMetadata(),
          config.getProperty(),
          ParameterizedType.from(builder.get()),
          builderFactory.get(),
          mergeFromBuilderMethod,
          partialToBuilderMethod));
    }
  }

  private final ParameterizedType builderType;
  private final BuilderFactory builderFactory;
  private final MergeBuilderMethod mergeFromBuilderMethod;
  private final PartialToBuilderMethod partialToBuilderMethod;
  private final Excerpt suppressUnchecked;

  private BuildableProperty(
      Metadata metadata,
      Property property,
      ParameterizedType builderType,
      BuilderFactory builderFactory,
      MergeBuilderMethod mergeFromBuilderMethod,
      PartialToBuilderMethod partialToBuilderMethod) {
    super(metadata, property);
    this.builderType = builderType;
    this.builderFactory = builderFactory;
    this.mergeFromBuilderMethod = mergeFromBuilderMethod;
    this.partialToBuilderMethod = partialToBuilderMethod;
    if (ModelUtils.needsSafeVarargs(property.getType())) {
      suppressUnchecked = Excerpts.add("@SuppressWarnings(\"unchecked\")");
    } else {
      suppressUnchecked = Excerpts.empty();
    }
  }

  @Override
  public void addBuilderFieldDeclaration(SourceBuilder code) {
    code.addLine("private Object %s = null;", property.getField());
  }

  @Override
  public void addBuilderFieldAccessors(SourceBuilder code) {
    addSetter(code, metadata);
    addSetterTakingBuilder(code, metadata);
    addMutate(code, metadata);
    addGetter(code, metadata);
  }

  private void addSetter(SourceBuilder code, Metadata metadata) {
    Variable builder = new Variable("builder");
    code.addLine("")
        .addLine("/**")
        .addLine(" * Sets the value to be returned by %s.",
            metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
        .addLine(" * @throws NullPointerException if {@code %s} is null", property.getName())
        .addLine(" */");
    addAccessorAnnotations(code);
    code.addLine("public %s %s(%s %s) {",
            metadata.getBuilder(),
            setter(property),
            property.getType(),
            property.getName());
    Block body = methodBody(code, property.getName())
        .addLine("  %s.requireNonNull(%s);", Objects.class, property.getName())
        .addLine("  if (%1$s == null || %1$s instanceof %2$s) {",
            property.getField(), ModelUtils.maybeAsTypeElement(property.getType()).get())
        .addLine("    %s = %s;", property.getField(), property.getName())
        .addLine("  } else {")
        .addLine("    %1$s %2$s %3$s = (%2$s) %4$s;",
            suppressUnchecked, builderType, builder, property.getField())
        .addLine("    %s.clear();", builder)
        .addLine("    %s.mergeFrom(%s);", builder, property.getName())
        .addLine("  }")
        .addLine("  return (%s) this;", metadata.getBuilder());
    code.add(body)
        .addLine("}");
  }

  private void addSetterTakingBuilder(SourceBuilder code, Metadata metadata) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Sets the value to be returned by %s.",
            metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
        .addLine(" * @throws NullPointerException if {@code builder} is null")
        .addLine(" */")
        .addLine("public %s %s(%s builder) {",
            metadata.getBuilder(),
            setter(property),
            builderType)
        .addLine("  return %s(builder.build());", setter(property))
        .addLine("}");
  }

  private void addMutate(SourceBuilder code, Metadata metadata) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Applies {@code mutator} to the builder for the value that will be")
        .addLine(" * returned by %s.",
            metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * <p>This method mutates the builder in-place. {@code mutator} is a void")
        .addLine(" * consumer, so any value returned from a lambda will be ignored.")
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
        .addLine(" * @throws NullPointerException if {@code mutator} is null")
        .addLine(" */")
        .addLine("public %s %s(%s<%s> mutator) {",
            metadata.getBuilder(),
            mutator(property),
            Consumer.class,
            builderType)
        .add(methodBody(code, "mutator")
            .addLine("  mutator.accept(%s());", getBuilderMethod(property))
            .addLine("  return (%s) this;", metadata.getBuilder()))
        .addLine("}");
  }

  private void addGetter(SourceBuilder code, Metadata metadata) {
    Variable builder = new Variable("builder");
    Variable value = new Variable("value");
    code.addLine("")
        .addLine("/**")
        .addLine(" * Returns a builder for the value that will be returned by %s.",
            metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" */")
        .addLine("public %s %s() {", builderType, getBuilderMethod(property));
    Block body = methodBody(code)
        .addLine("  if (%s == null) {", property.getField())
        .addLine("    %s = %s;",
            property.getField(), builderFactory.newBuilder(builderType, EXPLICIT_TYPES))
        .addLine("  } else if (%s instanceof %s) {",
            property.getField(), ModelUtils.maybeAsTypeElement(property.getType()).get())
        .addLine("    %1$s %2$s %3$s = (%2$s) %4$s;",
            suppressUnchecked, property.getType(), value, property.getField());
    if (partialToBuilderMethod == PartialToBuilderMethod.TO_BUILDER_AND_MERGE) {
      body.addLine("    %s = %s.toBuilder();", property.getField(), value);
    } else {
      body.addLine("    %s = %s",
              property.getField(), builderFactory.newBuilder(builderType, EXPLICIT_TYPES))
          .addLine("        .mergeFrom(%s);", value);
    }
    body.addLine("  }")
        .addLine("  %1$s %2$s %3$s = (%2$s) %4$s;",
            suppressUnchecked, builderType, builder, property.getField())
        .addLine("  return %s;", builder);
    code.add(body)
        .addLine("}");
  }

  @Override
  public void addFinalFieldAssignment(SourceBuilder code, Excerpt finalField, String builder) {
    addFieldAssignment(code, finalField, builder, "build");
  }

  @Override
  public void addPartialFieldAssignment(SourceBuilder code, Excerpt finalField, String builder) {
    addFieldAssignment(code, finalField, builder, "buildPartial");
  }

  private void addFieldAssignment(
      SourceBuilder code,
      Excerpt finalField,
      String builder,
      String buildMethod) {
    Variable fieldBuilder = new Variable(property.getName() + "Builder");
    Variable fieldValue = new Variable(property.getName() + "Value");
    code.addLine("if (%s == null) {", property.getField().on(builder))
        .addLine("  %s = %s.%s();",
            finalField, builderFactory.newBuilder(builderType, EXPLICIT_TYPES), buildMethod)
        .addLine("} else if (%s instanceof %s) {",
            property.getField().on(builder),
            ModelUtils.maybeAsTypeElement(property.getType()).get());
    if (suppressUnchecked != Excerpts.empty()) {
      code.addLine("  %1$s %2$s %3$s = (%2$s) %4$s;",
              suppressUnchecked, property.getType(), fieldValue, property.getField().on(builder))
          .addLine("  %s = %s;", finalField, fieldValue);
    } else {
      code.addLine("  %s = (%s) %s;",
              finalField, property.getType(), property.getField().on(builder));
    }
    code.addLine("} else {")
        .addLine("  %1$s %2$s %3$s = (%2$s) %4$s;",
            suppressUnchecked, builderType, fieldBuilder, property.getField().on(builder))
        .addLine("  %s = %s.%s();", finalField, fieldBuilder, buildMethod)
        .addLine("}");
  }

  @Override
  public void addMergeFromValue(Block code, String value) {
    code.addLine("if (%s == null) {", property.getField())
        .addLine("  %s = %s.%s();", property.getField(), value, property.getGetterName())
        .addLine("} else {")
        .addLine("  %s().mergeFrom(%s.%s());",
            getBuilderMethod(property), value, property.getGetterName())
        .addLine("}");
  }

  @Override
  public void addMergeFromBuilder(Block code, String builder) {
    Excerpt base = Declarations.upcastToGeneratedBuilder(code, metadata, builder);
    Variable fieldValue = new Variable(property.getName() + "Value");
    code.addLine("if (%s == null) {", property.getField().on(base))
        .addLine("  // Nothing to merge")
        .addLine("} else if (%s instanceof %s) {",
            property.getField().on(base),
            ModelUtils.maybeAsTypeElement(property.getType()).get())
        .addLine("  %1$s %2$s %3$s = (%2$s) %4$s;",
            suppressUnchecked, property.getType(), fieldValue, property.getField().on(base))
        .addLine("  if (%s == null) {", property.getField())
        .addLine("    %s = %s;", property.getField(), fieldValue)
        .addLine("  } else {")
        .addLine("    %s().mergeFrom(%s);", getBuilderMethod(property), fieldValue)
        .addLine("  }")
        .addLine("} else {")
        .add("  %s().mergeFrom(%s.%s()",
            getBuilderMethod(property), base, getBuilderMethod(property));
    if (mergeFromBuilderMethod == MergeBuilderMethod.BUILD_PARTIAL_AND_MERGE) {
      code.add(".buildPartial()");
    }
    code.add(");\n")
        .addLine("}");
  }

  @Override
  public void addSetBuilderFromPartial(Block code, String builder) {
    if (partialToBuilderMethod == PartialToBuilderMethod.TO_BUILDER_AND_MERGE) {
      code.add("%s.%s().mergeFrom(%s.toBuilder());",
          builder, getBuilderMethod(property), property.getField());
    } else {
      code.add("%s.%s().mergeFrom(%s);",
          builder, getBuilderMethod(property), property.getField());
    }
  }

  @Override
  public void addSetFromResult(SourceBuilder code, Excerpt builder, Excerpt variable) {
    code.addLine("%s.%s(%s);", builder, setter(property), variable);
  }

  @Override
  public void addClearField(Block code) {
    Variable fieldBuilder = new Variable(property.getName() + "Builder");
    code.addLine("  if (%1$s == null || %1$s instanceof %2$s) {",
            property.getField(),
            ModelUtils.maybeAsTypeElement(property.getType()).get())
        .addLine("    %s = null;", property.getField())
        .addLine("  } else {")
        .addLine("    %1$s %2$s %3$s = (%2$s) %4$s;",
            suppressUnchecked, builderType, fieldBuilder, property.getField())
        .addLine("    %s.clear();", fieldBuilder)
        .addLine("  }");
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

  private static final class IsToBuilderMethod implements Predicate<ExecutableElement> {
    final TypeMirror builderType;
    final Types types;

    IsToBuilderMethod(TypeMirror sourceType, Types types) {
      this.builderType = sourceType;
      this.types = types;
    }

    @Override public boolean apply(ExecutableElement element) {
      if (element.getParameters().size() != 0) {
        return false;
      }
      if (!element.getSimpleName().contentEquals("toBuilder")) {
        return false;
      }
      if (!types.isSubtype(element.getReturnType(), builderType)) {
        return false;
      }
      return true;
    }
  }

  private static final Predicate<Element> IS_BUILDER_TYPE = new Predicate<Element>() {
    @Override public boolean apply(Element element) {
      return element.getSimpleName().contentEquals("Builder")
          && element.getModifiers().contains(PUBLIC);
    }
  };

}
