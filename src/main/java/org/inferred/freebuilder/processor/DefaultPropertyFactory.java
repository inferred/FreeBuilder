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

import static org.inferred.freebuilder.processor.BuilderMethods.getter;
import static org.inferred.freebuilder.processor.BuilderMethods.setter;
import static org.inferred.freebuilder.processor.util.PreconditionExcerpts.checkNotNullInline;
import static org.inferred.freebuilder.processor.util.PreconditionExcerpts.checkNotNullPreamble;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;

import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.PropertyCodeGenerator.Config;
import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.PreconditionExcerpts;
import org.inferred.freebuilder.processor.util.SourceBuilder;

import javax.lang.model.element.TypeElement;

/** Default {@link PropertyCodeGenerator.Factory}, providing reference semantics for any type. */
public class DefaultPropertyFactory implements PropertyCodeGenerator.Factory {

  @Override
  public Optional<? extends PropertyCodeGenerator> create(Config config) {
    Property property = config.getProperty();
    boolean hasDefault =
        !property.getNullableAnnotations().isEmpty()
            || config.getMethodsInvokedInBuilderConstructor().contains(setter(property));
    return Optional.of(new CodeGenerator(property, hasDefault));
  }

  @VisibleForTesting static class CodeGenerator extends PropertyCodeGenerator {

    final boolean hasDefault;
    final boolean isPrimitive;
    final boolean isNullable;

    CodeGenerator(Property property, boolean hasDefault) {
      super(property);
      this.hasDefault = hasDefault;
      this.isPrimitive = property.getType().getKind().isPrimitive();
      this.isNullable = !property.getNullableAnnotations().isEmpty();
    }

    @Override
    public Type getType() {
      return isNullable ? Type.OPTIONAL : hasDefault ? Type.HAS_DEFAULT : Type.REQUIRED;
    }

    @Override
    public void addBuilderFieldDeclaration(SourceBuilder code) {
      code.add("");
      for (TypeElement nullableAnnotation : property.getNullableAnnotations()) {
        code.add("@%s ", nullableAnnotation);
      }
      code.add("private %s %s", property.getType(), property.getName());
      if (isNullable) {
        code.add(" = null");
      }
      code.add(";\n");
    }

    @Override
    public void addBuilderFieldAccessors(SourceBuilder code, final Metadata metadata) {
      addSetter(code, metadata);
      addGetter(code, metadata);
    }

    private void addSetter(SourceBuilder code, final Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Sets the value to be returned by %s.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName());
      if (!isNullable && !isPrimitive) {
        code.addLine(" * @throws NullPointerException if {@code %s} is null", property.getName());
      }
      code.addLine(" */");
      addAccessorAnnotations(code);
      code.add("public %s %s(", metadata.getBuilder(), setter(property));
      for (TypeElement nullableAnnotation : property.getNullableAnnotations()) {
        code.add("@%s ", nullableAnnotation);
      }
      code.add("%s %s) {\n", property.getType(), property.getName());
      if (isNullable || isPrimitive) {
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

    private void addGetter(SourceBuilder code, final Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Returns the value that will be returned by %s.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()));
      if (!hasDefault) {
        code.addLine(" *")
            .addLine(" * @throws IllegalStateException if the field has not been set");
      }
      code.addLine(" */");
      for (TypeElement nullableAnnotation : property.getNullableAnnotations()) {
        code.addLine("@%s", nullableAnnotation);
      }
      code.addLine("public %s %s() {", property.getType(), getter(property));
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
      for (TypeElement nullableAnnotation : property.getNullableAnnotations()) {
        code.add("@%s ", nullableAnnotation);
      }
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
    public void addMergeFromBuilder(SourceBuilder code, Metadata metadata, String builder) {
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
    public void addClear(SourceBuilder code, String template) {
      code.addLine("%1$s = %2$s.%1$s;", property.getName(), template);
    }

    @Override
    public void addPartialClear(SourceBuilder code) {
      if (isNullable) {
        code.addLine("%s = null;", property.getName());
      }
    }
  }
}
