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

import static org.inferred.freebuilder.processor.BuilderMethods.clearMethod;
import static org.inferred.freebuilder.processor.BuilderMethods.getter;
import static org.inferred.freebuilder.processor.BuilderMethods.mapper;
import static org.inferred.freebuilder.processor.BuilderMethods.nullableSetter;
import static org.inferred.freebuilder.processor.BuilderMethods.setter;
import static org.inferred.freebuilder.processor.Util.erasesToAnyOf;
import static org.inferred.freebuilder.processor.Util.upperBound;
import static org.inferred.freebuilder.processor.util.Block.methodBody;
import static org.inferred.freebuilder.processor.util.FunctionalType.functionalTypeAcceptedByMethod;
import static org.inferred.freebuilder.processor.util.FunctionalType.unaryOperator;
import static org.inferred.freebuilder.processor.util.ModelUtils.maybeDeclared;
import static org.inferred.freebuilder.processor.util.ModelUtils.maybeUnbox;

import com.google.common.annotations.VisibleForTesting;

import org.inferred.freebuilder.processor.util.Block;
import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.FieldAccess;
import org.inferred.freebuilder.processor.util.FunctionalType;
import org.inferred.freebuilder.processor.util.QualifiedName;
import org.inferred.freebuilder.processor.util.SourceBuilder;
import org.inferred.freebuilder.processor.util.Variable;
import org.inferred.freebuilder.processor.util.feature.Jsr305;

import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/**
 * {@link PropertyCodeGenerator} providing a default value (absent/empty) and convenience setter
 * methods for Guava and Java 8 Optional properties.
 */
class OptionalProperty extends PropertyCodeGenerator {

  @VisibleForTesting
  enum OptionalType {
    GUAVA(QualifiedName.of(com.google.common.base.Optional.class), "absent", "fromNullable") {
      @Override
      protected void applyMapper(
          SourceBuilder code,
          Datatype datatype,
          FunctionalType mapperType,
          Property property) {
        // Guava's transform method throws a NullPointerException if mapper returns null,
        // and it has no flatMap-equivalent. We choose to follow the Java 8 convention of
        // turning a null into an empty (absent) optional as that is the de facto standard
        // now. (If the mapper type *can* return null, of course.)
        if (mapperType.canReturnNull()) {
          code.addLine("%s.requireNonNull(mapper);", Objects.class)
              .addLine("  %s old%s = %s();",
                  property.getType(), property.getCapitalizedName(), getter(property))
              .addLine("  if (old%s.isPresent()) {", property.getCapitalizedName())
              .addLine("     %s(mapper.%s(old%s.get()));",
                  nullableSetter(property),
                  mapperType.getMethodName(),
                  property.getCapitalizedName())
              .addLine("  }")
              .addLine("  return (%s) this;", datatype.getBuilder());
        } else {
          code.addLine("  return %s(%s().transform(mapper::%s));",
              setter(property), getter(property), mapperType.getMethodName());
        }
      }

      @Override
      protected void invokeIfPresent(SourceBuilder code, String value, String method) {
        code.addLine("if (%s.isPresent()) {", value)
            .addLine("  %s(%s.get());", method, value)
            .addLine("}");
      }
    },
    JAVA8(QualifiedName.of(Optional.class), "empty", "ofNullable") {
      @Override
      protected void applyMapper(
          SourceBuilder code,
          Datatype datatype,
          FunctionalType mapperType,
          Property property) {
        code.add("  return %s(%s().map(mapper", setter(property), getter(property));
        if (!mapperType.getFunctionalInterface().getQualifiedName()
            .equals(QualifiedName.of(UnaryOperator.class))) {
          code.add("::%s", mapperType.getMethodName());
        }
        code.add("));%n");
      }

      @Override
      protected void invokeIfPresent(SourceBuilder code, String value, String method) {
        code.addLine("%s.ifPresent(this::%s);", value, method);
      }
    };

    private final QualifiedName cls;
    private final String empty;
    private final String ofNullable;

    OptionalType(QualifiedName cls, String empty, String ofNullable) {
      this.cls = cls;
      this.empty = empty;
      this.ofNullable = ofNullable;
    }

    protected abstract void applyMapper(
        SourceBuilder code,
        Datatype datatype,
        FunctionalType mapperType,
        Property property);
    protected abstract void invokeIfPresent(SourceBuilder code, String value, String method);
  }

  static class Factory implements PropertyCodeGenerator.Factory {

    @Override
    public Optional<OptionalProperty> create(Config config) {
      Property property = config.getProperty();
      DeclaredType type = maybeDeclared(property.getType()).orElse(null);
      if (type == null) {
        return Optional.empty();
      }

      OptionalType optionalType = maybeOptional(type).orElse(null);
      if (optionalType == null) {
        return Optional.empty();
      }

      TypeMirror elementType = upperBound(config.getElements(), type.getTypeArguments().get(0));
      Optional<TypeMirror> unboxedType = maybeUnbox(elementType, config.getTypes());

      FunctionalType mapperType = functionalTypeAcceptedByMethod(
          config.getBuilder(),
          mapper(property),
          unaryOperator(elementType),
          config.getElements(),
          config.getTypes());

      return Optional.of(new OptionalProperty(
          config.getDatatype(),
          property,
          optionalType,
          elementType,
          unboxedType,
          mapperType));
    }

    private static Optional<OptionalType> maybeOptional(DeclaredType type) {
      for (OptionalType optionalType : OptionalType.values()) {
        if (erasesToAnyOf(type, optionalType.cls)) {
          return Optional.of(optionalType);
        }
      }
      return Optional.empty();
    }
  }

  private final OptionalType optional;
  private final TypeMirror elementType;
  private final Optional<TypeMirror> unboxedType;
  private final FunctionalType mapperType;

  @VisibleForTesting OptionalProperty(
      Datatype datatype,
      Property property,
      OptionalType optional,
      TypeMirror elementType,
      Optional<TypeMirror> unboxedType,
      FunctionalType mapperType) {
    super(datatype, property);
    this.optional = optional;
    this.elementType = elementType;
    this.unboxedType = unboxedType;
    this.mapperType = mapperType;
  }

  @Override
  public Initially initialState() {
    return Initially.OPTIONAL;
  }

  @Override
  public void addValueFieldDeclaration(SourceBuilder code, FieldAccess finalField) {
    code.addLine("// Store a nullable object instead of an Optional. Escape analysis then")
        .addLine("// allows the JVM to optimize away the Optional objects created by our")
        .addLine("// getter method.")
        .addLine("private final %s %s;", elementType, finalField);
  }

  @Override
  public void addBuilderFieldDeclaration(SourceBuilder code) {
    code.addLine("// Store a nullable object instead of an Optional. Escape analysis then")
        .addLine("// allows the JVM to optimize away the Optional objects created by and")
        .addLine("// passed to our API.")
        .addLine("private %s %s = null;", elementType, property.getField());
  }

  @Override
  public void addBuilderFieldAccessors(SourceBuilder code) {
    addSetter(code);
    addOptionalSetter(code);
    addNullableSetter(code);
    addMapper(code);
    addClear(code);
    addGetter(code);
  }

  private void addSetter(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Sets the value to be returned by %s.",
            datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName());
    if (!unboxedType.isPresent()) {
      code.addLine(" * @throws NullPointerException if {@code %s} is null", property.getName());
    }
    code.addLine(" */")
        .addLine("public %s %s(%s %s) {",
            datatype.getBuilder(),
            setter(property),
            unboxedType.orElse(elementType),
            property.getName());
    Block body = methodBody(code, property.getName());
    if (unboxedType.isPresent()) {
      body.addLine("  %s = %s;", property.getField(), property.getName());
    } else {
      body.addLine("  %s = %s.requireNonNull(%s);",
          property.getField(), Objects.class, property.getName());
    }
    body.addLine("  return (%s) this;", datatype.getBuilder());
    code.add(body)
        .addLine("}");
  }

  private void addOptionalSetter(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Sets the value to be returned by %s.",
            datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName())
        .addLine(" */");
    addAccessorAnnotations(code);
    code.addLine("public %s %s(%s<? extends %s> %s) {",
            datatype.getBuilder(),
            setter(property),
            optional.cls,
            elementType,
            property.getName())
        .add(methodBody(code, property.getName())
            .addLine("  if (%s.isPresent()) {", property.getName())
            .addLine("    return %s(%s.get());", setter(property), property.getName())
            .addLine("  } else {")
            .addLine("    return %s();", clearMethod(property))
            .addLine("  }"))
        .addLine("}");
  }

  private void addNullableSetter(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Sets the value to be returned by %s.",
            datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName())
        .addLine(" */")
        .addLine("public %s %s(%s %s %s) {",
            datatype.getBuilder(),
            nullableSetter(property),
            Jsr305.nullable(),
            elementType,
            property.getName())
        .add(methodBody(code, property.getName())
            .addLine("  if (%s != null) {", property.getName())
            .addLine("    return %s(%s);", setter(property), property.getName())
            .addLine("  } else {")
            .addLine("    return %s();", clearMethod(property))
            .addLine("  }"))
        .addLine("}");
  }

  private void addMapper(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * If the value to be returned by %s is present,",
            datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" * replaces it by applying {@code mapper} to it and using the result.");
    if (mapperType.canReturnNull()) {
      code.addLine(" *")
          .addLine(" * <p>If the result is null, clears the value.");
    }
    code.addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName())
        .addLine(" * @throws NullPointerException if {@code mapper} is null")
        .addLine(" */")
        .addLine("public %s %s(%s mapper) {",
            datatype.getBuilder(),
            mapper(property),
            mapperType.getFunctionalInterface());
    optional.applyMapper(code, datatype, mapperType, property);
    code.addLine("}");
  }

  private void addClear(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Sets the value to be returned by %s",
            datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" * to {@link %1$s#%2$s() Optional.%2$s()}.", optional.cls, optional.empty)
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName())
        .addLine(" */")
        .addLine("public %s %s() {", datatype.getBuilder(), clearMethod(property))
        .addLine("  %s = null;", property.getField())
        .addLine("  return (%s) this;", datatype.getBuilder())
        .addLine("}");
  }

  private void addGetter(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Returns the value that will be returned by %s.",
            datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" */")
        .addLine("public %s %s() {", property.getType(), getter(property))
        .addLine("  return %s.%s(%s);\n", optional.cls, optional.ofNullable, property.getField())
        .addLine("}");
  }

  @Override
  public void addFinalFieldAssignment(SourceBuilder code, Excerpt finalField, String builder) {
    code.addLine("%s = %s;", finalField, property.getField().on(builder));
  }

  @Override
  public void addMergeFromValue(Block code, String value) {
    String propertyValue = value + "." + property.getGetterName() + "()";
    optional.invokeIfPresent(code, propertyValue, setter(property));
  }

  @Override
  public void addMergeFromBuilder(Block code, String builder) {
    String propertyValue = builder + "." + getter(property) + "()";
    optional.invokeIfPresent(code, propertyValue, setter(property));
  }

  @Override
  public void addSetBuilderFromPartial(Block code, Variable builder) {
    code.addLine("%s.%s(%s);", builder, nullableSetter(property), property.getField());
  }

  @Override
  public void addReadValueFragment(SourceBuilder code, Excerpt finalField) {
    code.add("%s.%s(%s)", optional.cls, optional.ofNullable, finalField);
  }

  @Override
  public void addSetFromResult(SourceBuilder code, Excerpt builder, Excerpt variable) {
    code.addLine("%s.%s(%s);", builder, setter(property), variable);
  }

  @Override
  public void addClearField(Block code) {
    Optional<Excerpt> defaults = Declarations.freshBuilder(code, datatype);
    if (defaults.isPresent()) {
      code.addLine("%s = %s;", property.getField(), property.getField().on(defaults.get()));
    } else {
      code.addLine("%s = null;", property.getField());
    }
  }
}
