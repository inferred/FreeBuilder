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
import static org.inferred.freebuilder.processor.util.ModelUtils.maybeDeclared;
import static org.inferred.freebuilder.processor.util.ModelUtils.maybeUnbox;
import static org.inferred.freebuilder.processor.util.ModelUtils.overrides;
import static org.inferred.freebuilder.processor.util.PreconditionExcerpts.checkNotNullInline;
import static org.inferred.freebuilder.processor.util.PreconditionExcerpts.checkNotNullPreamble;
import static org.inferred.freebuilder.processor.util.feature.FunctionPackage.FUNCTION_PACKAGE;
import static org.inferred.freebuilder.processor.util.feature.GuavaLibrary.GUAVA;
import static org.inferred.freebuilder.processor.util.feature.SourceLevel.SOURCE_LEVEL;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;

import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.PropertyCodeGenerator.Config;
import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.ParameterizedType;
import org.inferred.freebuilder.processor.util.QualifiedName;
import org.inferred.freebuilder.processor.util.SourceBuilder;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

/**
 * {@link PropertyCodeGenerator.Factory} providing append-only semantics for {@link Set}
 * properties.
 */
public class SetPropertyFactory implements PropertyCodeGenerator.Factory {

  private static final String ADD_PREFIX = "add";
  private static final String ADD_ALL_PREFIX = "addAll";
  private static final String CLEAR_PREFIX = "clear";
  private static final String GET_PREFIX = "get";
  private static final String MUTATE_PREFIX = "mutate";
  private static final String REMOVE_PREFIX = "remove";

  @Override
  public Optional<CodeGenerator> create(Config config) {
    // No @Nullable properties
    if (!config.getProperty().getNullableAnnotations().isEmpty()) {
      return Optional.absent();
    }

    DeclaredType type = maybeDeclared(config.getProperty().getType()).orNull();
    if (type != null && erasesToAnyOf(type, Set.class, ImmutableSet.class)) {
        return Optional.of(createForSetType(config, type));
    }
    return Optional.absent();
  }

  private static CodeGenerator createForSetType(Config config, DeclaredType type) {
    TypeMirror elementType = upperBound(config.getElements(), type.getTypeArguments().get(0));
    Optional<TypeMirror> unboxedType = maybeUnbox(elementType, config.getTypes());
    boolean overridesAddMethod = hasAddMethodOverride(config.getBuilder(), config.getProperty(), elementType, unboxedType, config.getTypes());
    return new CodeGenerator(config.getProperty(), elementType, unboxedType, overridesAddMethod);
  }

  private static boolean hasAddMethodOverride(
      Optional<TypeElement> builder,
      Property property,
      TypeMirror elementType,
      Optional<TypeMirror> unboxedType,
      Types types) {
    if (!builder.isPresent()) {
      return false;
    }
    return overrides(
        builder.get(),
        types,
        ADD_PREFIX + property.getCapitalizedName(),
        unboxedType.or(elementType));
  }

  @VisibleForTesting
  static class CodeGenerator extends PropertyCodeGenerator {

    private static final ParameterizedType COLLECTION = QualifiedName.of(Collection.class).withParameters();
    private final TypeMirror elementType;
    private final Optional<TypeMirror> unboxedType;
    private final boolean overridesAddMethod;

    CodeGenerator(
        Property property,
        TypeMirror elementType,
        Optional<TypeMirror> unboxedType,
        boolean overridesAddMethod) {
      super(property);
      this.elementType = elementType;
      this.unboxedType = unboxedType;
      this.overridesAddMethod = overridesAddMethod;
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
      addMutator(code, metadata);
      addClear(code, metadata);
      addGetter(code, metadata);
    }

    private void addAdd(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Adds {@code element} to the set to be returned from %s.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" * If the set already contains {@code element}, then {@code %s%s}",
              ADD_PREFIX, property.getCapitalizedName())
          .addLine(" * has no effect (only the previously added element is retained).")
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName());
      if (!unboxedType.isPresent()) {
        code.addLine(" * @throws NullPointerException if {@code element} is null");
      }
      code.addLine(" */")
          .addLine("public %s %s%s(%s element) {",
              metadata.getBuilder(),
              ADD_PREFIX,
              property.getCapitalizedName(),
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
          .addLine("public %s %s%s(%s... elements) {",
              metadata.getBuilder(),
              ADD_PREFIX,
              property.getCapitalizedName(),
              unboxedType.or(elementType))
          .addLine("  for (%s element : elements) {", unboxedType.or(elementType))
          .addLine("    %s%s(element);", ADD_PREFIX, property.getCapitalizedName())
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
      code.addLine("public %s %s%s(%s<? extends %s> elements) {",
              metadata.getBuilder(),
              ADD_ALL_PREFIX,
              property.getCapitalizedName(),
              Iterable.class,
              elementType)
          .addLine("  for (%s element : elements) {", unboxedType.or(elementType))
          .addLine("    %s%s(element);", ADD_PREFIX, property.getCapitalizedName())
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
          .addLine("public %s %s%s(%s element) {",
              metadata.getBuilder(),
              REMOVE_PREFIX,
              property.getCapitalizedName(),
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

    private void addMutator(SourceBuilder code, Metadata metadata) {
      Optional<ParameterizedType> consumer = code.feature(FUNCTION_PACKAGE).consumer();
      if (consumer.isPresent()) {
        code.addLine("")
            .addLine("/**")
            .addLine(" * Applies {@code mutator} to the set to be returned from %s.",
                metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
            .addLine(" *")
            .addLine(" * This method mutates the set in-place. {@code mutator} is a void consumer,")
            .addLine(" * so any value returned from a lambda will be ignored. Take care not to")
            .addLine(" * call pure functions, like %s.",
                COLLECTION.javadocNoArgMethodLink("stream"))
            .addLine(" *")
            .addLine(" * @return this {@code Builder} object")
            .addLine(" * @throws NullPointerException if {@code mutator} is null")
            .addLine(" */")
            .addLine("public %s %s%s(%s<? super %s<%s>> mutator) {",
                metadata.getBuilder(),
                MUTATE_PREFIX,
                property.getCapitalizedName(),
                consumer.get().getQualifiedName(),
                Set.class,
                elementType);
        if (overridesAddMethod) {
          code.addLine("  mutator.accept(new %s<%s>() {", AbstractSet.class, elementType)
              .addLine("    @Override public %s<%s> iterator() {", Iterator.class, elementType)
              .addLine("      return %s.iterator();", property.getName())
              .addLine("    }")
              .addLine("    @Override public int size() {")
              .addLine("      return %s.size();", property.getName())
              .addLine("    }")
              .addLine("    @Override public boolean contains(Object e) {")
              .addLine("      return %s.contains(e);", property.getName())
              .addLine("    }")
              .addLine("    @Override public boolean add(%s e) {", elementType)
              .addLine("      if (!%s.contains(e)) {", property.getName())
              .addLine("        %s%s(e);", ADD_PREFIX, property.getCapitalizedName())
              .addLine("        return true;")
              .addLine("      } else {")
              .addLine("        return false;")
              .addLine("      }")
              .addLine("    }")
              .addLine("    @Override public boolean remove(Object e) {")
              .addLine("      return %s.remove(e);", property.getName())
              .addLine("    }")
              .addLine("  });");
        } else {
          code.addLine("  // If %s%s is overridden, this method will be updated to delegate to it",
                  ADD_PREFIX, property.getCapitalizedName())
              .addLine("  mutator.accept(%s);", property.getName());
        }
        code.addLine("  return (%s) this;", metadata.getBuilder())
            .addLine("}");
      }
    }

    private void addClear(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Clears the set to be returned from %s.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
          .addLine(" */")
          .addLine("public %s %s%s() {",
              metadata.getBuilder(),
              CLEAR_PREFIX,
              property.getCapitalizedName())
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
          .addLine("public %s<%s> %s%s() {",
              Set.class,
              elementType,
              GET_PREFIX,
              property.getCapitalizedName())
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
      code.addLine("%s%s(%s.%s());",
          ADD_ALL_PREFIX, property.getCapitalizedName(), value, property.getGetterName());
    }

    @Override
    public void addMergeFromBuilder(SourceBuilder code, Metadata metadata, String builder) {
      code.addLine("%s%s(((%s) %s).%s);",
          ADD_ALL_PREFIX,
          property.getCapitalizedName(),
          metadata.getGeneratedBuilder(),
          builder,
          property.getName());
    }

    @Override
    public void addSetFromResult(SourceBuilder code, String builder, String variable) {
      code.addLine("%s.%s%s(%s);",
          builder, ADD_ALL_PREFIX, property.getCapitalizedName(), variable);
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
