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
package org.inferred.freebuilder.processor.property;

import static org.inferred.freebuilder.processor.BuilderMethods.addAllMethod;
import static org.inferred.freebuilder.processor.BuilderMethods.addCopiesMethod;
import static org.inferred.freebuilder.processor.BuilderMethods.addMethod;
import static org.inferred.freebuilder.processor.BuilderMethods.clearMethod;
import static org.inferred.freebuilder.processor.BuilderMethods.getter;
import static org.inferred.freebuilder.processor.BuilderMethods.mutator;
import static org.inferred.freebuilder.processor.BuilderMethods.setCountMethod;
import static org.inferred.freebuilder.processor.model.ModelUtils.erasesToAnyOf;
import static org.inferred.freebuilder.processor.model.ModelUtils.maybeDeclared;
import static org.inferred.freebuilder.processor.model.ModelUtils.maybeUnbox;
import static org.inferred.freebuilder.processor.model.ModelUtils.needsSafeVarargs;
import static org.inferred.freebuilder.processor.model.ModelUtils.overrides;
import static org.inferred.freebuilder.processor.model.ModelUtils.upperBound;
import static org.inferred.freebuilder.processor.property.MergeAction.appendingToCollections;
import static org.inferred.freebuilder.processor.source.FunctionalType.consumer;
import static org.inferred.freebuilder.processor.source.FunctionalType.functionalTypeAcceptedByMethod;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.stream.BaseStream;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.inferred.freebuilder.processor.Datatype;
import org.inferred.freebuilder.processor.Declarations;
import org.inferred.freebuilder.processor.excerpt.CheckedMultiset;
import org.inferred.freebuilder.processor.source.Excerpt;
import org.inferred.freebuilder.processor.source.FunctionalType;
import org.inferred.freebuilder.processor.source.SourceBuilder;
import org.inferred.freebuilder.processor.source.Type;
import org.inferred.freebuilder.processor.source.Variable;

/** {@link PropertyCodeGenerator} providing fluent methods for {@link Multiset} properties. */
class MultisetProperty extends PropertyCodeGenerator {

  static class Factory implements PropertyCodeGenerator.Factory {

    @Override
    public Optional<MultisetProperty> create(Config config) {
      DeclaredType type = maybeDeclared(config.getProperty().getType()).orElse(null);
      if (!erasesToAnyOf(type, Multiset.class, ImmutableMultiset.class)) {
        return Optional.empty();
      }

      TypeMirror elementType = upperBound(config.getElements(), type.getTypeArguments().get(0));
      Optional<TypeMirror> unboxedType = maybeUnbox(elementType, config.getTypes());
      boolean needsSafeVarargs = needsSafeVarargs(unboxedType.orElse(elementType));
      boolean overridesSetCountMethod =
          hasSetCountMethodOverride(config, unboxedType.orElse(elementType));
      boolean overridesVarargsAddMethod =
          hasVarargsAddMethodOverride(config, unboxedType.orElse(elementType));

      FunctionalType mutatorType =
          functionalTypeAcceptedByMethod(
              config.getBuilder(),
              mutator(config.getProperty()),
              consumer(multiset(elementType, config.getElements(), config.getTypes())),
              config.getElements(),
              config.getTypes());

      return Optional.of(
          new MultisetProperty(
              config.getDatatype(),
              config.getProperty(),
              needsSafeVarargs,
              overridesSetCountMethod,
              overridesVarargsAddMethod,
              elementType,
              unboxedType,
              mutatorType));
    }

    private static boolean hasSetCountMethodOverride(Config config, TypeMirror type) {
      return overrides(
          config.getBuilder(),
          config.getTypes(),
          setCountMethod(config.getProperty()),
          type,
          config.getTypes().getPrimitiveType(TypeKind.INT));
    }

    private static boolean hasVarargsAddMethodOverride(Config config, TypeMirror elementType) {
      return overrides(
          config.getBuilder(),
          config.getTypes(),
          addMethod(config.getProperty()),
          config.getTypes().getArrayType(elementType));
    }

    private static TypeMirror multiset(TypeMirror elementType, Elements elements, Types types) {
      TypeElement multisetType = elements.getTypeElement(Multiset.class.getName());
      return types.getDeclaredType(multisetType, elementType);
    }
  }

  private final boolean needsSafeVarargs;
  private final boolean overridesSetCountMethod;
  private final boolean overridesVarargsAddMethod;
  private final TypeMirror elementType;
  private final Optional<TypeMirror> unboxedType;
  private final FunctionalType mutatorType;

  MultisetProperty(
      Datatype datatype,
      Property property,
      boolean needsSafeVarargs,
      boolean overridesSetCountMethod,
      boolean overridesVarargsAddMethod,
      TypeMirror elementType,
      Optional<TypeMirror> unboxedType,
      FunctionalType mutatorType) {
    super(datatype, property);
    this.needsSafeVarargs = needsSafeVarargs;
    this.overridesSetCountMethod = overridesSetCountMethod;
    this.overridesVarargsAddMethod = overridesVarargsAddMethod;
    this.elementType = elementType;
    this.unboxedType = unboxedType;
    this.mutatorType = mutatorType;
  }

  @Override
  public void addValueFieldDeclaration(SourceBuilder code) {
    code.addLine(
        "private final %s<%s> %s;", ImmutableMultiset.class, elementType, property.getField());
  }

  @Override
  public void addBuilderFieldDeclaration(SourceBuilder code) {
    code.addLine(
        "private final %1$s<%2$s> %3$s = %1$s.create();",
        LinkedHashMultiset.class, elementType, property.getField());
  }

  @Override
  public void addBuilderFieldAccessors(SourceBuilder code) {
    addAdd(code);
    addVarargsAdd(code);
    addSpliteratorAddAll(code);
    addStreamAddAll(code);
    addIterableAddAll(code);
    addAddCopiesTo(code);
    addMutate(code);
    addClear(code);
    addSetCountOf(code);
    addGetter(code);
  }

  private void addAdd(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(
            " * Adds {@code element} to the multiset to be returned from %s.",
            datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName());
    if (!unboxedType.isPresent()) {
      code.addLine(" * @throws NullPointerException if {@code element} is null");
    }
    code.addLine(" */")
        .addLine(
            "public %s %s(%s element) {",
            datatype.getBuilder(), addMethod(property), unboxedType.orElse(elementType))
        .addLine("  %s(element, 1);", addCopiesMethod(property))
        .addLine("  return (%s) this;", datatype.getBuilder())
        .addLine("}");
  }

  private void addVarargsAdd(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Adds each element of {@code elements} to the multiset to be returned from")
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
    code.add(
            "%s %s(%s... elements) {\n",
            datatype.getBuilder(), addMethod(property), unboxedType.orElse(elementType))
        .addLine("  for (%s element : elements) {", unboxedType.orElse(elementType))
        .addLine("    %s(element, 1);", addCopiesMethod(property))
        .addLine("  }")
        .addLine("  return (%s) this;", datatype.getBuilder())
        .addLine("}");
  }

  private void addSpliteratorAddAll(SourceBuilder code) {
    addJavadocForAddAll(code);
    code.addLine(
            "public %s %s(%s<? extends %s> elements) {",
            datatype.getBuilder(), addAllMethod(property), Spliterator.class, elementType)
        .addLine("  elements.forEachRemaining(element -> {")
        .addLine("    %s(element, 1);", addCopiesMethod(property))
        .addLine("  });")
        .addLine("  return (%s) this;", datatype.getBuilder())
        .addLine("}");
  }

  private void addStreamAddAll(SourceBuilder code) {
    addJavadocForAddAll(code);
    code.addLine(
            "public %s %s(%s<? extends %s, ?> elements) {",
            datatype.getBuilder(), addAllMethod(property), BaseStream.class, elementType)
        .addLine("  return %s(elements.spliterator());", addAllMethod(property))
        .addLine("}");
  }

  private void addIterableAddAll(SourceBuilder code) {
    addJavadocForAddAll(code);
    addAccessorAnnotations(code);
    code.addLine(
            "public %s %s(%s<? extends %s> elements) {",
            datatype.getBuilder(), addAllMethod(property), Iterable.class, elementType)
        .addLine("  return %s(elements.spliterator());", addAllMethod(property))
        .addLine("}");
  }

  private void addJavadocForAddAll(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Adds each element of {@code elements} to the multiset to be returned from")
        .addLine(" * %s.", datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName())
        .addLine(" * @throws NullPointerException if {@code elements} is null or contains a")
        .addLine(" *     null element")
        .addLine(" */");
  }

  private void addAddCopiesTo(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Adds a number of occurrences of {@code element} to the multiset to be")
        .addLine(
            " * returned from %s.",
            datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName());
    if (!unboxedType.isPresent()) {
      code.addLine(" * @throws NullPointerException if {@code element} is null");
    }
    code.addLine(" * @throws IllegalArgumentException if {@code occurrences} is negative")
        .addLine(" */")
        .addLine(
            "public %s %s(%s element, int occurrences) {",
            datatype.getBuilder(), addCopiesMethod(property), unboxedType.orElse(elementType))
        .addLine(
            "  %s(element, %s.count(element) + occurrences);",
            setCountMethod(property), property.getField())
        .addLine("  return (%s) this;", datatype.getBuilder())
        .addLine("}");
  }

  private void addMutate(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(
            " * Applies {@code mutator} to the multiset to be returned from %s.",
            datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * <p>This method mutates the multiset in-place. {@code mutator} is a void")
        .addLine(" * consumer, so any value returned from a lambda will be ignored. Take care")
        .addLine(
            " * not to call pure functions, like %s.",
            Type.from(Collection.class).javadocNoArgMethodLink("stream"))
        .addLine(" *")
        .addLine(" * @return this {@code Builder} object")
        .addLine(" * @throws NullPointerException if {@code mutator} is null")
        .addLine(" */")
        .addLine(
            "public %s %s(%s mutator) {",
            datatype.getBuilder(), mutator(property), mutatorType.getFunctionalInterface());
    if (overridesSetCountMethod) {
      code.addLine(
          "  mutator.%s(new %s<>(%s, this::%s));",
          mutatorType.getMethodName(),
          CheckedMultiset.TYPE,
          property.getField(),
          setCountMethod(property));
    } else {
      code.addLine(
              "  // If %s is overridden, this method will be updated to delegate to it",
              setCountMethod(property))
          .addLine("  mutator.%s(%s);", mutatorType.getMethodName(), property.getField());
    }
    code.addLine("  return (%s) this;", datatype.getBuilder()).addLine("}");
  }

  private void addClear(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(
            " * Clears the multiset to be returned from %s.",
            datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName())
        .addLine(" */")
        .addLine("public %s %s() {", datatype.getBuilder(), clearMethod(property))
        .addLine("  %s.clear();", property.getField())
        .addLine("  return (%s) this;", datatype.getBuilder())
        .addLine("}");
  }

  private void addSetCountOf(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Adds or removes the necessary occurrences of {@code element} to/from the")
        .addLine(
            " * multiset to be returned from %s, such that it attains the",
            datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" * desired count.")
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName());
    if (!unboxedType.isPresent()) {
      code.addLine(" * @throws NullPointerException if {@code element} is null");
    }
    code.addLine(" * @throws IllegalArgumentException if {@code occurrences} is negative")
        .addLine(" */")
        .addLine(
            "public %s %s(%s element, int occurrences) {",
            datatype.getBuilder(), setCountMethod(property), unboxedType.orElse(elementType));
    if (!unboxedType.isPresent()) {
      code.addLine("  %s.checkNotNull(element);", Preconditions.class);
    }
    code.addLine("  %s.setCount(element, occurrences);", property.getField())
        .addLine("  return (%s) this;", datatype.getBuilder())
        .addLine("}");
  }

  private void addGetter(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Returns an unmodifiable view of the multiset that will be returned by")
        .addLine(" * %s.", datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" * Changes to this builder will be reflected in the view.")
        .addLine(" */")
        .addLine("public %s<%s> %s() {", Multiset.class, elementType, getter(property))
        .addLine("  return %s.unmodifiableMultiset(%s);", Multisets.class, property.getField())
        .addLine("}");
  }

  @Override
  public void addFinalFieldAssignment(SourceBuilder code, Excerpt finalField, String builder) {
    code.addLine(
        "%s = %s.copyOf(%s);",
        finalField, ImmutableMultiset.class, property.getField().on(builder));
  }

  @Override
  public void addAssignToBuilder(SourceBuilder code, Variable builder) {
    code.addLine("%s.addAll(%s);", property.getField().on(builder), property.getField());
  }

  @Override
  public void addMergeFromValue(SourceBuilder code, String value) {
    code.addLine("%s(%s.%s());", addAllMethod(property), value, property.getGetterName());
  }

  @Override
  public void addMergeFromBuilder(SourceBuilder code, String builder) {
    Excerpt base = Declarations.upcastToGeneratedBuilder(code, datatype, builder);
    code.addLine("%s(%s);", addAllMethod(property), property.getField().on(base));
  }

  @Override
  public Set<MergeAction> getMergeActions() {
    return ImmutableSet.of(appendingToCollections());
  }

  @Override
  public void addSetFromResult(SourceBuilder code, Excerpt builder, Excerpt variable) {
    code.addLine("%s.%s(%s);", builder, addAllMethod(property), variable);
  }

  @Override
  public void addClearField(SourceBuilder code) {
    code.addLine("%s.clear();", property.getField());
  }
}
