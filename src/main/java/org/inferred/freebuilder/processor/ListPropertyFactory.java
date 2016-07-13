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
import static org.inferred.freebuilder.processor.BuilderMethods.mutator;
import static org.inferred.freebuilder.processor.Util.erasesToAnyOf;
import static org.inferred.freebuilder.processor.Util.upperBound;
import static org.inferred.freebuilder.processor.util.ModelUtils.maybeDeclared;
import static org.inferred.freebuilder.processor.util.ModelUtils.maybeUnbox;
import static org.inferred.freebuilder.processor.util.ModelUtils.overrides;
import static org.inferred.freebuilder.processor.util.PreconditionExcerpts.checkNotNullInline;
import static org.inferred.freebuilder.processor.util.PreconditionExcerpts.checkNotNullPreamble;
import static org.inferred.freebuilder.processor.util.StaticExcerpt.Type.METHOD;
import static org.inferred.freebuilder.processor.util.feature.FunctionPackage.FUNCTION_PACKAGE;
import static org.inferred.freebuilder.processor.util.feature.GuavaLibrary.GUAVA;
import static org.inferred.freebuilder.processor.util.feature.SourceLevel.diamondOperator;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.PropertyCodeGenerator.Config;
import org.inferred.freebuilder.processor.excerpt.CheckedList;
import org.inferred.freebuilder.processor.util.Block;
import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.ParameterizedType;
import org.inferred.freebuilder.processor.util.QualifiedName;
import org.inferred.freebuilder.processor.util.SourceBuilder;
import org.inferred.freebuilder.processor.util.StaticExcerpt;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/**
 * {@link PropertyCodeGenerator.Factory} providing append-only semantics for {@link List}
 * properties.
 */
public class ListPropertyFactory implements PropertyCodeGenerator.Factory {

  @Override
  public Optional<? extends PropertyCodeGenerator> create(Config config) {
    DeclaredType type = maybeDeclared(config.getProperty().getType()).orNull();
    if (type == null || !erasesToAnyOf(type, Collection.class, List.class, ImmutableList.class)) {
      return Optional.absent();
    }

    TypeMirror elementType = upperBound(config.getElements(), type.getTypeArguments().get(0));
    Optional<TypeMirror> unboxedType = maybeUnbox(elementType, config.getTypes());
    boolean overridesAddMethod = hasAddMethodOverride(config, unboxedType.or(elementType));
    return Optional.of(new CodeGenerator(
        config.getMetadata(),
        config.getProperty(),
        overridesAddMethod,
        elementType,
        unboxedType));
  }

  private static boolean hasAddMethodOverride(Config config, TypeMirror keyType) {
    return overrides(
        config.getBuilder(),
        config.getTypes(),
        addMethod(config.getProperty()),
        keyType);
  }

  @VisibleForTesting static class CodeGenerator extends PropertyCodeGenerator {

    private static final ParameterizedType COLLECTION =
        QualifiedName.of(Collection.class).withParameters("E");

    private final boolean overridesAddMethod;
    private final TypeMirror elementType;
    private final Optional<TypeMirror> unboxedType;

    @VisibleForTesting
    CodeGenerator(
        Metadata metadata,
        Property property,
        boolean overridesAddMethod,
        TypeMirror elementType,
        Optional<TypeMirror> unboxedType) {
      super(metadata, property);
      this.overridesAddMethod = overridesAddMethod;
      this.elementType = elementType;
      this.unboxedType = unboxedType;
    }

    @Override
    public void addBuilderFieldDeclaration(SourceBuilder code) {
      if (code.feature(GUAVA).isAvailable()) {
        code.addLine("private %s<%s> %s = %s.of();",
            List.class,
            elementType,
            property.getName(),
            ImmutableList.class);
      } else {
        code.addLine("private final %1$s<%2$s> %3$s = new %1$s%4$s();",
            ArrayList.class,
            elementType,
            property.getName(),
            diamondOperator(elementType));
      }
    }

    @Override
    public void addBuilderFieldAccessors(SourceBuilder code) {
      addAdd(code, metadata);
      addVarargsAdd(code, metadata);
      addAddAll(code, metadata);
      addMutate(code, metadata);
      addClear(code, metadata);
      addGetter(code, metadata);
    }

    private void addAdd(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Adds {@code element} to the list to be returned from %s.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName());
      if (!unboxedType.isPresent()) {
        code.addLine(" * @throws NullPointerException if {@code element} is null");
      }
      code.addLine(" */")
          .addLine("public %s %s(%s element) {",
              metadata.getBuilder(), addMethod(property), unboxedType.or(elementType));
      if (code.feature(GUAVA).isAvailable()) {
        code.addLine("  if (this.%s instanceof %s) {", property.getName(), ImmutableList.class)
            .addLine("    this.%1$s = new %2$s%3$s(this.%1$s);",
                property.getName(),
                ArrayList.class,
                diamondOperator(elementType))
            .addLine("  }");
      }
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
          .addLine(" * Adds each element of {@code elements} to the list to be returned from")
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
              unboxedType.or(elementType));
      if (code.feature(GUAVA).isAvailable()) {
        code.addLine("  if (%s instanceof %s) {", property.getName(), ImmutableList.class)
            .addLine("    %1$s = new %2$s%3$s(%1$s);",
                property.getName(), ArrayList.class, diamondOperator(elementType))
            .addLine("  }")
            .add("  ((%s<?>) %s)", ArrayList.class, property.getName());
      } else {
        code.add("  %s", property.getName());
      }
      code.add(".ensureCapacity(%s.size() + elements.length);%n", property.getName());
      code.addLine("  for (%s element : elements) {", unboxedType.or(elementType))
          .addLine("    %s(element);", addMethod(property))
          .addLine("  }")
          .addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addAddAll(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Adds each element of {@code elements} to the list to be returned from")
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
              elementType);
      if (code.feature(GUAVA).isAvailable()) {
        code.addLine("  if (%1$s == %2$s.<%3$s>of() && elements instanceof %2$s) {",
                property.getName(), ImmutableList.class, elementType)
            .addLine("    // Use ImmutableList.copyOf to avoid an unchecked cast.")
            .addLine("    %s = %s.copyOf(elements);", property.getName(), ImmutableList.class)
            .addLine("  } else {")
            .addLine("    if (%s instanceof %s) {", property.getName(), ImmutableList.class)
            .addLine("      %1$s = new %2$s%3$s(%1$s);",
                property.getName(),
                ArrayList.class,
                diamondOperator(elementType))
            .addLine("    }");
      }
      code.addLine("    if (elements instanceof %s) {", Collection.class)
          .add("      ");
      if (code.feature(GUAVA).isAvailable()) {
        code.add("((%s<?>) %s)", ArrayList.class, property.getName());
      } else {
        code.add("%s", property.getName());
      }
      code.add(".ensureCapacity(%s.size() + ((%s<?>) elements).size());%n",
              property.getName(), Collection.class);
      code.addLine("    }")
          .addLine("    for (%s element : elements) {", unboxedType.or(elementType))
          .addLine("      %s(element);", addMethod(property))
          .addLine("    }");
      if (code.feature(GUAVA).isAvailable()) {
        code.addLine("  }");
      }
      code.addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addMutate(SourceBuilder code, Metadata metadata) {
      ParameterizedType consumer = code.feature(FUNCTION_PACKAGE).consumer().orNull();
      if (consumer == null) {
        return;
      }
      code.addLine("")
          .addLine("/**")
          .addLine(" * Applies {@code mutator} to the list to be returned from %s.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * <p>This method mutates the list in-place. {@code mutator} is a void")
          .addLine(" * consumer, so any value returned from a lambda will be ignored. Take care")
          .addLine(" * not to call pure functions, like %s.",
              COLLECTION.javadocNoArgMethodLink("stream"))
          .addLine(" *")
          .addLine(" * @return this {@code Builder} object")
          .addLine(" * @throws NullPointerException if {@code mutator} is null")
          .addLine(" */")
          .addLine("public %s %s(%s<? super %s<%s>> mutator) {",
              metadata.getBuilder(),
              mutator(property),
              consumer.getQualifiedName(),
              List.class,
              elementType);
      if (code.feature(GUAVA).isAvailable()) {
        code.addLine("  if (this.%s instanceof %s) {", property.getName(), ImmutableList.class)
            .addLine("    this.%1$s = new %2$s%3$s(this.%1$s);",
                property.getName(),
                ArrayList.class,
                diamondOperator(elementType))
            .addLine("  }");
      }
      if (overridesAddMethod) {
        code.addLine("  mutator.accept(new CheckedList<>(%s, this::%s));",
            property.getName(), addMethod(property));
      } else {
        code.addLine("  // If %s is overridden, this method will be updated to delegate to it",
                addMethod(property))
            .addLine("  mutator.accept(%s);", property.getName());
      }
      code.addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addClear(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Clears the list to be returned from %s.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
          .addLine(" */")
          .addLine("public %s %s() {", metadata.getBuilder(), clearMethod(property));
      if (code.feature(GUAVA).isAvailable()) {
        code.addLine("  if (%s instanceof %s) {", property.getName(), ImmutableList.class)
            .addLine("    %s = %s.of();", property.getName(), ImmutableList.class)
            .addLine("  } else {");
      }
      code.addLine("    %s.clear();", property.getName());
      if (code.feature(GUAVA).isAvailable()) {
        code.addLine("  }");
      }
      code.addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addGetter(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Returns an unmodifiable view of the list that will be returned by")
          .addLine(" * %s.", metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" * Changes to this builder will be reflected in the view.")
          .addLine(" */")
          .addLine("public %s<%s> %s() {", List.class, elementType, getter(property));
      if (code.feature(GUAVA).isAvailable()) {
        code.addLine("  if (%s instanceof %s) {", property.getName(), ImmutableList.class)
            .addLine("    %1$s = new %2$s%3$s(%1$s);",
                property.getName(), ArrayList.class, diamondOperator(elementType))
            .addLine("  }");
      }
      code.addLine("  return %s.unmodifiableList(%s);", Collections.class, property.getName())
          .addLine("}");
    }

    @Override
    public void addFinalFieldAssignment(SourceBuilder code, String finalField, String builder) {
      if (code.feature(GUAVA).isAvailable()) {
        code.addLine("%s = %s.copyOf(%s.%s);",
            finalField, ImmutableList.class, builder, property.getName());
      } else {
        code.addLine("%s = immutableList(%s.%s, %s.class);",
            finalField, builder, property.getName(), elementType);
      }
    }

    @Override
    public void addMergeFromValue(Block code, String value) {
      code.addLine("%s(%s.%s());", addAllMethod(property), value, property.getGetterName());
    }

    @Override
    public void addMergeFromBuilder(Block code, String builder) {
      Excerpt base = Declarations.upcastToGeneratedBuilder(code, metadata, builder);
      code.addLine("%s(%s.%s);", addAllMethod(property), base, property.getName());
    }

    @Override
    public void addSetFromResult(SourceBuilder code, String builder, String variable) {
      code.addLine("%s.%s(%s);", builder, addAllMethod(property), variable);
    }

    @Override
    public void addClearField(Block code) {
      code.addLine("%s();", clearMethod(property));
    }

    @Override
    public Set<StaticExcerpt> getStaticExcerpts() {
      ImmutableSet.Builder<StaticExcerpt> methods = ImmutableSet.builder();
      methods.add(IMMUTABLE_LIST);
      if (overridesAddMethod) {
        methods.addAll(CheckedList.excerpts());
      }
      return methods.build();
    }
  }

  private static final StaticExcerpt IMMUTABLE_LIST = new StaticExcerpt(METHOD, "immutableList") {
    @Override
    public void addTo(SourceBuilder code) {
      if (!code.feature(GUAVA).isAvailable()) {
        code.addLine("")
            .addLine("@%s(\"unchecked\")", SuppressWarnings.class)
            .addLine("private static <E> %1$s<E> immutableList(%1$s<E> elements, %2$s<E> type) {",
                List.class, Class.class)
            .addLine("  switch (elements.size()) {")
            .addLine("  case 0:")
            .addLine("    return %s.emptyList();", Collections.class)
            .addLine("  case 1:")
            .addLine("    return %s.singletonList(elements.get(0));", Collections.class)
            .addLine("  default:")
            .addLine("    return %s.unmodifiableList(%s.asList(elements.toArray(",
                Collections.class, Arrays.class)
            .addLine("        (E[]) %s.newInstance(type, elements.size()))));", Array.class)
            .addLine("  }")
            .addLine("}");
      }
    }
  };
}
