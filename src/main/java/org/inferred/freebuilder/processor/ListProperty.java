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
import static org.inferred.freebuilder.processor.util.Block.methodBody;
import static org.inferred.freebuilder.processor.util.FunctionalType.consumer;
import static org.inferred.freebuilder.processor.util.FunctionalType.functionalTypeAcceptedByMethod;
import static org.inferred.freebuilder.processor.util.ModelUtils.maybeDeclared;
import static org.inferred.freebuilder.processor.util.ModelUtils.maybeUnbox;
import static org.inferred.freebuilder.processor.util.ModelUtils.needsSafeVarargs;
import static org.inferred.freebuilder.processor.util.ModelUtils.overrides;
import static org.inferred.freebuilder.processor.util.feature.FunctionPackage.FUNCTION_PACKAGE;
import static org.inferred.freebuilder.processor.util.feature.GuavaLibrary.GUAVA;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

import org.inferred.freebuilder.processor.excerpt.CheckedList;
import org.inferred.freebuilder.processor.util.Block;
import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.Excerpts;
import org.inferred.freebuilder.processor.util.FunctionalType;
import org.inferred.freebuilder.processor.util.LazyName;
import org.inferred.freebuilder.processor.util.ParameterizedType;
import org.inferred.freebuilder.processor.util.QualifiedName;
import org.inferred.freebuilder.processor.util.SourceBuilder;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.stream.BaseStream;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * {@link PropertyCodeGenerator} providing fluent methods for {@link List} properties.
 */
class ListProperty extends PropertyCodeGenerator {
  static class Factory implements PropertyCodeGenerator.Factory {

    @Override
    public Optional<ListProperty> create(Config config) {
      DeclaredType type = maybeDeclared(config.getProperty().getType()).orNull();
      if (type == null || !erasesToAnyOf(type, Collection.class, List.class, ImmutableList.class)) {
        return Optional.absent();
      }

      TypeMirror elementType = upperBound(config.getElements(), type.getTypeArguments().get(0));
      Optional<TypeMirror> unboxedType = maybeUnbox(elementType, config.getTypes());
      boolean needsSafeVarargs = needsSafeVarargs(unboxedType.or(elementType));
      boolean overridesAddMethod = hasAddMethodOverride(config, unboxedType.or(elementType));
      boolean overridesVarargsAddMethod =
          hasVarargsAddMethodOverride(config, unboxedType.or(elementType));

      FunctionalType mutatorType = functionalTypeAcceptedByMethod(
          config.getBuilder(),
          mutator(config.getProperty()),
          consumer(wildcardSuperList(elementType, config.getElements(), config.getTypes())),
          config.getElements(),
          config.getTypes());

      return Optional.of(new ListProperty(
          config.getDatatype(),
          config.getProperty(),
          needsSafeVarargs,
          overridesAddMethod,
          overridesVarargsAddMethod,
          elementType,
          unboxedType,
          mutatorType));
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

    /**
     * Returns {@code ? super List<elementType>}.
     */
    private static TypeMirror wildcardSuperList(
        TypeMirror elementType,
        Elements elements,
        Types types) {
      TypeElement listType = elements.getTypeElement(List.class.getName());
      return types.getWildcardType(null, types.getDeclaredType(listType, elementType));
    }
  }

  private static final ParameterizedType COLLECTION =
      QualifiedName.of(Collection.class).withParameters("E");

  private final boolean needsSafeVarargs;
  private final boolean overridesAddMethod;
  private final boolean overridesVarargsAddMethod;
  private final TypeMirror elementType;
  private final Optional<TypeMirror> unboxedType;
  private final FunctionalType mutatorType;

  @VisibleForTesting
  ListProperty(
      Datatype datatype,
      Property property,
      boolean needsSafeVarargs,
      boolean overridesAddMethod,
      boolean overridesVarargsAddMethod,
      TypeMirror elementType,
      Optional<TypeMirror> unboxedType,
      FunctionalType mutatorType) {
    super(datatype, property);
    this.needsSafeVarargs = needsSafeVarargs;
    this.overridesAddMethod = overridesAddMethod;
    this.overridesVarargsAddMethod = overridesVarargsAddMethod;
    this.elementType = elementType;
    this.unboxedType = unboxedType;
    this.mutatorType = mutatorType;
  }

  @Override
  public void addBuilderFieldDeclaration(SourceBuilder code) {
    if (code.feature(GUAVA).isAvailable()) {
      code.addLine("private %s<%s> %s = %s.of();",
          List.class,
          elementType,
          property.getField(),
          ImmutableList.class);
    } else {
      code.addLine("private final %1$s<%2$s> %3$s = new %1$s<>();",
          ArrayList.class,
          elementType,
          property.getField());
    }
  }

  @Override
  public void addBuilderFieldAccessors(SourceBuilder code) {
    addAdd(code);
    addVarargsAdd(code);
    addSpliteratorAddAll(code);
    addStreamAddAll(code);
    addIterableAddAll(code);
    addMutate(code);
    addClear(code);
    addGetter(code);
  }

  private void addAdd(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Adds {@code element} to the list to be returned from %s.",
            datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName());
    if (!unboxedType.isPresent()) {
      code.addLine(" * @throws NullPointerException if {@code element} is null");
    }
    code.addLine(" */")
        .addLine("public %s %s(%s element) {",
            datatype.getBuilder(), addMethod(property), unboxedType.or(elementType));
    Block body = methodBody(code, "element");
    if (body.feature(GUAVA).isAvailable()) {
      body.addLine("  if (%s instanceof %s) {", property.getField(), ImmutableList.class)
          .addLine("    %1$s = new %2$s<>(%1$s);", property.getField(), ArrayList.class)
          .addLine("  }");
    }
    if (unboxedType.isPresent()) {
      body.addLine("  %s.add(element);", property.getField());
    } else {
      body.addLine("  %s.add(%s.requireNonNull(element));", property.getField(), Objects.class);
    }
    body.addLine("  return (%s) this;", datatype.getBuilder());
    code.add(body)
        .addLine("}");
  }

  private void addVarargsAdd(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Adds each element of {@code elements} to the list to be returned from")
        .addLine(" * %s.", datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName());
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
            datatype.getBuilder(),
            addMethod(property),
            unboxedType.or(elementType));
    Block body = methodBody(code, "elements");
    Optional<Class<?>> arrayUtils = body.feature(GUAVA).arrayUtils(unboxedType.or(elementType));
    if (arrayUtils.isPresent()) {
      body.addLine("  return %s(%s.asList(elements));", addAllMethod(property), arrayUtils.get());
    } else {
      // Primitive type, Guava not available
      body.addLine("  %1$s.ensureCapacity(%1$s.size() + elements.length);", property.getField())
          .addLine("  for (%s element : elements) {", unboxedType.get())
          .addLine("    %s(element);", addMethod(property))
          .addLine("  }")
          .addLine("  return (%s) this;", datatype.getBuilder());
    }
    code.add(body)
        .addLine("}");
  }

  private void addSpliteratorAddAll(SourceBuilder code) {
    addJavadocForAddAll(code);
    code.addLine("public %s %s(%s<? extends %s> elements) {",
            datatype.getBuilder(),
            addAllMethod(property),
            Spliterator.class,
            elementType);
    Block body = methodBody(code, "elements");
    body.addLine("  if ((elements.characteristics() & %s.SIZED) != 0) {", Spliterator.class)
        .addLine("    long elementsSize = elements.estimateSize();")
        .addLine("    if (elementsSize > 0 && elementsSize <= Integer.MAX_VALUE) {");
    if (body.feature(GUAVA).isAvailable()) {
      body.addLine("      if (%s instanceof %s) {", property.getField(), ImmutableList.class)
          .addLine("        %1$s = new %2$s<>(%1$s);", property.getField(), ArrayList.class)
          .addLine("      }")
          .add("      ((%s<?>) %s)", ArrayList.class, property.getField());
    } else {
      body.add("      %s", property.getField());
    }
    body.add(".ensureCapacity(%s.size() + (int) elementsSize);%n", property.getField())
        .addLine("    }")
        .addLine("  }")
        .addLine("  elements.forEachRemaining(this::%s);", addMethod(property))
        .addLine("  return (%s) this;", datatype.getBuilder());
    code.add(body)
        .addLine("}");
  }

  private void addIterableAddAll(SourceBuilder code) {
    addJavadocForAddAll(code);
    addAccessorAnnotations(code);
    code.addLine("public %s %s(%s<? extends %s> elements) {",
            datatype.getBuilder(),
            addAllMethod(property),
            Iterable.class,
            elementType)
        .addLine("  return %s(elements.spliterator());", addAllMethod(property))
        .addLine("}");
  }

  private void addStreamAddAll(SourceBuilder code) {
    addJavadocForAddAll(code);
    code.addLine("public %s %s(%s<? extends %s, ?> elements) {",
            datatype.getBuilder(),
            addAllMethod(property),
            BaseStream.class,
            elementType)
        .addLine("  return %s(elements.spliterator());", addAllMethod(property))
        .addLine("}");
  }

  private void addJavadocForAddAll(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Adds each element of {@code elements} to the list to be returned from")
        .addLine(" * %s.", datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName())
        .addLine(" * @throws NullPointerException if {@code elements} is null or contains a")
        .addLine(" *     null element")
        .addLine(" */");
  }

  private void addMutate(SourceBuilder code) {
    if (!code.feature(FUNCTION_PACKAGE).consumer().isPresent()) {
      return;
    }
    code.addLine("")
        .addLine("/**")
        .addLine(" * Applies {@code mutator} to the list to be returned from %s.",
            datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * <p>This method mutates the list in-place. {@code mutator} is a void")
        .addLine(" * consumer, so any value returned from a lambda will be ignored. Take care")
        .addLine(" * not to call pure functions, like %s.",
            COLLECTION.javadocNoArgMethodLink("stream"))
        .addLine(" *")
        .addLine(" * @return this {@code Builder} object")
        .addLine(" * @throws NullPointerException if {@code mutator} is null")
        .addLine(" */")
        .addLine("public %s %s(%s mutator) {",
            datatype.getBuilder(),
            mutator(property),
            mutatorType.getFunctionalInterface());
    Block body = methodBody(code, "mutator");
    if (body.feature(GUAVA).isAvailable()) {
      body.addLine("  if (%s instanceof %s) {", property.getField(), ImmutableList.class)
          .addLine("    %1$s = new %2$s<>(%1$s);", property.getField(), ArrayList.class)
          .addLine("  }");
    }
    if (overridesAddMethod) {
      body.addLine("  mutator.%s(new %s<>(%s, this::%s));",
          mutatorType.getMethodName(), CheckedList.TYPE, property.getField(), addMethod(property));
    } else {
      body.addLine("  // If %s is overridden, this method will be updated to delegate to it",
              addMethod(property))
          .addLine("  mutator.%s(%s);", mutatorType.getMethodName(), property.getField());
    }
    body.addLine("  return (%s) this;", datatype.getBuilder());
    code.add(body)
        .addLine("}");
  }

  private void addClear(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Clears the list to be returned from %s.",
            datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName())
        .addLine(" */")
        .addLine("public %s %s() {", datatype.getBuilder(), clearMethod(property));
    if (code.feature(GUAVA).isAvailable()) {
      code.addLine("  if (%s instanceof %s) {", property.getField(), ImmutableList.class)
          .addLine("    %s = %s.of();", property.getField(), ImmutableList.class)
          .addLine("  } else {");
    }
    code.addLine("    %s.clear();", property.getField());
    if (code.feature(GUAVA).isAvailable()) {
      code.addLine("  }");
    }
    code.addLine("  return (%s) this;", datatype.getBuilder())
        .addLine("}");
  }

  private void addGetter(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Returns an unmodifiable view of the list that will be returned by")
        .addLine(" * %s.", datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" * Changes to this builder will be reflected in the view.")
        .addLine(" */")
        .addLine("public %s<%s> %s() {", List.class, elementType, getter(property));
    if (code.feature(GUAVA).isAvailable()) {
      code.addLine("  if (%s instanceof %s) {", property.getField(), ImmutableList.class)
          .addLine("    %1$s = new %2$s<>(%1$s);", property.getField(), ArrayList.class)
          .addLine("  }");
    }
    code.addLine("  return %s.unmodifiableList(%s);", Collections.class, property.getField())
        .addLine("}");
  }

  @Override
  public void addFinalFieldAssignment(SourceBuilder code, Excerpt finalField, String builder) {
    Excerpt immutableListMethod;
    if (code.feature(GUAVA).isAvailable()) {
      immutableListMethod = Excerpts.add("%s.copyOf", ImmutableList.class);
    } else {
      immutableListMethod = ImmutableListMethod.REFERENCE;
    }
    code.addLine("%s = %s(%s);", finalField, immutableListMethod, property.getField().on(builder));
  }

  @Override
  public void addMergeFromValue(Block code, String value) {
    if (code.feature(GUAVA).isAvailable()) {
      code.addLine("if (%s instanceof %s && %s == %s.<%s>of()) {",
              value,
              datatype.getValueType().getQualifiedName(),
              property.getField(),
              ImmutableList.class,
              elementType)
          .addLine("  %s = %s.copyOf(%s.%s());",
              property.getField(), ImmutableList.class, value, property.getGetterName())
          .addLine("} else {");
    }
    code.addLine("%s(%s.%s());", addAllMethod(property), value, property.getGetterName());
    if (code.feature(GUAVA).isAvailable()) {
      code.addLine("}");
    }
  }

  @Override
  public void addMergeFromBuilder(Block code, String builder) {
    Excerpt base = Declarations.upcastToGeneratedBuilder(code, datatype, builder);
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

  private static class ImmutableListMethod extends Excerpt {

    static final LazyName REFERENCE = new LazyName("immutableList", new ImmutableListMethod());

    private ImmutableListMethod() {}

    @Override
    public void addTo(SourceBuilder code) {
      code.addLine("")
          .addLine("@%s(\"unchecked\")", SuppressWarnings.class)
          .addLine("private static <E> %1$s<E> %2$s(%1$s<E> elements) {", List.class, REFERENCE)
          .addLine("  switch (elements.size()) {")
          .addLine("  case 0:")
          .addLine("    return %s.emptyList();", Collections.class)
          .addLine("  case 1:")
          .addLine("    return %s.singletonList(elements.get(0));", Collections.class)
          .addLine("  default:")
          .addLine("    return (%1$s<E>)(%1$s<?>) %2$s.unmodifiableList(%3$s.asList(",
              List.class, Collections.class, Arrays.class)
          .addLine("        elements.toArray()));", Array.class)
          .addLine("  }")
          .addLine("}");
    }

    @Override
    protected void addFields(FieldReceiver fields) {}
  }
}
