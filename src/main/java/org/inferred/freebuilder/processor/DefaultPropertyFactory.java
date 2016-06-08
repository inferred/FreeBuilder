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
import static org.inferred.freebuilder.processor.BuilderMethods.getter;
import static org.inferred.freebuilder.processor.BuilderMethods.mapper;
import static org.inferred.freebuilder.processor.BuilderMethods.setter;
import static org.inferred.freebuilder.processor.util.PreconditionExcerpts.checkNotNullInline;
import static org.inferred.freebuilder.processor.util.PreconditionExcerpts.checkNotNullPreamble;
import static org.inferred.freebuilder.processor.util.feature.FunctionPackage.FUNCTION_PACKAGE;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;

import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.PropertyCodeGenerator.Config;
import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.ParameterizedType;
import org.inferred.freebuilder.processor.util.PreconditionExcerpts;
import org.inferred.freebuilder.processor.util.SourceBuilder;

import javax.lang.model.type.TypeMirror;

/** Default {@link PropertyCodeGenerator.Factory}, providing reference semantics for any type. */
public class DefaultPropertyFactory implements PropertyCodeGenerator.Factory {

  @Override
  public Optional<? extends PropertyCodeGenerator> create(Config config) {
    Property property = config.getProperty();
    boolean hasDefault = config.getMethodsInvokedInBuilderConstructor().contains(setter(property));
    return Optional.of(new CodeGenerator(config.getMetadata(), property, hasDefault));
  }

  @VisibleForTesting static class CodeGenerator extends PropertyCodeGenerator {

    private final boolean hasDefault;
    private final boolean isPrimitive;

    CodeGenerator(Metadata metadata, Property property, boolean hasDefault) {
      super(metadata, property);
      this.hasDefault = hasDefault;
      this.isPrimitive = property.getType().getKind().isPrimitive();
    }

    @Override
    public Type getType() {
      return hasDefault ? Type.HAS_DEFAULT : Type.REQUIRED;
    }

    @Override
    public void addBuilderFieldDeclaration(SourceBuilder code) {
      code.addLine("private %s %s;", property.getType(), property.getName());
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
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName());
      if (!isPrimitive) {
        code.addLine(" * @throws NullPointerException if {@code %s} is null", property.getName());
      }
      code.addLine(" */");
      addAccessorAnnotations(code);
      code.addLine("public %s %s(%s %s) {",
          metadata.getBuilder(), setter(property), property.getType(), property.getName());
      if (isPrimitive) {
        code.addLine("  this.%1$s = %1$s;", property.getName());
      } else {
        code.add(checkNotNullPreamble(property.getName()))
            .addLine("  this.%s = %s;", property.getName(), checkNotNullInline(property.getName()));
      }
      if (!hasDefault) {
        code.addLine("  _unsetProperties.remove(%s.%s);",
            metadata.getPropertyEnum(), property.getAllCapsName());
      }
      if ((metadata.getBuilder() == metadata.getGeneratedBuilder())) {
        code.addLine("  return this;");
      } else {
        code.addLine("  return (%s) this;", metadata.getBuilder());
      }
      code.addLine("}");
    }

    private void addMapper(SourceBuilder code, final Metadata metadata) {
      ParameterizedType unaryOperator = code.feature(FUNCTION_PACKAGE).unaryOperator().orNull();
      if (unaryOperator == null) {
        return;
      }
      code.addLine("")
          .addLine("/**")
          .addLine(" * Replaces the value to be returned by %s",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" * by applying {@code mapper} to it and using the result.")
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
          .addLine(" * @throws NullPointerException if {@code mapper} is null"
          + " or returns null");
      if (!hasDefault) {
        code.addLine(" * @throws IllegalStateException if the field has not been set");
      }
      TypeMirror typeParam = firstNonNull(property.getBoxedType(), property.getType());
      code.addLine(" */")
          .add("public %s %s(%s mapper) {",
              metadata.getBuilder(),
              mapper(property),
              unaryOperator.withParameters(typeParam));
      if (!hasDefault) {
        code.add(PreconditionExcerpts.checkNotNull("mapper"));
      }
      code.addLine("  return %s(mapper.apply(%s()));", setter(property), getter(property))
          .addLine("}");
    }

    private void addGetter(SourceBuilder code, final Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Returns the value that will be returned by %s.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()));
      if (!hasDefault) {
        code.addLine(" *")
            .addLine(" * @throws IllegalStateException if the field has not been set");
      }
      code.addLine(" */")
          .addLine("public %s %s() {", property.getType(), getter(property));
      if (!hasDefault) {
        Excerpt propertyIsSet = new Excerpt() {
          @Override
          public void addTo(SourceBuilder source) {
            source.add("!_unsetProperties.contains(%s.%s)",
                metadata.getPropertyEnum(), property.getAllCapsName());
          }
        };
        code.add(PreconditionExcerpts.checkState(propertyIsSet, property.getName() + " not set"));
      }
      code.addLine("  return %s;", property.getName())
          .addLine("}");
    }

    @Override
    public void addValueFieldDeclaration(SourceBuilder code, String finalField) {
      code.add("private final %s %s;\n", property.getType(), finalField);
    }

    @Override
    public void addFinalFieldAssignment(SourceBuilder code, String finalField, String builder) {
      code.addLine("%s = %s.%s;", finalField, builder, property.getName());
    }

    @Override
    public void addMergeFromValue(SourceBuilder code, String value) {
      code.addLine("%s(%s.%s());", setter(property), value, property.getGetterName());
    }

    @Override
    public void addMergeFromBuilder(SourceBuilder code, String builder) {
      code.addLine("%s(%s.%s());", setter(property), builder, getter(property));
    }

    @Override
    public void addSetFromResult(SourceBuilder code, String builder, String variable) {
      code.addLine("%s.%s(%s);", builder, setter(property), variable);
    }

    @Override
    public boolean isTemplateRequiredInClear() {
      return true;
    }

    @Override
    public void addClearField(SourceBuilder code, String template) {
      code.addLine("%1$s = %2$s.%1$s;", property.getName(), template);
    }

    @Override
    public void addPartialClearField(SourceBuilder code) {
      // Cannot clear property without a template
    }
  }
}
