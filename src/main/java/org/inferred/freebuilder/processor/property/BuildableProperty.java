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

import static org.inferred.freebuilder.processor.BuildableType.maybeBuilder;
import static org.inferred.freebuilder.processor.BuilderFactory.TypeInference.EXPLICIT_TYPES;
import static org.inferred.freebuilder.processor.BuilderMethods.getBuilderMethod;
import static org.inferred.freebuilder.processor.BuilderMethods.mutator;
import static org.inferred.freebuilder.processor.BuilderMethods.setter;
import static org.inferred.freebuilder.processor.model.ModelUtils.maybeAsTypeElement;
import static org.inferred.freebuilder.processor.model.ModelUtils.maybeDeclared;
import static org.inferred.freebuilder.processor.source.FunctionalType.consumer;
import static org.inferred.freebuilder.processor.source.FunctionalType.functionalTypeAcceptedByMethod;

import org.inferred.freebuilder.processor.BuildableType;
import org.inferred.freebuilder.processor.BuildableType.MergeBuilderMethod;
import org.inferred.freebuilder.processor.BuildableType.PartialToBuilderMethod;
import org.inferred.freebuilder.processor.Datatype;
import org.inferred.freebuilder.processor.Declarations;
import org.inferred.freebuilder.processor.source.Excerpt;
import org.inferred.freebuilder.processor.source.Excerpts;
import org.inferred.freebuilder.processor.source.FunctionalType;
import org.inferred.freebuilder.processor.source.SourceBuilder;
import org.inferred.freebuilder.processor.source.Variable;

import java.util.Objects;
import java.util.Optional;

import javax.lang.model.type.DeclaredType;

/**
 * {@link PropertyCodeGenerator} providing fluent methods for fields of a {@link BuildableType}.
 */
class BuildableProperty extends PropertyCodeGenerator {

  static class Factory implements PropertyCodeGenerator.Factory {

    @Override
    public Optional<BuildableProperty> create(Config config) {
      DeclaredType datatype = maybeDeclared(config.getProperty().getType()).orElse(null);
      if (datatype == null) {
        return Optional.empty();
      }

      DeclaredType builder = maybeBuilder(datatype, config.getElements(), config.getTypes())
          .orElse(null);
      if (builder == null) {
        return Optional.empty();
      }

      BuildableType type = BuildableType.create(
          datatype, builder, config.getElements(), config.getTypes());

      // Find any mutate method override
      FunctionalType mutatorType = functionalTypeAcceptedByMethod(
          config.getBuilder(),
          mutator(config.getProperty()),
          consumer(builder),
          config.getElements(),
          config.getTypes());

      return Optional.of(new BuildableProperty(
          config.getDatatype(), config.getProperty(), type, mutatorType));
    }
  }

  private final BuildableType type;
  private final FunctionalType mutatorType;

  private BuildableProperty(
      Datatype datatype,
      Property property,
      BuildableType type,
      FunctionalType mutatorType) {
    super(datatype, property);
    this.type = type;
    this.mutatorType = mutatorType;
  }

  @Override
  public void addBuilderFieldDeclaration(SourceBuilder code) {
    code.addLine("private Object %s = null;", property.getField());
  }

  @Override
  public void addBuilderFieldAccessors(SourceBuilder code) {
    addSetter(code);
    addSetterTakingBuilder(code);
    addMutate(code);
    addGetter(code);
  }

  private void addSetter(SourceBuilder code) {
    Variable builder = new Variable("builder");
    code.addLine("")
        .addLine("/**")
        .addLine(" * Sets the value to be returned by %s.",
            datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName())
        .addLine(" * @throws NullPointerException if {@code %s} is null", property.getName())
        .addLine(" */");
    addAccessorAnnotations(code);
    code.addLine("public %s %s(%s %s) {",
            datatype.getBuilder(),
            setter(property),
            property.getType(),
            property.getName())
        .addLine("  %s.requireNonNull(%s);", Objects.class, property.getName())
        .addLine("  if (%1$s == null || %1$s instanceof %2$s) {",
            property.getField(), maybeAsTypeElement(property.getType()).get())
        .addLine("    %s = %s;", property.getField(), property.getName())
        .addLine("  } else {")
        .addLine("    %1$s %2$s %3$s = (%2$s) %4$s;",
            type.suppressUnchecked(), type.builderType(), builder, property.getField())
        .addLine("    %s.clear();", builder)
        .addLine("    %s.mergeFrom(%s);", builder, property.getName())
        .addLine("  }")
        .addLine("  return (%s) this;", datatype.getBuilder())
        .addLine("}");
  }

  private void addSetterTakingBuilder(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Sets the value to be returned by %s.",
            datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName())
        .addLine(" * @throws NullPointerException if {@code builder} is null")
        .addLine(" */")
        .addLine("public %s %s(%s builder) {",
            datatype.getBuilder(),
            setter(property),
            type.builderType())
        .addLine("  return %s(builder.build());", setter(property))
        .addLine("}");
  }

  private void addMutate(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Applies {@code mutator} to the builder for the value that will be")
        .addLine(" * returned by %s.",
            datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * <p>This method mutates the builder in-place. {@code mutator} is a void")
        .addLine(" * consumer, so any value returned from a lambda will be ignored.")
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName())
        .addLine(" * @throws NullPointerException if {@code mutator} is null")
        .addLine(" */")
        .addLine("public %s %s(%s mutator) {",
            datatype.getBuilder(),
            mutator(property),
            mutatorType.getFunctionalInterface())
        .addLine("  mutator.%s(%s());", mutatorType.getMethodName(), getBuilderMethod(property))
        .addLine("  return (%s) this;", datatype.getBuilder())
        .addLine("}");
  }

  private void addGetter(SourceBuilder code) {
    Variable builder = new Variable("builder");
    Variable value = new Variable("value");
    code.addLine("")
        .addLine("/**")
        .addLine(" * Returns a builder for the value that will be returned by %s.",
            datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" */")
        .addLine("public %s %s() {", type.builderType(), getBuilderMethod(property))
        .addLine("  if (%s == null) {", property.getField())
        .addLine("    %s = %s;", property.getField(), type.newBuilder(EXPLICIT_TYPES))
        .addLine("  } else if (%s instanceof %s) {",
            property.getField(), maybeAsTypeElement(property.getType()).get())
        .addLine("    %1$s %2$s %3$s = (%2$s) %4$s;",
            type.suppressUnchecked(), property.getType(), value, property.getField());
    if (type.partialToBuilder() == PartialToBuilderMethod.TO_BUILDER_AND_MERGE) {
      code.addLine("    %s = %s.toBuilder();", property.getField(), value);
    } else {
      code.addLine("    %s = %s", property.getField(), type.newBuilder(EXPLICIT_TYPES))
          .addLine("        .mergeFrom(%s);", value);
    }
    code.addLine("  }")
        .addLine("  %1$s %2$s %3$s = (%2$s) %4$s;",
            type.suppressUnchecked(), type.builderType(), builder, property.getField())
        .addLine("  return %s;", builder)
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
        .addLine("  %s = %s.%s();", finalField, type.newBuilder(EXPLICIT_TYPES), buildMethod)
        .addLine("} else if (%s instanceof %s) {",
            property.getField().on(builder), maybeAsTypeElement(property.getType()).get());
    if (type.suppressUnchecked() != Excerpts.EMPTY) {
      code.addLine("  %1$s %2$s %3$s = (%2$s) %4$s;",
              type.suppressUnchecked(),
              property.getType(),
              fieldValue,
              property.getField().on(builder))
          .addLine("  %s = %s;", finalField, fieldValue);
    } else {
      code.addLine("  %s = (%s) %s;",
              finalField, property.getType(), property.getField().on(builder));
    }
    code.addLine("} else {")
        .addLine("  %1$s %2$s %3$s = (%2$s) %4$s;",
            type.suppressUnchecked(),
            type.builderType(),
            fieldBuilder,
            property.getField().on(builder))
        .addLine("  %s = %s.%s();", finalField, fieldBuilder, buildMethod)
        .addLine("}");
  }

  @Override
  public void addAssignToBuilder(SourceBuilder code, Variable builder) {
    if (type.partialToBuilder() == PartialToBuilderMethod.TO_BUILDER_AND_MERGE) {
      code.add("%s = %s.toBuilder();",
          property.getField().on(builder), property.getField());
    } else {
      code.add("%s = %s.mergeFrom(%s);",
          property.getField().on(builder), type.newBuilder(EXPLICIT_TYPES), property.getField());
    }
  }

  @Override
  public void addMergeFromValue(SourceBuilder code, String value) {
    code.addLine("if (%s == null) {", property.getField())
        .addLine("  %s = %s.%s();", property.getField(), value, property.getGetterName())
        .addLine("} else {")
        .addLine("  %s().mergeFrom(%s.%s());",
            getBuilderMethod(property), value, property.getGetterName())
        .addLine("}");
  }

  @Override
  public void addMergeFromBuilder(SourceBuilder code, String builder) {
    Excerpt base = Declarations.upcastToGeneratedBuilder(code, datatype, builder);
    Variable fieldValue = new Variable(property.getName() + "Value");
    code.addLine("if (%s == null) {", property.getField().on(base))
        .addLine("  // Nothing to merge")
        .addLine("} else if (%s instanceof %s) {",
            property.getField().on(base), maybeAsTypeElement(property.getType()).get())
        .addLine("  %1$s %2$s %3$s = (%2$s) %4$s;",
           type.suppressUnchecked(), property.getType(), fieldValue, property.getField().on(base))
        .addLine("  if (%s == null) {", property.getField())
        .addLine("    %s = %s;", property.getField(), fieldValue)
        .addLine("  } else {")
        .addLine("    %s().mergeFrom(%s);", getBuilderMethod(property), fieldValue)
        .addLine("  }")
        .addLine("} else {")
        .add("  %s().mergeFrom(%s.%s()",
            getBuilderMethod(property), base, getBuilderMethod(property));
    if (type.mergeBuilder() == MergeBuilderMethod.BUILD_PARTIAL_AND_MERGE) {
      code.add(".buildPartial()");
    }
    code.add(");\n")
        .addLine("}");
  }

  @Override
  public void addSetFromResult(SourceBuilder code, Excerpt builder, Excerpt variable) {
    code.addLine("%s.%s(%s);", builder, setter(property), variable);
  }

  @Override
  public void addClearField(SourceBuilder code) {
    Variable fieldBuilder = new Variable(property.getName() + "Builder");
    code.addLine("  if (%1$s == null || %1$s instanceof %2$s) {",
            property.getField(), maybeAsTypeElement(property.getType()).get())
        .addLine("    %s = null;", property.getField())
        .addLine("  } else {")
        .addLine("    %1$s %2$s %3$s = (%2$s) %4$s;",
            type.suppressUnchecked(), type.builderType(), fieldBuilder, property.getField())
        .addLine("    %s.clear();", fieldBuilder)
        .addLine("  }");
  }
}
