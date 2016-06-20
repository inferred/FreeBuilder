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
import static org.inferred.freebuilder.processor.BuilderMethods.addCopiesMethod;
import static org.inferred.freebuilder.processor.BuilderMethods.addMethod;
import static org.inferred.freebuilder.processor.BuilderMethods.clearMethod;
import static org.inferred.freebuilder.processor.BuilderMethods.getter;
import static org.inferred.freebuilder.processor.BuilderMethods.mutator;
import static org.inferred.freebuilder.processor.BuilderMethods.setCountMethod;
import static org.inferred.freebuilder.processor.Util.erasesToAnyOf;
import static org.inferred.freebuilder.processor.Util.upperBound;
import static org.inferred.freebuilder.processor.util.ModelUtils.maybeDeclared;
import static org.inferred.freebuilder.processor.util.ModelUtils.maybeUnbox;
import static org.inferred.freebuilder.processor.util.ModelUtils.overrides;
import static org.inferred.freebuilder.processor.util.feature.FunctionPackage.FUNCTION_PACKAGE;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;

import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.PropertyCodeGenerator.Config;
import org.inferred.freebuilder.processor.excerpt.CheckedMultiset;
import org.inferred.freebuilder.processor.util.Block;
import org.inferred.freebuilder.processor.util.ParameterizedType;
import org.inferred.freebuilder.processor.util.QualifiedName;
import org.inferred.freebuilder.processor.util.SourceBuilder;
import org.inferred.freebuilder.processor.util.StaticExcerpt;

import java.util.Collection;
import java.util.Set;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * {@link PropertyCodeGenerator.Factory} providing append-only semantics for {@link Multiset}
 * properties.
 */
public class MultisetPropertyFactory implements PropertyCodeGenerator.Factory {

  @Override
  public Optional<CodeGenerator> create(Config config) {
    DeclaredType type = maybeDeclared(config.getProperty().getType()).orNull();
    if (type == null || !erasesToAnyOf(type, Multiset.class, ImmutableMultiset.class)) {
      return Optional.absent();
    }

    TypeMirror elementType = upperBound(config.getElements(), type.getTypeArguments().get(0));
    Optional<TypeMirror> unboxedType = maybeUnbox(elementType, config.getTypes());
    boolean overridesSetCountMethod =
        hasSetCountMethodOverride(config, unboxedType.or(elementType));
    return Optional.of(new CodeGenerator(
        config.getMetadata(),
        config.getProperty(),
        overridesSetCountMethod,
        elementType,
        unboxedType));
  }

  private static boolean hasSetCountMethodOverride(
      Config config, TypeMirror type) {
    return overrides(
        config.getBuilder(),
        config.getTypes(),
        setCountMethod(config.getProperty()),
        type,
        config.getTypes().getPrimitiveType(TypeKind.INT));
  }

  private static class CodeGenerator extends PropertyCodeGenerator {

    private static final ParameterizedType COLLECTION =
        QualifiedName.of(Collection.class).withParameters("E");
    private final boolean overridesSetCountMethod;
    private final TypeMirror elementType;
    private final Optional<TypeMirror> unboxedType;

    CodeGenerator(
        Metadata metadata,
        Property property,
        boolean overridesSetCountMethod,
        TypeMirror elementType,
        Optional<TypeMirror> unboxedType) {
      super(metadata, property);
      this.overridesSetCountMethod = overridesSetCountMethod;
      this.elementType = elementType;
      this.unboxedType = unboxedType;
    }

    @Override
    public void addBuilderFieldDeclaration(SourceBuilder code) {
      code.addLine("private final %1$s<%2$s> %3$s = %1$s.create();",
          LinkedHashMultiset.class, elementType, property.getName());
    }

    @Override
    public void addBuilderFieldAccessors(SourceBuilder code) {
      addAdd(code, metadata);
      addVarargsAdd(code, metadata);
      addAddAll(code, metadata);
      addAddCopiesTo(code, metadata);
      addMutate(code, metadata);
      addClear(code, metadata);
      addSetCountOf(code, metadata);
      addGetter(code, metadata);
    }

    private void addAdd(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Adds {@code element} to the multiset to be returned from %s.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName());
      if (!unboxedType.isPresent()) {
        code.addLine(" * @throws NullPointerException if {@code element} is null");
      }
      code.addLine(" */")
          .addLine("public %s %s(%s element) {",
              metadata.getBuilder(),
              addMethod(property),
              unboxedType.or(elementType))
          .addLine("  %s(element, 1);", addCopiesMethod(property))
          .addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addVarargsAdd(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Adds each element of {@code elements} to the multiset to be returned from")
          .addLine(" * %s.", metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
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
          .addLine("    %s(element, 1);", addCopiesMethod(property))
          .addLine("  }")
          .addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addAddAll(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Adds each element of {@code elements} to the multiset to be returned from")
          .addLine(" * %s.", metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
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
          .addLine("    %s(element, 1);", addCopiesMethod(property))
          .addLine("  }")
          .addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addAddCopiesTo(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Adds a number of occurrences of {@code element} to the multiset to be")
          .addLine(" * returned from %s.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName());
      if (!unboxedType.isPresent()) {
        code.addLine(" * @throws NullPointerException if {@code element} is null");
      }
      code.addLine(" * @throws IllegalArgumentException if {@code occurrences} is negative")
          .addLine(" */")
          .addLine("public %s %s(%s element, int occurrences) {",
              metadata.getBuilder(),
              addCopiesMethod(property),
              unboxedType.or(elementType))
          .addLine("  %s(element, this.%s.count(element) + occurrences);",
              setCountMethod(property),
              property.getName())
          .addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addMutate(SourceBuilder code, Metadata metadata) {
      ParameterizedType consumer = code.feature(FUNCTION_PACKAGE).consumer().orNull();
      if (consumer == null) {
        return;
      }
      code.addLine("")
          .addLine("/**")
          .addLine(" * Applies {@code mutator} to the multiset to be returned from %s.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * <p>This method mutates the multiset in-place. {@code mutator} is a void")
          .addLine(" * consumer, so any value returned from a lambda will be ignored. Take care")
          .addLine(" * not to call pure functions, like %s.",
              COLLECTION.javadocNoArgMethodLink("stream"))
          .addLine(" *")
          .addLine(" * @return this {@code Builder} object")
          .addLine(" * @throws NullPointerException if {@code mutator} is null")
          .addLine(" */")
          .addLine("public %s %s(%s<%s<%s>> mutator) {",
              metadata.getBuilder(),
              mutator(property),
              consumer.getQualifiedName(),
              Multiset.class,
              elementType);
      if (overridesSetCountMethod) {
        code.addLine("  mutator.accept(new CheckedMultiset<>(%s, this::%s));",
            property.getName(), setCountMethod(property));
      } else {
        code.addLine("  // If %s is overridden, this method will be updated to delegate to it",
                setCountMethod(property))
            .addLine("  mutator.accept(%s);", property.getName());
      }
      code.addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addClear(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Clears the multiset to be returned from %s.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
          .addLine(" */")
          .addLine("public %s %s() {", metadata.getBuilder(), clearMethod(property))
          .addLine("  this.%s.clear();", property.getName())
          .addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addSetCountOf(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Adds or removes the necessary occurrences of {@code element} to/from the")
          .addLine(" * multiset to be returned from %s, such that it attains the",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" * desired count.")
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName());
      if (!unboxedType.isPresent()) {
        code.addLine(" * @throws NullPointerException if {@code element} is null");
      }
      code.addLine(" * @throws IllegalArgumentException if {@code occurrences} is negative")
          .addLine(" */")
          .addLine("public %s %s(%s element, int occurrences) {",
              metadata.getBuilder(),
              setCountMethod(property),
              unboxedType.or(elementType));
      if (!unboxedType.isPresent()) {
        code.addLine("  %s.checkNotNull(element);", Preconditions.class, property.getName());
      }
      code.addLine("  this.%s.setCount(element, occurrences);", property.getName())
          .addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addGetter(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Returns an unmodifiable view of the multiset that will be returned by")
          .addLine(" * %s.", metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" * Changes to this builder will be reflected in the view.")
          .addLine(" */")
          .addLine("public %s<%s> %s() {", Multiset.class, elementType, getter(property))
          .addLine("  return %s.unmodifiableMultiset(%s);", Multisets.class, property.getName())
          .addLine("}");
    }

    @Override
    public void addFinalFieldAssignment(SourceBuilder code, String finalField, String builder) {
      code.addLine("%s = %s.copyOf(%s.%s);",
              finalField, ImmutableMultiset.class, builder, property.getName());
    }

    @Override
    public void addMergeFromValue(SourceBuilder code, String value) {
      code.addLine("%s(%s.%s());", addAllMethod(property), value, property.getGetterName());
    }

    @Override
    public void addMergeFromBuilder(SourceBuilder code, String builder) {
      code.addLine("%s(((%s) %s).%s);",
          addAllMethod(property),
          metadata.getGeneratedBuilder(),
          builder,
          property.getName());
    }

    @Override
    public void addSetFromResult(SourceBuilder code, String builder, String variable) {
      code.addLine("%s.%s%s(%s);", builder, addAllMethod(property), variable);
    }

    @Override
    public void addClearField(Block code) {
      code.addLine("%s.clear();", property.getName());
    }

    @Override
    public Set<StaticExcerpt> getStaticExcerpts() {
      ImmutableSet.Builder<StaticExcerpt> staticMethods = ImmutableSet.builder();
      if (overridesSetCountMethod) {
        staticMethods.addAll(CheckedMultiset.excerpts());
      }
      return staticMethods.build();
    }
  }
}
