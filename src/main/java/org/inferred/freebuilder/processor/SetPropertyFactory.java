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

import static org.inferred.freebuilder.processor.BuilderMethods.addAllMethod;
import static org.inferred.freebuilder.processor.BuilderMethods.addMethod;
import static org.inferred.freebuilder.processor.BuilderMethods.clearMethod;
import static org.inferred.freebuilder.processor.BuilderMethods.getter;
import static org.inferred.freebuilder.processor.BuilderMethods.removeMethod;
import static org.inferred.freebuilder.processor.Util.erasesToAnyOf;
import static org.inferred.freebuilder.processor.Util.upperBound;
import static org.inferred.freebuilder.processor.util.PreconditionExcerpts.checkNotNullInline;
import static org.inferred.freebuilder.processor.util.PreconditionExcerpts.checkNotNullPreamble;
import static org.inferred.freebuilder.processor.util.feature.GuavaLibrary.GUAVA;
import static org.inferred.freebuilder.processor.util.feature.SourceLevel.SOURCE_LEVEL;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;

import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.PropertyCodeGenerator.Config;
import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.SourceBuilder;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * {@link PropertyCodeGenerator.Factory} providing append-only semantics for {@link Set}
 * properties.
 */
public class SetPropertyFactory implements PropertyCodeGenerator.Factory {

  @Override
  public Optional<CodeGenerator> create(Config config) {
    if (config.getProperty().getType().getKind() == TypeKind.DECLARED) {
      DeclaredType type = (DeclaredType) config.getProperty().getType();
      if (erasesToAnyOf(type, Set.class, ImmutableSet.class)) {
        TypeMirror elementType = upperBound(config.getElements(), type.getTypeArguments().get(0));
        Optional<TypeMirror> unboxedType;
        try {
          unboxedType = Optional.<TypeMirror>of(config.getTypes().unboxedType(elementType));
        } catch (IllegalArgumentException e) {
          unboxedType = Optional.absent();
        }
        return Optional.of(new CodeGenerator(config.getProperty(), elementType, unboxedType));
      }
    }
    return Optional.absent();
  }

  @VisibleForTesting
  static class CodeGenerator extends PropertyCodeGenerator {

    private final TypeMirror elementType;
    private final Optional<TypeMirror> unboxedType;

    CodeGenerator(Property property, TypeMirror elementType, Optional<TypeMirror> unboxedType) {
      super(property);
      this.elementType = elementType;
      this.unboxedType = unboxedType;
    }

    @Override
    public void addBuilderFieldDeclaration(SourceBuilder code) {
      code.addLine("private final %1$s<%2$s> %3$s = new %1$s<%4$s>();",
          LinkedHashSet.class,
          elementType,
          property.getName(),
          code.feature(SOURCE_LEVEL).supportsDiamondOperator() ? "" : elementType);
    }

    @Override
    public void addBuilderFieldAccessors(SourceBuilder code, Metadata metadata) {
      addAdd(code, metadata);
      addVarargsAdd(code, metadata);
      addAddAll(code, metadata);
      addRemove(code, metadata);
      addClear(code, metadata);
      addGetter(code, metadata);
    }

    private void addAdd(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Adds {@code element} to the set to be returned from %s.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" * If the set already contains {@code element}, then {@code %s}",
              addMethod(property))
          .addLine(" * has no effect (only the previously added element is retained).")
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName());
      if (!unboxedType.isPresent()) {
        code.addLine(" * @throws NullPointerException if {@code element} is null");
      }
      code.addLine(" */")
          .addLine("public %s %s(%s element) {",
              metadata.getBuilder(),
              addMethod(property),
              unboxedType.or(elementType));
      if (unboxedType.isPresent()) {
        code.addLine("  this.%s.add(element);", property.getName());
      } else {
        code.add(checkNotNullPreamble("element"))
            .addLine("  this.%s.add(%s);", property.getName(), checkNotNullInline("element"));
      }
      code.addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addVarargsAdd(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Adds each element of {@code elements} to the set to be returned from")
          .addLine(" * %s, ignoring duplicate elements",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" * (only the first duplicate element is added).")
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName());
      if (!unboxedType.isPresent()) {
        code.addLine(" * @throws NullPointerException if {@code elements} is null or contains a")
            .addLine(" *     null element");
      }
      code.addLine(" */")
          .addLine("public %s %s(%s... elements) {",
              metadata.getBuilder(),
              addMethod(property),
              unboxedType.or(elementType))
          .addLine("  for (%s element : elements) {", unboxedType.or(elementType))
          .addLine("    %s(element);", addMethod(property))
          .addLine("  }")
          .addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addAddAll(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Adds each element of {@code elements} to the set to be returned from")
          .addLine(" * %s, ignoring duplicate elements",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" * (only the first duplicate element is added).")
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
          .addLine(" * @throws NullPointerException if {@code elements} is null or contains a")
          .addLine(" *     null element")
          .addLine(" */");
      addAccessorAnnotations(code);
      code.addLine("public %s %s(%s<? extends %s> elements) {",
              metadata.getBuilder(),
              addAllMethod(property),
              Iterable.class,
              elementType)
          .addLine("  for (%s element : elements) {", unboxedType.or(elementType))
          .addLine("    %s(element);", addMethod(property))
          .addLine("  }")
          .addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addRemove(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Removes {@code element} from the set to be returned from %s.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" * Does nothing if {@code element} is not a member of the set.")
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName());
      if (!unboxedType.isPresent()) {
        code.addLine(" * @throws NullPointerException if {@code element} is null");
      }
      code.addLine(" */")
          .addLine("public %s %s(%s element) {",
              metadata.getBuilder(),
              removeMethod(property),
              unboxedType.or(elementType));
      if (unboxedType.isPresent()) {
        code.addLine("  this.%s.remove(element);", property.getName());
      } else {
        code.add(checkNotNullPreamble("element"))
            .addLine("  this.%s.remove(%s);", property.getName(), checkNotNullInline("element"));
      }
      code.addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addClear(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Clears the set to be returned from %s.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
          .addLine(" */")
          .addLine("public %s %s() {", metadata.getBuilder(), clearMethod(property))
          .addLine("  %s.clear();", property.getName())
          .addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addGetter(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Returns an unmodifiable view of the set that will be returned by")
          .addLine(" * %s.", metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" * Changes to this builder will be reflected in the view.")
          .addLine(" */")
          .addLine("public %s<%s> %s() {", Set.class, elementType, getter(property))
          .addLine("  return %s.unmodifiableSet(%s);", Collections.class, property.getName())
          .addLine("}");
    }

    @Override
    public void addFinalFieldAssignment(SourceBuilder code, String finalField, String builder) {
      code.add("%s = ", finalField);
      if (code.feature(GUAVA).isAvailable()) {
        code.add("%s.copyOf", ImmutableSet.class);
      } else {
        code.add("immutableSet");
      }
      code.add("(%s.%s);\n", builder, property.getName());
    }

    @Override
    public void addMergeFromValue(SourceBuilder code, String value) {
      code.addLine("%s(%s.%s());", addAllMethod(property), value, property.getGetterName());
    }

    @Override
    public void addMergeFromBuilder(SourceBuilder code, Metadata metadata, String builder) {
      code.addLine("%s(((%s) %s).%s);",
          addAllMethod(property),
          metadata.getGeneratedBuilder(),
          builder,
          property.getName());
    }

    @Override
    public void addSetFromResult(SourceBuilder code, String builder, String variable) {
      code.addLine("%s.%s(%s);", builder, addAllMethod(property), variable);
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

    @Override
    public Set<StaticMethod> getStaticMethods() {
      return ImmutableSet.copyOf(StaticMethod.values());
    }
  }

  private enum StaticMethod implements Excerpt {
    IMMUTABLE_SET {
      @Override
      public void addTo(SourceBuilder code) {
        if (!code.feature(GUAVA).isAvailable()) {
          code.addLine("")
              .addLine("private static <E> %1$s<E> immutableSet(%1$s<E> elements) {",
                  Set.class, Class.class)
              .addLine("  switch (elements.size()) {")
              .addLine("  case 0:")
              .addLine("    return %s.emptySet();", Collections.class)
              .addLine("  case 1:")
              .addLine("    return %s.singleton(elements.iterator().next());", Collections.class)
              .addLine("  default:")
              .add("    return %s.unmodifiableSet(new %s<", Collections.class, LinkedHashSet.class);
          if (!code.feature(SOURCE_LEVEL).supportsDiamondOperator()) {
            code.add("E");
          }
          code.add(">(elements));\n")
              .addLine("  }")
              .addLine("}");
        }
      }
    }
  }
}
