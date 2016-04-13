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
import static org.inferred.freebuilder.processor.BuilderMethods.checkMethod;
import static org.inferred.freebuilder.processor.BuilderMethods.clearMethod;
import static org.inferred.freebuilder.processor.BuilderMethods.getter;
import static org.inferred.freebuilder.processor.BuilderMethods.mutator;
import static org.inferred.freebuilder.processor.Util.erasesToAnyOf;
import static org.inferred.freebuilder.processor.Util.upperBound;
import static org.inferred.freebuilder.processor.util.ModelUtils.findMethod;
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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.PropertyCodeGenerator.Config;
import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.ParameterizedType;
import org.inferred.freebuilder.processor.util.SourceBuilder;

import java.lang.reflect.Array;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;

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

    return Optional.of(createForListType(config, type));
  }

  private static CodeGenerator createForListType(Config config, DeclaredType type) {
    TypeMirror elementType = upperBound(config.getElements(), type.getTypeArguments().get(0));
    Optional<TypeMirror> unboxedType = maybeUnbox(elementType, config.getTypes());
    boolean overridesCheckMethod = hasCheckMethodOverride(config, unboxedType.or(elementType));
    Optional<ExecutableElement> addMethod = getAddMethodOverride(
        config, unboxedType.or(elementType));
    if (addMethod.isPresent() && !overridesCheckMethod) {
      config.getMessager().printMessage(
          Kind.MANDATORY_WARNING,
          "Overriding add methods on @FreeBuilder types is deprecated; please override "
              + checkMethod(config.getProperty()) + " instead",
          addMethod.get());
    }
    return new CodeGenerator(
        config.getProperty(),
        overridesCheckMethod,
        addMethod.isPresent(),
        elementType,
        unboxedType);
  }

  private static boolean hasCheckMethodOverride(Config config, TypeMirror keyType) {
    if (!config.getBuilder().isPresent()) {
      return false;
    }
    return overrides(
        config.getBuilder().get(),
        config.getTypes(),
        checkMethod(config.getProperty()),
        keyType);
  }

  private static Optional<ExecutableElement> getAddMethodOverride(
      Config config, TypeMirror keyType) {
    if (!config.getBuilder().isPresent()) {
      return Optional.absent();
    }
    return findMethod(
        config.getBuilder().get(),
        config.getTypes(),
        addMethod(config.getProperty()),
        keyType);
  }

  @VisibleForTesting static class CodeGenerator extends PropertyCodeGenerator {

    private final boolean overridesCheckMethod;
    private final boolean overridesAddMethod;
    private final TypeMirror elementType;
    private final Optional<TypeMirror> unboxedType;

    @VisibleForTesting
    CodeGenerator(
        Property property,
        boolean overridesCheckMethod,
        boolean overridesAddMethod,
        TypeMirror elementType,
        Optional<TypeMirror> unboxedType) {
      super(property);
      this.overridesCheckMethod = overridesCheckMethod;
      this.overridesAddMethod = overridesAddMethod;
      this.elementType = elementType;
      this.unboxedType = unboxedType;
    }

    @Override
    public void addBuilderFieldDeclaration(SourceBuilder code) {
      code.addLine("private final %1$s<%2$s> %3$s = new %1$s<%4$s>();",
          ArrayList.class,
          elementType,
          property.getName(),
          code.feature(SOURCE_LEVEL).supportsDiamondOperator() ? "" : elementType);
    }

    @Override
    public void addBuilderFieldAccessors(SourceBuilder code, Metadata metadata) {
      addAdd(code, metadata);
      addVarargsAdd(code, metadata);
      addAddAll(code, metadata);
      addMutate(code, metadata);
      addClear(code, metadata);
      addGetter(code, metadata);
      addCheck(code, metadata);
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
      if (overridesCheckMethod) {
        code.addLine(" * @throws IllegalArgumentException if {@code element} is invalid");
      }
      code.addLine(" */")
          .addLine("public %s %s %s(%s element) {",
              (overridesAddMethod || overridesCheckMethod) ? "" : "final",
              metadata.getBuilder(),
              addMethod(property),
              unboxedType.or(elementType));
      if (unboxedType.isPresent()) {
        code.addLine("  %s(element);", checkMethod(property));
      } else {
        code.add(checkNotNullPreamble("element"))
            .addLine("  %s(%s);", checkMethod(property), checkNotNullInline("element"));
      }
      code.addLine("  this.%s.add(element);", property.getName())
          .addLine("  return (%s) this;", metadata.getBuilder())
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
      if (overridesCheckMethod) {
        code.addLine(" * @throws IllegalArgumentException if {@code elements} contains an invalid "
            + "element");
      }
      code.addLine(" */")
          .addLine("public %s %s(%s... elements) {",
              metadata.getBuilder(),
              addMethod(property),
              unboxedType.or(elementType))
          .addLine("  %1$s.ensureCapacity(%1$s.size() + elements.length);", property.getName())
          .addLine("  for (%s element : elements) {", unboxedType.or(elementType))
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
          .addLine(" *     null element");
      if (overridesCheckMethod) {
        code.addLine(" * @throws IllegalArgumentException if {@code elements} contains an invalid "
            + "element");
      }
      code.addLine(" */");
      addAccessorAnnotations(code);
      code.addLine("public %s %s(%s<? extends %s> elements) {",
              metadata.getBuilder(),
              addAllMethod(property),
              Iterable.class,
              elementType)
          .addLine("  if (elements instanceof %s) {", Collection.class)
          .addLine("    %1$s.ensureCapacity(%1$s.size() + ((%2$s<?>) elements).size());",
              property.getName(), Collection.class)
          .addLine("  }")
          .addLine("  for (%s element : elements) {", unboxedType.or(elementType))
          .addLine("    %s(element);", addMethod(property))
          .addLine("  }")
          .addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addMutate(SourceBuilder code, Metadata metadata) {
      if (overridesAddMethod && !overridesCheckMethod) {
        return;
      }
      Optional<ParameterizedType> consumer = code.feature(FUNCTION_PACKAGE).consumer();
      if (consumer.isPresent()) {
        code.addLine("")
            .addLine("public %s %s(%s<? super %s<%s>> mutator) {",
                metadata.getBuilder(),
                mutator(property),
                consumer.get().getQualifiedName(),
                List.class,
                elementType);
        if (overridesCheckMethod) {
          code.addLine("  mutator.accept(new CheckedDelegatingList<%s>(%s, this::%s));",
              elementType, property.getName(), checkMethod(property));
        } else {
          code.addLine("  // If %s is overridden, this method will be updated to delegate to it",
                  checkMethod(property))
              .addLine("  mutator.accept(%s);", property.getName());
        }
        code.addLine("  return (%s) this;", metadata.getBuilder())
            .addLine("}");
      }
    }

    private void addClear(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Clears the list to be returned from %s.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
          .addLine(" */")
          .addLine("public %s %s() {", metadata.getBuilder(), clearMethod(property))
          .addLine("  this.%s.clear();", property.getName())
          .addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addGetter(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Returns an unmodifiable view of the list that will be returned by")
          .addLine(" * %s.", metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" * Changes to this builder will be reflected in the view.")
          .addLine(" */")
          .addLine("public %s<%s> %s() {", List.class, elementType, getter(property))
          .addLine("  return %s.unmodifiableList(%s);", Collections.class, property.getName())
          .addLine("}");
    }

    private void addCheck(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Checks that {@code element} can be put into the list to be returned from")
          .addLine(" * %s.", metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * <p>Override this to perform argument validation, throwing an")
          .addLine(" * %s if validation fails.", IllegalArgumentException.class)
          .addLine(" */")
          .addLine("@%s(\"unused\")  // element may be used in an overriding method",
              SuppressWarnings.class)
          .addLine("void %s(%s element) {}", checkMethod(property), unboxedType.or(elementType));
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
    public Set<Excerpt> getStaticMethods() {
      ImmutableSet.Builder<Excerpt> methods = ImmutableSet.builder();
      methods.add(StaticMethod.values());
      if (overridesCheckMethod) {
        methods.add(CheckedDelegatingTypes.values());
      }
      return methods.build();
    }
  }

  private enum StaticMethod implements Excerpt {
    IMMUTABLE_LIST() {
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
    }
  }

  private enum CheckedDelegatingTypes implements Excerpt {
    CHECKED_DELEGATING_LIST {
      @Override
      public void addTo(SourceBuilder code) {
        ParameterizedType consumer = code.feature(FUNCTION_PACKAGE).consumer().orNull();
        if (consumer != null) {
          code.addLine("")
              .addLine("private static class CheckedDelegatingList<E> extends %s<E> {",
                  AbstractList.class)
              .addLine("")
              .addLine("  private final %s<E> delegate;", List.class)
              .addLine("  private final %s<E> check;", consumer.getQualifiedName())
              .addLine("")
              .addLine("  CheckedDelegatingList(%s<E> delegate, %s<E> check) {",
                  List.class, consumer.getQualifiedName())
              .addLine("    this.delegate = delegate;")
              .addLine("    this.check = check;")
              .addLine("  }")
              .addLine("")
              .addLine("  @Override public int size() {")
              .addLine("    return delegate.size();")
              .addLine("  }")
              .addLine("")
              .addLine("  @Override public E get(int index) {")
              .addLine("    return delegate.get(index);")
              .addLine("  }")
              .addLine("")
              .addLine("  @Override public E set(int index, E element) {")
              .addLine("    check.accept(element);")
              .addLine("    return delegate.set(index, element);")
              .addLine("  }")
              .addLine("")
              .addLine("  @Override public void add(int index, E element) {")
              .addLine("    check.accept(element);")
              .addLine("    delegate.add(index, element);")
              .addLine("  }")
              .addLine("")
              .addLine("  @Override public E remove(int index) {")
              .addLine("    return delegate.remove(index);")
              .addLine("  }")
              .addLine("")
              .addLine("  @Override public void clear() {")
              .addLine("    delegate.clear();")
              .addLine("  }")
              .addLine("")
              .addLine("  @Override protected void removeRange(int fromIndex, int toIndex) {")
              .addLine("    delegate.subList(fromIndex, toIndex).clear();")
              .addLine("  }")
              .addLine("}");
        }
      }
    },
  }
}
