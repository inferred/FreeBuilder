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

import static org.inferred.freebuilder.processor.Util.erasesToAnyOf;
import static org.inferred.freebuilder.processor.Util.upperBound;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.SimpleTypeVisitor6;

import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.PropertyCodeGenerator.Config;
import org.inferred.freebuilder.processor.util.SourceBuilder;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 * {@link PropertyCodeGenerator.Factory} providing a default value (absent) and convenience
 * setter methods for {@link Optional} properties.
 */
public class OptionalPropertyFactory implements PropertyCodeGenerator.Factory {

  private static final String SET_PREFIX = "set";
  private static final String NULLABLE_SET_PREFIX = "setNullable";
  private static final String CLEAR_PREFIX = "clear";

  @Override
  public Optional<? extends PropertyCodeGenerator> create(Config config) {
    // No @Nullable properties
    if (!config.getProperty().getNullableAnnotations().isEmpty()) {
      return Optional.absent();
    }

    if (config.getProperty().getType().getKind() == TypeKind.DECLARED) {
      DeclaredType type = (DeclaredType) config.getProperty().getType();
      if (erasesToAnyOf(type, Optional.class)) {
        TypeMirror elementType = upperBound(config.getElements(), type.getTypeArguments().get(0));
        Optional<TypeMirror> unboxedType;
        try {
          unboxedType = Optional.<TypeMirror>of(config.getTypes().unboxedType(elementType));
        } catch (IllegalArgumentException e) {
          unboxedType = Optional.absent();
        }
        String capitalizedName = config.getProperty().getCapitalizedName();
        String setterName = SET_PREFIX + capitalizedName;
        String nullableSetterName = NULLABLE_SET_PREFIX + capitalizedName;
        String clearName = CLEAR_PREFIX + capitalizedName;

        // Issue 29: In Java 7 and earlier, wildcards are not correctly handled when inferring the
        // type parameter of a static method (like Optional.fromNullable(t), in this case). We need
        // to set the type parameter explicitly (i.e. Optional.<T>fromNullable(t)).
        boolean requiresExplicitTypeParameters = HAS_WILDCARD.visit(elementType);

        return Optional.of(new CodeGenerator(
            config.getProperty(),
            setterName,
            nullableSetterName,
            clearName,
            elementType,
            unboxedType,
            requiresExplicitTypeParameters));
      }
    }
    return Optional.absent();
  }

  @VisibleForTesting static class CodeGenerator extends PropertyCodeGenerator {

    private final String setterName;
    private final String nullableSetterName;
    private final String clearName;
    private final TypeMirror elementType;
    private final Optional<TypeMirror> unboxedType;
    private final boolean requiresExplicitTypeParameters;

    @VisibleForTesting CodeGenerator(
        Property property,
        String setterName,
        String nullableSetterName,
        String clearName,
        TypeMirror elementType,
        Optional<TypeMirror> unboxedType,
        boolean requiresExplicitTypeParametersInJava7) {
      super(property);
      this.setterName = setterName;
      this.nullableSetterName = nullableSetterName;
      this.clearName = clearName;
      this.elementType = elementType;
      this.unboxedType = unboxedType;
      this.requiresExplicitTypeParameters = requiresExplicitTypeParametersInJava7;
    }

    @Override
    public Type getType() {
      return Type.OPTIONAL;
    }

    @Override
    public void addValueFieldDeclaration(SourceBuilder code, String finalField) {
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
          .addLine("private %s %s = null;", elementType, property.getName());
    }

    @Override
    public void addBuilderFieldAccessors(SourceBuilder code, Metadata metadata) {
      // Setter (T, not nullable)
      code.addLine("")
          .addLine("/**")
          .addLine(" * Sets the value to be returned by %s.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName());
      if (!unboxedType.isPresent()) {
        code.addLine(" * @throws NullPointerException if {@code %s} is null", property.getName());
      }
      code.addLine(" */")
          .addLine("public %s %s(%s %s) {",
              metadata.getBuilder(),
              setterName,
              unboxedType.or(elementType),
              property.getName());
      if (unboxedType.isPresent()) {
        code.addLine("  this.%1$s = %1$s;", property.getName());
      } else {
        code.addLine("  this.%1$s = %2$s.checkNotNull(%1$s);",
            property.getName(), Preconditions.class);
      }
      code.addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");

      // Setter (Optional<? extends T>)
      code.addLine("")
          .addLine("/**")
          .addLine(" * Sets the value to be returned by %s.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
          .addLine(" */")
          .addLine("public %s %s(%s<? extends %s> %s) {",
              metadata.getBuilder(),
              setterName,
              Optional.class,
              elementType,
              property.getName())
          .addLine("  if (%s.isPresent()) {", property.getName())
          .addLine("    return %s(%s.get());", setterName, property.getName())
          .addLine("  } else {")
          .addLine("    return %s();", clearName)
          .addLine("  }")
          .addLine("}");

      // Setter (nullable T)
      code.addLine("")
          .addLine("/**")
          .addLine(" * Sets the value to be returned by %s.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
          .addLine(" */")
          .addLine("public %s %s(@%s %s %s) {",
              metadata.getBuilder(),
              nullableSetterName,
              javax.annotation.Nullable.class,
              elementType,
              property.getName())
          .addLine("  if (%s != null) {", property.getName())
          .addLine("    return %s(%s);", setterName, property.getName())
          .addLine("  } else {")
          .addLine("    return %s();", clearName)
          .addLine("  }")
          .addLine("}");

      // Clear
      code.addLine("")
          .addLine("/**")
          .addLine(" * Sets the value to be returned by %s",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" * to {@link %s#absent() Optional.absent()}.", Optional.class)
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
          .addLine(" */")
          .addLine("public %s %s() {", metadata.getBuilder(), clearName)
          .addLine("  this.%s = null;", property.getName())
          .addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");

      // Getter
      code.addLine("")
          .addLine("/**")
          .addLine(" * Returns the value that will be returned by %s.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" */")
          .addLine("public %s %s() {", property.getType(), property.getGetterName());
      code.add("  return %s.", Optional.class);
      if (requiresExplicitTypeParameters) {
        code.add("<%s>", elementType);
      }
      code.add("fromNullable(%s);\n", property.getName())
          .addLine("}");
    }

    @Override
    public void addFinalFieldAssignment(SourceBuilder code, String finalField, String builder) {
      code.addLine("%s = %s.%s;", finalField, builder, property.getName());
    }

    @Override
    public void addMergeFromValue(SourceBuilder code, String value) {
      code.addLine("%s(%s.%s());", setterName, value, property.getGetterName());
    }

    @Override
    public void addMergeFromBuilder(SourceBuilder code, Metadata metadata, String builder) {
      code.addLine("%s(%s.%s());", setterName, builder, property.getGetterName());
    }

    @Override
    public void addReadValueFragment(SourceBuilder code, String finalField) {
      code.add("%s.", Optional.class);
      if (requiresExplicitTypeParameters) {
        code.add("<%s>", elementType);
      }
      code.add("fromNullable(%s)", finalField);
    }

    @Override
    public void addSetFromResult(SourceBuilder code, String builder, String variable) {
      code.addLine("%s.%s(%s);", builder, setterName, variable);
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
      code.addLine("%s = null;", property.getName());
    }
  }

  private static final SimpleTypeVisitor6<Boolean, Void> HAS_WILDCARD =
      new SimpleTypeVisitor6<Boolean, Void>() {
        @Override protected Boolean defaultAction(TypeMirror e, Void p) {
          return false;
        }

        @Override
        public Boolean visitDeclared(DeclaredType t, Void p) {
          for (TypeMirror typeArgument : t.getTypeArguments()) {
            if (visit(typeArgument)) {
              return true;
            }
          }
          return false;
        }

        @Override
        public Boolean visitWildcard(WildcardType t, Void p) {
          return true;
        }
      };
}
