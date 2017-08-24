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

import static com.google.common.base.MoreObjects.firstNonNull;
import static javax.lang.model.type.TypeKind.DECLARED;
import static org.inferred.freebuilder.processor.BuilderMethods.getter;
import static org.inferred.freebuilder.processor.BuilderMethods.mapper;
import static org.inferred.freebuilder.processor.BuilderMethods.setter;
import static org.inferred.freebuilder.processor.util.Block.methodBody;
import static org.inferred.freebuilder.processor.util.ObjectsExcerpts.Nullability.NULLABLE;
import static org.inferred.freebuilder.processor.util.feature.FunctionPackage.FUNCTION_PACKAGE;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;

import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.util.Block;
import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.Excerpts;
import org.inferred.freebuilder.processor.util.FieldAccess;
import org.inferred.freebuilder.processor.util.ObjectsExcerpts;
import org.inferred.freebuilder.processor.util.ParameterizedType;
import org.inferred.freebuilder.processor.util.PreconditionExcerpts;
import org.inferred.freebuilder.processor.util.SourceBuilder;
import org.inferred.freebuilder.processor.util.TypeMirrorExcerpt;

import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/** {@link PropertyCodeGenerator} providing reference semantics for Nullable properties. */
class NullableProperty extends PropertyCodeGenerator {

  static class Factory implements PropertyCodeGenerator.Factory {

    @Override
    public Optional<NullableProperty> create(Config config) {
      Property property = config.getProperty();
      boolean isPrimitive = property.getType().getKind().isPrimitive();
      Set<TypeElement> nullableAnnotations = nullablesIn(config.getAnnotations());
      if (isPrimitive || nullableAnnotations.isEmpty()) {
        return Optional.absent();
      }
      return Optional.of(new NullableProperty(config.getMetadata(), property, nullableAnnotations));
    }

    private static Set<TypeElement> nullablesIn(Iterable<? extends AnnotationMirror> annotations) {
      ImmutableSet.Builder<TypeElement> nullableAnnotations = ImmutableSet.builder();
      for (AnnotationMirror mirror : annotations) {
        if (mirror.getElementValues().isEmpty()) {
          TypeElement type = (TypeElement) mirror.getAnnotationType().asElement();
          if (type.getSimpleName().contentEquals("Nullable")) {
            nullableAnnotations.add(type);
          }
        }
      }
      return nullableAnnotations.build();
    }
  }

  private final Set<TypeElement> nullables;

  NullableProperty(
      Metadata metadata,
      Property property,
      Iterable<TypeElement> nullableAnnotations) {
    super(metadata, property);
    this.nullables = ImmutableSet.copyOf(nullableAnnotations);
  }

  @Override
  public Type getType() {
    return Type.OPTIONAL;
  }

  @Override
  public void addBuilderFieldDeclaration(SourceBuilder code) {
    addGetterAnnotations(code);
    code.add("private %s %s = null;\n", property.getType(), property.getField());
  }

  @Override
  public void addBuilderFieldAccessors(SourceBuilder code) {
    addSetter(code, metadata);
    addMapper(code, metadata);
    addGetter(code, metadata);
  }

  private void addSetter(SourceBuilder code, final Metadata metadata) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Sets the value to be returned by %s.",
            metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
        .addLine(" */");
    addAccessorAnnotations(code);
    code.add("public %s %s(", metadata.getBuilder(), setter(property));
    addGetterAnnotations(code);
    code.add("%s %s) {\n", property.getType(), property.getName())
        .add(methodBody(code, property.getName())
            .addLine("  %s = %s;", property.getField(), property.getName())
            .addLine("  return (%s) this;", metadata.getBuilder()))
        .addLine("}");
  }

  private void addMapper(SourceBuilder code, final Metadata metadata) {
    ParameterizedType unaryOperator = code.feature(FUNCTION_PACKAGE).unaryOperator().orNull();
    if (unaryOperator == null) {
      return;
    }
    TypeMirror typeParam = firstNonNull(property.getBoxedType(), property.getType());
    code.addLine("")
        .addLine("/**")
        .addLine(" * If the value to be returned by %s is not",
            metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" * null, replaces it by applying {@code mapper} to it and using the result.")
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
        .addLine(" * @throws NullPointerException if {@code mapper} is null")
        .addLine(" */")
        .addLine("public %s %s(%s mapper) {",
            metadata.getBuilder(),
            mapper(property),
            unaryOperator.withParameters(typeParam))
        .add(PreconditionExcerpts.checkNotNull("mapper"));
    Block body = methodBody(code, "mapper");
    Excerpt propertyValue = body.declare(new TypeMirrorExcerpt(
        property.getType()), property.getName(), Excerpts.add("%s()", getter(property)));
    body.addLine("  if (%s != null) {", propertyValue)
        .addLine("    %s(mapper.apply(%s));", setter(property), propertyValue)
        .addLine("  }")
        .addLine("  return (%s) this;", metadata.getBuilder());
    code.add(body)
        .addLine("}");
  }

  private void addGetter(SourceBuilder code, final Metadata metadata) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Returns the value that will be returned by %s.",
            metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" */");
    addGetterAnnotations(code);
    code.addLine("public %s %s() {", property.getType(), getter(property))
        .addLine("  return %s;", property.getField())
        .addLine("}");
  }

  @Override
  public void addValueFieldDeclaration(SourceBuilder code, FieldAccess finalField) {
    addGetterAnnotations(code);
    code.add("private final %s %s;\n", property.getType(), finalField);
  }

  @Override
  public void addFinalFieldAssignment(SourceBuilder code, Excerpt finalField, String builder) {
    code.addLine("%s = %s;", finalField, property.getField().on(builder));
  }

  @Override
  public void addMergeFromValue(Block code, String value) {
    Excerpt defaults = Declarations.freshBuilder(code, metadata).orNull();
    if (defaults != null) {
      code.addLine("if (%s) {", ObjectsExcerpts.notEquals(
          Excerpts.add("%s.%s()", value, property.getGetterName()),
          Excerpts.add("%s.%s()", defaults, getter(property)),
          DECLARED,
          NULLABLE));
    }
    code.addLine("  %s(%s.%s());", setter(property), value, property.getGetterName());
    if (defaults != null) {
      code.addLine("}");
    }
  }

  @Override
  public void addMergeFromBuilder(Block code, String builder) {
    Excerpt defaults = Declarations.freshBuilder(code, metadata).orNull();
    if (defaults != null) {
      code.addLine("if (%s) {", ObjectsExcerpts.notEquals(
          Excerpts.add("%s.%s()", builder, getter(property)),
          Excerpts.add("%s.%s()", defaults, getter(property)),
          DECLARED,
          NULLABLE));
    }
    code.addLine("  %s(%s.%s());", setter(property), builder, getter(property));
    if (defaults != null) {
      code.addLine("}");
    }
  }

  @Override
  public void addGetterAnnotations(SourceBuilder code) {
    for (TypeElement nullableAnnotation : nullables) {
      code.add("@%s ", nullableAnnotation);
    }
  }

  @Override
  public void addSetFromResult(SourceBuilder code, Excerpt builder, Excerpt variable) {
    code.addLine("%s.%s(%s);", builder, setter(property), variable);
  }

  @Override
  public void addClearField(Block code) {
    Optional<Excerpt> defaults = Declarations.freshBuilder(code, metadata);
    if (defaults.isPresent()) {
      code.addLine("%s = %s;", property.getField(), property.getField().on(defaults.get()));
    } else {
      code.addLine("%s = null;", property.getField());
    }
  }
}
