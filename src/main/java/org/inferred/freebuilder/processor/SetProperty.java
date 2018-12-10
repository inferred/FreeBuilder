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
import static org.inferred.freebuilder.processor.BuilderMethods.removeMethod;
import static org.inferred.freebuilder.processor.Util.erasesToAnyOf;
import static org.inferred.freebuilder.processor.Util.upperBound;
import static org.inferred.freebuilder.processor.util.Block.methodBody;
import static org.inferred.freebuilder.processor.util.ModelUtils.maybeDeclared;
import static org.inferred.freebuilder.processor.util.ModelUtils.maybeUnbox;
import static org.inferred.freebuilder.processor.util.ModelUtils.needsSafeVarargs;
import static org.inferred.freebuilder.processor.util.ModelUtils.overrides;
import static org.inferred.freebuilder.processor.util.feature.GuavaLibrary.GUAVA;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;

import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.excerpt.CheckedSet;
import org.inferred.freebuilder.processor.util.Block;
import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.Excerpts;
import org.inferred.freebuilder.processor.util.LazyName;
import org.inferred.freebuilder.processor.util.ParameterizedType;
import org.inferred.freebuilder.processor.util.QualifiedName;
import org.inferred.freebuilder.processor.util.SourceBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.BaseStream;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/**
 * {@link PropertyCodeGenerator} providing fluent methods for {@link Set} properties.
 */
class SetProperty extends PropertyCodeGenerator {

  static class Factory implements PropertyCodeGenerator.Factory {

    @Override
    public Optional<SetProperty> create(Config config) {
      DeclaredType type = maybeDeclared(config.getProperty().getType()).orNull();
      if (type == null || !erasesToAnyOf(type, Set.class, ImmutableSet.class)) {
        return Optional.absent();
      }

      TypeMirror elementType = upperBound(config.getElements(), type.getTypeArguments().get(0));
      Optional<TypeMirror> unboxedType = maybeUnbox(elementType, config.getTypes());
      boolean needsSafeVarargs = needsSafeVarargs(unboxedType.or(elementType));
      boolean overridesAddMethod = hasAddMethodOverride(config, unboxedType.or(elementType));
      boolean overridesVarargsAddMethod =
          hasVarargsAddMethodOverride(config, unboxedType.or(elementType));
      return Optional.of(new SetProperty(
          config.getMetadata(),
          config.getProperty(),
          elementType,
          unboxedType,
          needsSafeVarargs,
          overridesAddMethod,
          overridesVarargsAddMethod));
    }

    private static boolean hasAddMethodOverride(Config config, TypeMirror elementType) {
      return overrides(
          config.getBuilder(),
          config.getTypes(),
          addMethod(config.getProperty()),
          elementType);
    }

    private static boolean hasVarargsAddMethodOverride(Config config, TypeMirror elementType) {
      return overrides(
          config.getBuilder(),
          config.getTypes(),
          addMethod(config.getProperty()),
          config.getTypes().getArrayType(elementType));
    }
  }

  private static final ParameterizedType COLLECTION =
      QualifiedName.of(Collection.class).withParameters("E");
  private final TypeMirror elementType;
  private final Optional<TypeMirror> unboxedType;
  private final boolean needsSafeVarargs;
  private final boolean overridesAddMethod;
  private final boolean overridesVarargsAddMethod;

  SetProperty(
      Metadata metadata,
      Property property,
      TypeMirror elementType,
      Optional<TypeMirror> unboxedType,
      boolean needsSafeVarargs,
      boolean overridesAddMethod,
      boolean overridesVarargsAddMethod) {
    super(metadata, property);
    this.elementType = elementType;
    this.unboxedType = unboxedType;
    this.needsSafeVarargs = needsSafeVarargs;
    this.overridesAddMethod = overridesAddMethod;
    this.overridesVarargsAddMethod = overridesVarargsAddMethod;
  }

  @Override
  public void addBuilderFieldDeclaration(SourceBuilder code) {
    if (code.feature(GUAVA).isAvailable()) {
      code.addLine("private %s<%s> %s = %s.of();",
          Set.class, elementType, property.getField(), ImmutableSet.class);
    } else {
      code.addLine("private final %1$s<%2$s> %3$s = new %1$s<>();",
          LinkedHashSet.class, elementType, property.getField());
    }
  }

  @Override
  public void addBuilderFieldAccessors(SourceBuilder code) {
    addAdd(code, metadata);
    addVarargsAdd(code, metadata);
    addSpliteratorAddAll(code, metadata);
    addStreamAddAll(code, metadata);
    addIterableAddAll(code, metadata);
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
    Block body = methodBody(code, "element");
    if (body.feature(GUAVA).isAvailable()) {
      body.addLine("  if (%s instanceof %s) {", property.getField(), ImmutableSet.class)
          .addLine("    %1$s = new %2$s<>(%1$s);", property.getField(), LinkedHashSet.class)
          .addLine("  }");
    }
    if (unboxedType.isPresent()) {
      body.addLine("  %s.add(element);", property.getField());
    } else {
      body.addLine("  %s.add(%s.requireNonNull(element));", property.getField(), Objects.class);
    }
    body.addLine("  return (%s) this;", metadata.getBuilder());
    code.add(body)
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
    code.addLine(" */");
    if (needsSafeVarargs) {
      if (!overridesVarargsAddMethod) {
        code.addLine("@%s", SafeVarargs.class)
            .addLine("@%s({\"varargs\"})", SuppressWarnings.class);
      } else {
        code.addLine("@%s({\"unchecked\", \"varargs\"})", SuppressWarnings.class);
      }
    }
    code.add("public ");
    if (needsSafeVarargs && !overridesVarargsAddMethod) {
      code.add("final ");
    }
    code.add("%s %s(%s... elements) {\n",
            metadata.getBuilder(),
            addMethod(property),
            unboxedType.or(elementType));
    Optional<Class<?>> arrayUtils = code.feature(GUAVA).arrayUtils(unboxedType.or(elementType));
    if (arrayUtils.isPresent()) {
      code.addLine("  return %s(%s.asList(elements));", addAllMethod(property), arrayUtils.get());
    } else {
      // Primitive type, Guava not available
      code.addLine("  for (%s element : elements) {", elementType)
          .addLine("    %s(element);", addMethod(property))
          .addLine("  }")
          .addLine("  return (%s) this;", metadata.getBuilder());
    }
    code.addLine("}");
  }

  private void addSpliteratorAddAll(SourceBuilder code, Metadata metadata) {
    addJavadocForAddAll(code, metadata);
    code.addLine("public %s %s(%s<? extends %s> elements) {",
            metadata.getBuilder(),
            addAllMethod(property),
            Spliterator.class,
            elementType)
        .addLine("  elements.forEachRemaining(this::%s);", addMethod(property))
        .addLine("  return (%s) this;", metadata.getBuilder())
        .addLine("}");
  }

  private void addStreamAddAll(SourceBuilder code, Metadata metadata) {
    addJavadocForAddAll(code, metadata);
    code.addLine("public %s %s(%s<? extends %s, ?> elements) {",
            metadata.getBuilder(),
            addAllMethod(property),
            BaseStream.class,
            elementType)
        .addLine("  return %s(elements.spliterator());", addAllMethod(property))
        .addLine("}");
  }

  private void addIterableAddAll(SourceBuilder code, Metadata metadata) {
    addJavadocForAddAll(code, metadata);
    addAccessorAnnotations(code);
    code.addLine("public %s %s(%s<? extends %s> elements) {",
            metadata.getBuilder(),
            addAllMethod(property),
            Iterable.class,
            elementType)
        .addLine("  elements.forEach(this::%s);", addMethod(property))
        .addLine("  return (%s) this;", metadata.getBuilder())
        .addLine("}");
  }

  private void addJavadocForAddAll(SourceBuilder code, Metadata metadata) {
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
    Block body = methodBody(code, "element");
    if (body.feature(GUAVA).isAvailable()) {
      body.addLine("  if (%s instanceof %s) {", property.getField(), ImmutableSet.class)
          .addLine("    %1$s = new %2$s<>(%1$s);", property.getField(), LinkedHashSet.class)
          .addLine("  }");
    }
    if (unboxedType.isPresent()) {
      body.addLine("  %s.remove(element);", property.getField());
    } else {
      body.addLine("  %s.remove(%s.requireNonNull(element));", property.getField(), Objects.class);
    }
    body.addLine("  return (%s) this;", metadata.getBuilder());
    code.add(body)
        .addLine("}");
  }

  private void addMutator(SourceBuilder code, Metadata metadata) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Applies {@code mutator} to the set to be returned from %s.",
            metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * <p>This method mutates the set in-place. {@code mutator} is a void")
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
            Consumer.class,
            Set.class,
            elementType);
    Block body = methodBody(code, "mutator");
    if (body.feature(GUAVA).isAvailable()) {
      body.addLine("  if (%s instanceof %s) {", property.getField(), ImmutableSet.class)
          .addLine("    %1$s = new %2$s<>(%1$s);", property.getField(), LinkedHashSet.class)
          .addLine("  }");
    }
    if (overridesAddMethod) {
      body.addLine("  mutator.accept(new %s<%s>(%s, this::%s));",
              CheckedSet.TYPE, elementType, property.getField(), addMethod(property));
    } else {
      body.addLine("  // If %s is overridden, this method will be updated to delegate to it",
              addMethod(property))
          .addLine("  mutator.accept(%s);", property.getField());
    }
    body.addLine("  return (%s) this;", metadata.getBuilder());
    code.add(body)
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
        .addLine("public %s %s() {", metadata.getBuilder(), clearMethod(property));
    if (code.feature(GUAVA).isAvailable()) {
      code.addLine("if (%s instanceof %s) {", property.getField(), ImmutableSet.class)
          .addLine("  %s = %s.of();", property.getField(), ImmutableSet.class)
          .addLine("} else {");
    }
    code.addLine("%s.clear();", property.getField());
    if (code.feature(GUAVA).isAvailable()) {
      code.addLine("}");
    }
    code.addLine("  return (%s) this;", metadata.getBuilder())
        .addLine("}");
  }

  private void addGetter(SourceBuilder code, Metadata metadata) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Returns an unmodifiable view of the set that will be returned by")
        .addLine(" * %s.", metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" * Changes to this builder will be reflected in the view.")
        .addLine(" */")
        .addLine("public %s<%s> %s() {", Set.class, elementType, getter(property));
    if (code.feature(GUAVA).isAvailable()) {
      code.addLine("  if (%s instanceof %s) {", property.getField(), ImmutableSet.class)
          .addLine("    %1$s = new %2$s<>(%1$s);", property.getField(), LinkedHashSet.class)
          .addLine("  }");
    }
    code.addLine("  return %s.unmodifiableSet(%s);", Collections.class, property.getField())
        .addLine("}");
  }

  @Override
  public void addFinalFieldAssignment(SourceBuilder code, Excerpt finalField, String builder) {
    Excerpt immutableSetMethod;
    if (code.feature(GUAVA).isAvailable()) {
      immutableSetMethod = Excerpts.add("%s.copyOf", ImmutableSet.class);
    } else {
      immutableSetMethod = ImmutableSetMethod.REFERENCE;
    }
    code.addLine("%s = %s(%s);", finalField, immutableSetMethod, property.getField().on(builder));
  }

  @Override
  public void addMergeFromValue(Block code, String value) {
    if (code.feature(GUAVA).isAvailable()) {
      code.addLine("if (%s instanceof %s && %s == %s.<%s>of()) {",
              value,
              metadata.getValueType().getQualifiedName(),
              property.getField(),
              ImmutableSet.class,
              elementType)
          .addLine("  %s = %s.copyOf(%s.%s());",
              property.getField(), ImmutableSet.class, value, property.getGetterName())
          .addLine("} else {");
    }
    code.addLine("%s(%s.%s());", addAllMethod(property), value, property.getGetterName());
    if (code.feature(GUAVA).isAvailable()) {
      code.addLine("}");
    }
  }

  @Override
  public void addMergeFromBuilder(Block code, String builder) {
    Excerpt base = Declarations.upcastToGeneratedBuilder(code, metadata, builder);
    code.addLine("%s(%s);", addAllMethod(property), property.getField().on(base));
  }

  @Override
  public void addSetFromResult(SourceBuilder code, Excerpt builder, Excerpt variable) {
    code.addLine("%s.%s(%s);", builder, addAllMethod(property), variable);
  }

  @Override
  public void addClearField(Block code) {
    code.addLine("%s();", clearMethod(property));
  }

  private static class ImmutableSetMethod extends Excerpt {

    static final LazyName REFERENCE = new LazyName("immutableSet", new ImmutableSetMethod());

    private ImmutableSetMethod() {}

    @Override
    public void addTo(SourceBuilder code) {
      code.addLine("")
          .addLine("private static <E> %1$s<E> %2$s(%1$s<E> elements) {", Set.class, REFERENCE)
          .addLine("  switch (elements.size()) {")
          .addLine("  case 0:")
          .addLine("    return %s.emptySet();", Collections.class)
          .addLine("  case 1:")
          .addLine("    return %s.singleton(elements.iterator().next());", Collections.class)
          .addLine("  default:")
          .addLine("    return %s.unmodifiableSet(new %s<>(elements));",
              Collections.class, LinkedHashSet.class)
          .addLine("  }")
          .addLine("}");
    }

    @Override
    protected void addFields(FieldReceiver fields) {}
  }
}
