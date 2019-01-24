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
package org.inferred.freebuilder.processor.property;

import static org.inferred.freebuilder.processor.BuilderMethods.getter;
import static org.inferred.freebuilder.processor.BuilderMethods.mapper;
import static org.inferred.freebuilder.processor.BuilderMethods.setter;
import static org.inferred.freebuilder.processor.util.FunctionalType.functionalTypeAcceptedByMethod;
import static org.inferred.freebuilder.processor.util.FunctionalType.unboxedUnaryOperator;

import org.inferred.freebuilder.processor.Datatype;
import org.inferred.freebuilder.processor.Declarations;
import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.Excerpts;
import org.inferred.freebuilder.processor.util.FieldAccess;
import org.inferred.freebuilder.processor.util.FunctionalType;
import org.inferred.freebuilder.processor.util.ObjectsExcerpts;
import org.inferred.freebuilder.processor.util.PreconditionExcerpts;
import org.inferred.freebuilder.processor.util.SourceBuilder;
import org.inferred.freebuilder.processor.util.Variable;

import java.util.Objects;
import java.util.Optional;

import javax.lang.model.type.TypeKind;

/** Default {@link PropertyCodeGenerator}, providing reference semantics for any type. */
public class DefaultProperty extends PropertyCodeGenerator {

  static class Factory implements PropertyCodeGenerator.Factory {

    @Override
    public Optional<DefaultProperty> create(Config config) {
      Property property = config.getProperty();
      boolean hasDefault = config.getMethodsInvokedInBuilderConstructor()
          .contains(setter(property));
      FunctionalType mapperType = functionalTypeAcceptedByMethod(
          config.getBuilder(),
          mapper(property),
          unboxedUnaryOperator(property.getType(), config.getTypes()),
          config.getElements(),
          config.getTypes());
      return Optional.of(new DefaultProperty(
          config.getDatatype(), property, hasDefault, mapperType));
    }
  }

  public static final FieldAccess UNSET_PROPERTIES = new FieldAccess("_unsetProperties");

  private final boolean hasDefault;
  private final FunctionalType mapperType;
  private final TypeKind kind;

  DefaultProperty(
      Datatype datatype,
      Property property,
      boolean hasDefault,
      FunctionalType mapperType) {
    super(datatype, property);
    this.hasDefault = hasDefault;
    this.mapperType = mapperType;
    this.kind = property.getType().getKind();
  }

  @Override
  public Initially initialState() {
    return hasDefault ? Initially.HAS_DEFAULT : Initially.REQUIRED;
  }

  @Override
  public void addBuilderFieldDeclaration(SourceBuilder code) {
    code.addLine("private %s %s;", property.getType(), property.getField());
  }

  @Override
  public void addBuilderFieldAccessors(SourceBuilder code) {
    addSetter(code);
    addMapper(code);
    addGetter(code);
  }

  private void addSetter(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Sets the value to be returned by %s.",
            datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName());
    if (!kind.isPrimitive()) {
      code.addLine(" * @throws NullPointerException if {@code %s} is null", property.getName());
    }
    code.addLine(" */");
    addAccessorAnnotations(code);
    code.addLine("public %s %s(%s %s) {",
        datatype.getBuilder(), setter(property), property.getType(), property.getName());
    if (kind.isPrimitive()) {
      code.addLine("  %s = %s;", property.getField(), property.getName());
    } else {
      code.addLine("  %s = %s.requireNonNull(%s);",
          property.getField(), Objects.class, property.getName());
    }
    if (!hasDefault) {
      code.addLine("  %s.remove(%s.%s);",
          UNSET_PROPERTIES, datatype.getPropertyEnum(), property.getAllCapsName());
    }
    if ((datatype.getBuilder() == datatype.getGeneratedBuilder())) {
      code.addLine("  return this;");
    } else {
      code.addLine("  return (%s) this;", datatype.getBuilder());
    }
    code.addLine("}");
  }

  private void addMapper(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Replaces the value to be returned by %s",
            datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" * by applying {@code mapper} to it and using the result.")
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName())
        .addLine(" * @throws NullPointerException if {@code mapper} is null");
    if (mapperType.canReturnNull()) {
      code.addLine(" * or returns null");
    }
    if (!hasDefault) {
      code.addLine(" * @throws IllegalStateException if the field has not been set");
    }
    code.addLine(" */")
        .add("public %s %s(%s mapper) {",
            datatype.getBuilder(),
            mapper(property),
            mapperType.getFunctionalInterface());
    if (!hasDefault) {
      code.addLine("  %s.requireNonNull(mapper);", Objects.class);
    }
    code.addLine("  return %s(mapper.%s(%s()));",
            setter(property), mapperType.getMethodName(), getter(property))
        .addLine("}");
  }

  private void addGetter(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Returns the value that will be returned by %s.",
            datatype.getType().javadocNoArgMethodLink(property.getGetterName()));
    if (!hasDefault) {
      code.addLine(" *")
          .addLine(" * @throws IllegalStateException if the field has not been set");
    }
    code.addLine(" */")
        .addLine("public %s %s() {", property.getType(), getter(property));
    if (!hasDefault) {
      code.add(PreconditionExcerpts.checkState(
          "!%s.contains(%s.%s)",
          property.getName() + " not set",
          UNSET_PROPERTIES,
          datatype.getPropertyEnum(),
          property.getAllCapsName()));
    }
    code.addLine("  return %s;", property.getField())
        .addLine("}");
  }

  @Override
  public void addValueFieldDeclaration(SourceBuilder code, FieldAccess finalField) {
    code.add("private final %s %s;\n", property.getType(), finalField);
  }

  @Override
  public void addFinalFieldAssignment(SourceBuilder code, Excerpt finalField, String builder) {
    code.addLine("%s = %s;", finalField, property.getField().on(builder));
  }

  @Override
  public void addMergeFromValue(SourceBuilder code, String value) {
    Excerpt defaults = Declarations.freshBuilder(code, datatype).orElse(null);
    if (defaults != null) {
      code.add("if (");
      if (!hasDefault) {
        code.add("%s.contains(%s.%s) || ",
            UNSET_PROPERTIES.on(defaults), datatype.getPropertyEnum(), property.getAllCapsName());
      }
      code.add(ObjectsExcerpts.notEquals(
          Excerpts.add("%s.%s()", value, property.getGetterName()),
          Excerpts.add("%s.%s()", defaults, getter(property)),
          kind));
      code.add(") {%n");
    }
    code.addLine("  %s(%s.%s());", setter(property), value, property.getGetterName());
    if (defaults != null) {
      code.addLine("}");
    }
  }

  @Override
  public void addMergeFromBuilder(SourceBuilder code, String builder) {
    Excerpt base =
        hasDefault ? null : Declarations.upcastToGeneratedBuilder(code, datatype, builder);
    Excerpt defaults = Declarations.freshBuilder(code, datatype).orElse(null);
    if (defaults != null) {
      code.add("if (");
      if (!hasDefault) {
        code.add("!%s.contains(%s.%s) && ",
                UNSET_PROPERTIES.on(base), datatype.getPropertyEnum(), property.getAllCapsName())
            .add("(%s.contains(%s.%s) ||",
                UNSET_PROPERTIES.on(defaults),
                datatype.getPropertyEnum(),
                property.getAllCapsName());
      }
      code.add(ObjectsExcerpts.notEquals(
          Excerpts.add("%s.%s()", builder, getter(property)),
          Excerpts.add("%s.%s()", defaults, getter(property)),
          kind));
      if (!hasDefault) {
        code.add(")");
      }
      code.add(") {%n");
    } else if (!hasDefault) {
      code.addLine("if (!%s.contains(%s.%s)) {",
          UNSET_PROPERTIES.on(base), datatype.getPropertyEnum(), property.getAllCapsName());
    }
    code.addLine("  %s(%s.%s());", setter(property), builder, getter(property));
    if (defaults != null || !hasDefault) {
      code.addLine("}");
    }
  }

  @Override
  public void addSetBuilderFromPartial(SourceBuilder code, Variable builder) {
    if (!hasDefault) {
      code.add("if (!%s.contains(%s.%s)) {",
          UNSET_PROPERTIES, datatype.getPropertyEnum(), property.getAllCapsName());
    }
    code.addLine("  %s.%s(%s);", builder, setter(property), property.getField());
    if (!hasDefault) {
      code.addLine("}");
    }
  }

  @Override
  public void addSetFromResult(SourceBuilder code, Excerpt builder, Excerpt variable) {
    code.addLine("%s.%s(%s);", builder, setter(property), variable);
  }

  @Override
  public void addClearField(SourceBuilder code) {
    Optional<Variable> defaults = Declarations.freshBuilder(code, datatype);
    // Cannot clear property without defaults
    if (defaults.isPresent()) {
      code.addLine("%s = %s;", property.getField(), property.getField().on(defaults.get()));
    }
  }
}
