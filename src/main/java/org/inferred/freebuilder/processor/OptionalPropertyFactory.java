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

import static org.inferred.freebuilder.processor.BuilderMethods.checkMethod;
import static org.inferred.freebuilder.processor.BuilderMethods.clearMethod;
import static org.inferred.freebuilder.processor.BuilderMethods.getter;
import static org.inferred.freebuilder.processor.BuilderMethods.mapper;
import static org.inferred.freebuilder.processor.BuilderMethods.nullableSetter;
import static org.inferred.freebuilder.processor.BuilderMethods.setter;
import static org.inferred.freebuilder.processor.Util.erasesToAnyOf;
import static org.inferred.freebuilder.processor.Util.upperBound;
import static org.inferred.freebuilder.processor.util.ModelUtils.maybeDeclared;
import static org.inferred.freebuilder.processor.util.ModelUtils.maybeUnbox;
import static org.inferred.freebuilder.processor.util.feature.FunctionPackage.FUNCTION_PACKAGE;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.SimpleTypeVisitor6;

import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.PropertyCodeGenerator.Config;
import org.inferred.freebuilder.processor.util.ParameterizedType;
import org.inferred.freebuilder.processor.util.PreconditionExcerpts;
import org.inferred.freebuilder.processor.util.QualifiedName;
import org.inferred.freebuilder.processor.util.SourceBuilder;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 * {@link PropertyCodeGenerator.Factory} providing a default value (absent) and convenience
 * setter methods for {@link Optional} properties.
 */
public class OptionalPropertyFactory implements PropertyCodeGenerator.Factory {

  @VisibleForTesting
  static enum OptionalType {
    GUAVA(QualifiedName.of(Optional.class), "absent", "fromNullable"),
    JAVA8(QualifiedName.of("java.util", "Optional"), "empty", "ofNullable");

    private final QualifiedName cls;
    private final String empty;
    private final String ofNullable;

    private OptionalType(QualifiedName cls, String empty, String ofNullable) {
      this.cls = cls;
      this.empty = empty;
      this.ofNullable = ofNullable;
    }
  }

  @Override
  public Optional<? extends PropertyCodeGenerator> create(Config config) {
    DeclaredType type = maybeDeclared(config.getProperty().getType()).orNull();
    if (type == null) {
      return Optional.absent();
    }

    for (OptionalType optional : OptionalType.values()) {
      if (erasesToAnyOf(type, optional.cls)) {
        TypeMirror elementType = upperBound(config.getElements(), type.getTypeArguments().get(0));
        Optional<TypeMirror> unboxedType = maybeUnbox(elementType, config.getTypes());

        // Issue 29: In Java 7 and earlier, wildcards are not correctly handled when inferring the
        // type parameter of a static method (i.e. Optional.fromNullable(t)). We need to set the
        // type parameter explicitly (i.e. Optional.<T>fromNullable(t)).
        boolean requiresExplicitTypeParameters = HAS_WILDCARD.visit(elementType);

        CodeGenerator codeGenerator = new CodeGenerator(
            config.getProperty(),
            optional,
            elementType,
            unboxedType,
            requiresExplicitTypeParameters);
        return Optional.of(codeGenerator);
      }
    }
    return Optional.absent();
  }

  @VisibleForTesting static class CodeGenerator extends PropertyCodeGenerator {

    private final OptionalType optional;
    private final TypeMirror elementType;
    private final Optional<TypeMirror> unboxedType;
    private final boolean requiresExplicitTypeParameters;

    @VisibleForTesting CodeGenerator(
        Property property,
        OptionalType optional,
        TypeMirror elementType,
        Optional<TypeMirror> unboxedType,
        boolean requiresExplicitTypeParametersInJava7) {
      super(property);
      this.optional = optional;
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
      addSetter(code, metadata);
      addOptionalSetter(code, metadata);
      addNullableSetter(code, metadata);
      addMapper(code, metadata);
      addClear(code, metadata);
      addGetter(code, metadata);
      addCheck(code, metadata);
    }

    private void addSetter(SourceBuilder code, Metadata metadata) {
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
              setter(property),
              unboxedType.or(elementType),
              property.getName());
      if (unboxedType.isPresent()) {
        code.addLine("  %s(%s);", checkMethod(property), property.getName());
      } else {
        code.addLine("  %s(%s.checkNotNull(%s));",
            checkMethod(property), Preconditions.class, property.getName());
      }
      code.addLine("  this.%1$s = %1$s;", property.getName())
          .addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addOptionalSetter(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Sets the value to be returned by %s.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
          .addLine(" */");
      addAccessorAnnotations(code);
      code.addLine("public %s %s(%s<? extends %s> %s) {",
              metadata.getBuilder(),
              setter(property),
              optional.cls,
              elementType,
              property.getName())
          .addLine("  if (%s.isPresent()) {", property.getName())
          .addLine("    return %s(%s.get());", setter(property), property.getName())
          .addLine("  } else {")
          .addLine("    return %s();", clearMethod(property))
          .addLine("  }")
          .addLine("}");
    }

    private void addNullableSetter(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Sets the value to be returned by %s.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
          .addLine(" */")
          .addLine("public %s %s(@%s %s %s) {",
              metadata.getBuilder(),
              nullableSetter(property),
              javax.annotation.Nullable.class,
              elementType,
              property.getName())
          .addLine("  if (%s != null) {", property.getName())
          .addLine("    return %s(%s);", setter(property), property.getName())
          .addLine("  } else {")
          .addLine("    return %s();", clearMethod(property))
          .addLine("  }")
          .addLine("}");
    }

    private void addMapper(SourceBuilder code, Metadata metadata) {
      Optional<ParameterizedType> unaryOperator = code.feature(FUNCTION_PACKAGE).unaryOperator();
      if (unaryOperator.isPresent()) {
        code.addLine("")
            .addLine("/**")
            .addLine(" * If the value to be returned by %s is present,",
                metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
            .addLine(" * replaces it by applying {@code mapper} to it and using the result.")
            .addLine(" *")
            .addLine(" * <p>If the result is null, clears the value.")
            .addLine(" *")
            .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
            .addLine(" * @throws NullPointerException if {@code mapper} is null")
            .addLine(" */")
            .addLine("public %s %s(%s mapper) {",
                metadata.getBuilder(),
                mapper(property),
                unaryOperator.get().withParameters(elementType));
          applyMapper(code, metadata);
          code.addLine("}");
      }
    }

    private void applyMapper(SourceBuilder code, Metadata metadata) {
      switch (optional) {
      case JAVA8:
        code.addLine("  return %s(%s().map(mapper));", setter(property), getter(property));
        return;
      case GUAVA:
        code.add(PreconditionExcerpts.checkNotNull("mapper"))
            .addLine("  %s old%s = %s();",
                property.getType(), property.getCapitalizedName(), getter(property))
            .addLine("  if (old%s.isPresent()) {", property.getCapitalizedName())
            .addLine("     %s(mapper.apply(old%s.get()));",
                nullableSetter(property), property.getCapitalizedName())
            .addLine("  }")
            .addLine("  return (%s) this;", metadata.getBuilder());
        return;
      }
      throw new IllegalStateException("Unexpected optional type " + optional);
    }

    private void addClear(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Sets the value to be returned by %s",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" * to {@link %1$s#%2$s() Optional.%2$s()}.", optional.cls, optional.empty)
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
          .addLine(" */")
          .addLine("public %s %s() {", metadata.getBuilder(), clearMethod(property))
          .addLine("  this.%s = null;", property.getName())
          .addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addGetter(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Returns the value that will be returned by %s.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" */")
          .addLine("public %s %s() {", property.getType(), getter(property));
      code.add("  return %s.", optional.cls);
      if (requiresExplicitTypeParameters) {
        code.add("<%s>", elementType);
      }
      code.add("%s(%s);\n", optional.ofNullable, property.getName())
          .addLine("}");
    }

    private void addCheck(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Checks that {@code %s} can be returned from", property.getName())
          .addLine(" * %s.", metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * <p>Override this to perform argument validation, throwing an")
          .addLine(" * %s if validation fails.", IllegalArgumentException.class)
          .addLine(" */")
          .addLine("@%s(\"unused\")  // %s may be used in an overriding method",
              SuppressWarnings.class, property.getName());
      code.add("void %s(%s %s) {}\n",
          checkMethod(property), unboxedType.or(elementType), property.getName());
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
    public void addReadValueFragment(SourceBuilder code, String finalField) {
      code.add("%s.", optional.cls);
      if (requiresExplicitTypeParameters) {
        code.add("<%s>", elementType);
      }
      code.add("%s(%s)", optional.ofNullable, finalField);
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
