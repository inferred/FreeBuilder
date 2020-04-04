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

import static org.inferred.freebuilder.processor.BuilderMethods.clearMethod;
import static org.inferred.freebuilder.processor.BuilderMethods.forcePutMethod;
import static org.inferred.freebuilder.processor.BuilderMethods.getter;
import static org.inferred.freebuilder.processor.BuilderMethods.mutator;
import static org.inferred.freebuilder.processor.BuilderMethods.putAllMethod;
import static org.inferred.freebuilder.processor.BuilderMethods.putMethod;
import static org.inferred.freebuilder.processor.BuilderMethods.removeKeyFromMethod;
import static org.inferred.freebuilder.processor.BuilderMethods.removeValueFromMethod;
import static org.inferred.freebuilder.processor.model.ModelUtils.erasesToAnyOf;
import static org.inferred.freebuilder.processor.model.ModelUtils.maybeDeclared;
import static org.inferred.freebuilder.processor.model.ModelUtils.maybeUnbox;
import static org.inferred.freebuilder.processor.model.ModelUtils.overrides;
import static org.inferred.freebuilder.processor.model.ModelUtils.upperBound;
import static org.inferred.freebuilder.processor.property.MergeAction.appendingToCollections;
import static org.inferred.freebuilder.processor.source.FunctionalType.consumer;
import static org.inferred.freebuilder.processor.source.FunctionalType.functionalTypeAcceptedByMethod;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import org.inferred.freebuilder.processor.Datatype;
import org.inferred.freebuilder.processor.Declarations;
import org.inferred.freebuilder.processor.excerpt.CheckedBiMap;
import org.inferred.freebuilder.processor.source.Excerpt;
import org.inferred.freebuilder.processor.source.FunctionalType;
import org.inferred.freebuilder.processor.source.SourceBuilder;
import org.inferred.freebuilder.processor.source.Type;
import org.inferred.freebuilder.processor.source.Variable;

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

/**
 * {@link PropertyCodeGenerator} providing fluent methods for {@link BiMap} properties.
 */
class BiMapProperty extends PropertyCodeGenerator {

  static class Factory implements PropertyCodeGenerator.Factory {

    @Override
    public Optional<BiMapProperty> create(Config config) {
      Property property = config.getProperty();
      DeclaredType type = maybeDeclared(property.getType()).orElse(null);
      if (!erasesToAnyOf(type, BiMap.class, ImmutableBiMap.class)) {
        return Optional.empty();
      }
      TypeMirror keyType = upperBound(config.getElements(), type.getTypeArguments().get(0));
      TypeMirror valueType = upperBound(config.getElements(), type.getTypeArguments().get(1));
      Optional<TypeMirror> unboxedKeyType = maybeUnbox(keyType, config.getTypes());
      Optional<TypeMirror> unboxedValueType = maybeUnbox(valueType, config.getTypes());
      boolean overridesForcePutMethod = hasForcePutMethodOverride(
          config, unboxedKeyType.orElse(keyType), unboxedValueType.orElse(valueType));

      FunctionalType mutatorType = functionalTypeAcceptedByMethod(
          config.getBuilder(),
          mutator(property),
          consumer(biMap(keyType, valueType, config.getElements(), config.getTypes())),
          config.getElements(),
          config.getTypes());

      return Optional.of(new BiMapProperty(
          config.getDatatype(),
          property,
          overridesForcePutMethod,
          keyType,
          unboxedKeyType,
          valueType,
          unboxedValueType,
          mutatorType));
    }

    private static boolean hasForcePutMethodOverride(
        Config config, TypeMirror keyType, TypeMirror valueType) {
      return overrides(
          config.getBuilder(),
          config.getTypes(),
          forcePutMethod(config.getProperty()),
          keyType,
          valueType);
    }

    private static TypeMirror biMap(
        TypeMirror keyType,
        TypeMirror valueType,
        Elements elements,
        Types types) {
      TypeElement mapType = elements.getTypeElement(BiMap.class.getName());
      return types.getDeclaredType(mapType, keyType, valueType);
    }
  }

  private final boolean overridesForcePutMethod;
  private final TypeMirror keyType;
  private final Optional<TypeMirror> unboxedKeyType;
  private final TypeMirror valueType;
  private final Optional<TypeMirror> unboxedValueType;
  private final FunctionalType mutatorType;

  BiMapProperty(
      Datatype datatype,
      Property property,
      boolean overridesForcePutMethod,
      TypeMirror keyType,
      Optional<TypeMirror> unboxedKeyType,
      TypeMirror valueType,
      Optional<TypeMirror> unboxedValueType,
      FunctionalType mutatorType) {
    super(datatype, property);
    this.overridesForcePutMethod = overridesForcePutMethod;
    this.keyType = keyType;
    this.unboxedKeyType = unboxedKeyType;
    this.valueType = valueType;
    this.unboxedValueType = unboxedValueType;
    this.mutatorType = mutatorType;
  }

  @Override
  public void addValueFieldDeclaration(SourceBuilder code) {
    code.addLine("private final %s<%s, %s> %s;",
        ImmutableBiMap.class, keyType, valueType, property.getField());
  }

  @Override
  public void addBuilderFieldDeclaration(SourceBuilder code) {
    code.addLine("private final %1$s<%2$s, %3$s> %4$s = %1$s.create();",
        HashBiMap.class, keyType, valueType, property.getField());
  }

  @Override
  public void addBuilderFieldAccessors(SourceBuilder code) {
    addPut(code);
    addForcePut(code);
    addPutAll(code);
    addRemoveKeyFrom(code);
    addRemoveValueFrom(code);
    addMutate(code);
    addClear(code);
    addGetter(code);
  }

  private void addPut(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Associates {@code key} with {@code value} in the bimap to be returned from")
        .addLine(" * %s.", datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" * If the bimap previously contained a mapping for the key,")
        .addLine(" * the old value is replaced by the specified value.")
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName())
        .addLine(" * @throws IllegalArgumentException if the given value is already bound to a")
        .addLine(" *     different key in this bimap. The bimap will remain unmodified in this")
        .addLine(" *     event. To avoid this exception, call {@link #forcePut} instead.");
    if (!unboxedKeyType.isPresent() || !unboxedValueType.isPresent()) {
      code.add(" * @throws NullPointerException if ");
      if (unboxedKeyType.isPresent()) {
        code.add("{@code value} is");
      } else if (unboxedValueType.isPresent()) {
        code.add("{@code key} is");
      } else {
        code.add("either {@code key} or {@code value} are");
      }
      code.add(" null\n");
    }
    code.addLine(" */");
    addPutAnnotations(code);
    code.addLine(
        "public %s %s(%s key, %s value) {",
        datatype.getBuilder(),
        putMethod(property),
        unboxedKeyType.orElse(keyType),
        unboxedValueType.orElse(valueType));
    if (!unboxedKeyType.isPresent()) {
      code.addLine("  %s.requireNonNull(key);", Objects.class);
    }
    if (!unboxedValueType.isPresent()) {
      code.addLine("  %s.requireNonNull(value);", Objects.class);
    }
    code.addLine("  %s.checkArgument(", Preconditions.class)
        .addLine("      !%s.containsValue(value), \"value already present: %%s\", value);",
            property.getField())
        .addLine("  %s(key, value);", forcePutMethod(property))
        .addLine("  return (%s) this;", datatype.getBuilder())
        .addLine("}");
  }

  private void addForcePut(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Associates {@code key} with {@code value} in the bimap to be returned from")
        .addLine(" * %s.", datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" * See {@link BiMap#forcePut(Object, Object)}.")
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName());
    if (!unboxedKeyType.isPresent() || !unboxedValueType.isPresent()) {
      code.add(" * @throws NullPointerException if ");
      if (unboxedKeyType.isPresent()) {
        code.add("{@code value} is");
      } else if (unboxedValueType.isPresent()) {
        code.add("{@code key} is");
      } else {
        code.add("either {@code key} or {@code value} are");
      }
      code.add(" null\n");
    }
    code.addLine(" */");
    addPutAnnotations(code);
    code.addLine("public %s %s(%s key, %s value) {",
            datatype.getBuilder(),
            forcePutMethod(property),
            unboxedKeyType.orElse(keyType),
            unboxedValueType.orElse(valueType));
    if (!unboxedKeyType.isPresent()) {
      code.addLine("  %s.requireNonNull(key);", Objects.class);
    }
    if (!unboxedValueType.isPresent()) {
      code.addLine("  %s.requireNonNull(value);", Objects.class);
    }
    code.addLine("  %s.forcePut(key, value);", property.getField())
        .addLine("  return (%s) this;", datatype.getBuilder())
        .addLine("}");
  }

  private void addPutAll(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Copies all of the mappings from {@code map} to the bimap to be returned ")
        .addLine(" * from %s.",
            datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName())
        .addLine(" * @throws NullPointerException if {@code map} is null or contains a")
        .addLine(" *     null key or value")
        .addLine(" * @throws IllegalArgumentException if an attempt to {@code put} any")
        .addLine(" *     entry fails. Note that some map entries may have been added to the")
        .addLine(" *     bimap before the exception was thrown.")
        .addLine(" */");
    addAccessorAnnotations(code);
    code.addLine("public %s %s(%s<? extends %s, ? extends %s> map) {",
            datatype.getBuilder(), putAllMethod(property), Map.class, keyType, valueType)
        .addLine("  for (%s<? extends %s, ? extends %s> entry : map.entrySet()) {",
            Map.Entry.class, keyType, valueType)
        .addLine("    %s(entry.getKey(), entry.getValue());", putMethod(property))
        .addLine("  }")
        .addLine("  return (%s) this;", datatype.getBuilder())
        .addLine("}");
  }

  private void addRemoveKeyFrom(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Removes the mapping for {@code key} from the bimap to be returned from")
        .addLine(" * %s, if one is present.",
            datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName());
    if (!unboxedKeyType.isPresent()) {
      code.addLine(" * @throws NullPointerException if {@code key} is null");
    }
    code.addLine(" */")
        .addLine("public %s %s(%s key) {",
            datatype.getBuilder(), removeKeyFromMethod(property), unboxedKeyType.orElse(keyType));
    if (!unboxedKeyType.isPresent()) {
      code.addLine("  %s.requireNonNull(key);", Objects.class);
    }
    code.addLine("  %s.remove(key);", property.getField())
        .addLine("  return (%s) this;", datatype.getBuilder())
        .addLine("}");
  }

  private void addRemoveValueFrom(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Removes the mapping for {@code value} from the bimap to be returned from")
        .addLine(" * %s, if one is present.",
            datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName());
    if (!unboxedValueType.isPresent()) {
      code.addLine(" * @throws NullPointerException if {@code value} is null");
    }
    code.addLine(" */")
        .addLine("public %s %s(%s value) {",
            datatype.getBuilder(),
            removeValueFromMethod(property),
            unboxedValueType.orElse(valueType));
    if (!unboxedValueType.isPresent()) {
      code.addLine("  %s.requireNonNull(value);", Objects.class);
    }
    code.addLine("  %s.inverse().remove(value);", property.getField())
        .addLine("  return (%s) this;", datatype.getBuilder())
        .addLine("}");
  }

  private void addMutate(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Invokes {@code mutator} with the bimap to be returned from")
        .addLine(" * %s.", datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * <p>This method mutates the bimap in-place. {@code mutator} is a void")
        .addLine(" * consumer, so any value returned from a lambda will be ignored. Take care")
        .addLine(" * not to call pure functions, like %s.",
            Type.from(Collection.class).javadocNoArgMethodLink("stream"))
        .addLine(" *")
        .addLine(" * @return this {@code Builder} object")
        .addLine(" * @throws NullPointerException if {@code mutator} is null")
        .addLine(" */")
        .addLine("public %s %s(%s mutator) {",
            datatype.getBuilder(), mutator(property), mutatorType.getFunctionalInterface());
    if (overridesForcePutMethod) {
      code.addLine("  mutator.%s(new %s<>(%s, this::%s));",
          mutatorType.getMethodName(),
          CheckedBiMap.TYPE,
          property.getField(),
          forcePutMethod(property));
    } else {
      code.addLine("  // If %s is overridden, this method will be updated to delegate to it",
              forcePutMethod(property))
          .addLine("  mutator.%s(%s);", mutatorType.getMethodName(), property.getField());
    }
    code.addLine("  return (%s) this;", datatype.getBuilder())
        .addLine("}");
  }

  private void addClear(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Removes all of the mappings from the bimap to be returned from ")
        .addLine(" * %s.", datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName())
        .addLine(" */")
        .addLine("public %s %s() {", datatype.getBuilder(), clearMethod(property))
        .addLine("  %s.clear();", property.getField())
        .addLine("  return (%s) this;", datatype.getBuilder())
        .addLine("}");
  }

  private void addGetter(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Returns an unmodifiable view of the bimap that will be returned by")
        .addLine(" * %s.", datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" * Changes to this builder will be reflected in the view.")
        .addLine(" */")
        .addLine("public %s<%s, %s> %s() {", BiMap.class, keyType, valueType, getter(property))
        .addLine("  return %s.unmodifiableBiMap(%s);", Maps.class, property.getField())
        .addLine("}");
  }

  @Override
  public void addFinalFieldAssignment(SourceBuilder code, Excerpt finalField, String builder) {
    code.addLine("%s = %s.copyOf(%s);",
        finalField, ImmutableBiMap.class, property.getField().on(builder)
    );
  }

  @Override
  public void addAssignToBuilder(SourceBuilder code, Variable builder) {
    code.addLine("%s.putAll(%s);", property.getField().on(builder), property.getField());
  }

  @Override
  public void addMergeFromValue(SourceBuilder code, String value) {
    code.addLine("%s(%s.%s());", putAllMethod(property), value, property.getGetterName());
  }

  @Override
  public void addMergeFromBuilder(SourceBuilder code, String builder) {
    Excerpt base = Declarations.upcastToGeneratedBuilder(code, datatype, builder);
    code.addLine("%s(%s);", putAllMethod(property), property.getField().on(base));
  }

  @Override
  public Set<MergeAction> getMergeActions() {
    return ImmutableSet.of(appendingToCollections());
  }

  @Override
  public void addSetFromResult(SourceBuilder code, Excerpt builder, Excerpt variable) {
    code.addLine("%s.%s(%s);", builder, putAllMethod(property), variable);
  }

  @Override
  public void addClearField(SourceBuilder code) {
    code.addLine("%s.clear();", property.getField());
  }
}
