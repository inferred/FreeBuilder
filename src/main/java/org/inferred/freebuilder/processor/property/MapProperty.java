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
import static org.inferred.freebuilder.processor.BuilderMethods.getter;
import static org.inferred.freebuilder.processor.BuilderMethods.mutator;
import static org.inferred.freebuilder.processor.BuilderMethods.putAllMethod;
import static org.inferred.freebuilder.processor.BuilderMethods.putMethod;
import static org.inferred.freebuilder.processor.BuilderMethods.removeMethod;
import static org.inferred.freebuilder.processor.Util.erasesToAnyOf;
import static org.inferred.freebuilder.processor.Util.upperBound;
import static org.inferred.freebuilder.processor.util.FunctionalType.consumer;
import static org.inferred.freebuilder.processor.util.FunctionalType.functionalTypeAcceptedByMethod;
import static org.inferred.freebuilder.processor.util.ModelUtils.maybeDeclared;
import static org.inferred.freebuilder.processor.util.ModelUtils.maybeUnbox;
import static org.inferred.freebuilder.processor.util.ModelUtils.overrides;
import static org.inferred.freebuilder.processor.util.feature.GuavaLibrary.GUAVA;

import com.google.common.collect.ImmutableMap;

import org.inferred.freebuilder.processor.Datatype;
import org.inferred.freebuilder.processor.Declarations;
import org.inferred.freebuilder.processor.excerpt.CheckedMap;
import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.Excerpts;
import org.inferred.freebuilder.processor.util.FunctionalType;
import org.inferred.freebuilder.processor.util.LazyName;
import org.inferred.freebuilder.processor.util.SourceBuilder;
import org.inferred.freebuilder.processor.util.Type;
import org.inferred.freebuilder.processor.util.ValueType;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * {@link PropertyCodeGenerator} providing fluent methods for {@link Map} properties.
 */
class MapProperty extends PropertyCodeGenerator {

  static class Factory implements PropertyCodeGenerator.Factory {

    @Override
    public Optional<MapProperty> create(Config config) {
      Property property = config.getProperty();
      DeclaredType type = maybeDeclared(property.getType()).orElse(null);
      if (type == null || !erasesToAnyOf(type, Map.class, ImmutableMap.class)) {
        return Optional.empty();
      }
      TypeMirror keyType = upperBound(config.getElements(), type.getTypeArguments().get(0));
      TypeMirror valueType = upperBound(config.getElements(), type.getTypeArguments().get(1));
      Optional<TypeMirror> unboxedKeyType = maybeUnbox(keyType, config.getTypes());
      Optional<TypeMirror> unboxedValueType = maybeUnbox(valueType, config.getTypes());
      boolean overridesPutMethod = hasPutMethodOverride(
          config, unboxedKeyType.orElse(keyType), unboxedValueType.orElse(valueType));

      FunctionalType mutatorType = functionalTypeAcceptedByMethod(
          config.getBuilder(),
          mutator(property),
          consumer(wildcardSuperMap(keyType, valueType, config.getElements(), config.getTypes())),
          config.getElements(),
          config.getTypes());

      return Optional.of(new MapProperty(
          config.getDatatype(),
          property,
          overridesPutMethod,
          keyType,
          unboxedKeyType,
          valueType,
          unboxedValueType,
          mutatorType));
    }

    private static boolean hasPutMethodOverride(
        Config config, TypeMirror keyType, TypeMirror valueType) {
      return overrides(
          config.getBuilder(),
          config.getTypes(),
          putMethod(config.getProperty()),
          keyType,
          valueType);
    }

    private static TypeMirror wildcardSuperMap(
        TypeMirror keyType,
        TypeMirror valueType,
        Elements elements,
        Types types) {
      TypeElement mapType = elements.getTypeElement(Map.class.getName());
      return types.getWildcardType(null, types.getDeclaredType(mapType, keyType, valueType));
    }
  }

  private final boolean overridesPutMethod;
  private final TypeMirror keyType;
  private final Optional<TypeMirror> unboxedKeyType;
  private final TypeMirror valueType;
  private final Optional<TypeMirror> unboxedValueType;
  private final FunctionalType mutatorType;

  MapProperty(
      Datatype datatype,
      Property property,
      boolean overridesPutMethod,
      TypeMirror keyType,
      Optional<TypeMirror> unboxedKeyType,
      TypeMirror valueType,
      Optional<TypeMirror> unboxedValueType,
      FunctionalType mutatorType) {
    super(datatype, property);
    this.overridesPutMethod = overridesPutMethod;
    this.keyType = keyType;
    this.unboxedKeyType = unboxedKeyType;
    this.valueType = valueType;
    this.unboxedValueType = unboxedValueType;
    this.mutatorType = mutatorType;
  }

  @Override
  public void addBuilderFieldDeclaration(SourceBuilder code) {
    code.addLine("private final %1$s<%2$s, %3$s> %4$s = new %1$s<>();",
        LinkedHashMap.class,
        keyType,
        valueType,
        property.getField());
  }

  @Override
  public void addBuilderFieldAccessors(SourceBuilder code) {
    addPut(code);
    addPutAll(code);
    addRemove(code);
    addMutate(code);
    addClear(code);
    addGetter(code);
  }

  private void addPut(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Associates {@code key} with {@code value} in the map to be returned from")
        .addLine(" * %s.", datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" * If the map previously contained a mapping for the key,")
        .addLine(" * the old value is replaced by the specified value.")
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
    code.addLine(" */")
        .addLine("public %s %s(%s key, %s value) {",
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
    code.addLine("  %s.put(key, value);", property.getField())
        .addLine("  return (%s) this;", datatype.getBuilder())
        .addLine("}");
  }

  private void addPutAll(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Copies all of the mappings from {@code map} to the map to be returned from")
        .addLine(" * %s.", datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName())
        .addLine(" * @throws NullPointerException if {@code map} is null or contains a")
        .addLine(" *     null key or value")
        .addLine(" */");
    addAccessorAnnotations(code);
    code.addLine("public %s %s(%s<? extends %s, ? extends %s> map) {",
            datatype.getBuilder(),
            putAllMethod(property),
            Map.class,
            keyType,
            valueType)
        .addLine("  for (%s<? extends %s, ? extends %s> entry : map.entrySet()) {",
            Map.Entry.class, keyType, valueType)
        .addLine("    %s(entry.getKey(), entry.getValue());", putMethod(property))
        .addLine("  }")
        .addLine("  return (%s) this;", datatype.getBuilder())
        .addLine("}");
  }

  private void addRemove(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Removes the mapping for {@code key} from the map to be returned from")
        .addLine(" * %s, if one is present.",
            datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName());
    if (!unboxedKeyType.isPresent()) {
      code.addLine(" * @throws NullPointerException if {@code key} is null");
    }
    code.addLine(" */")
        .addLine("public %s %s(%s key) {",
            datatype.getBuilder(),
            removeMethod(property),
            unboxedKeyType.orElse(keyType));
    if (!unboxedKeyType.isPresent()) {
      code.addLine("  %s.requireNonNull(key);", Objects.class);
    }
    code.addLine("  %s.remove(key);", property.getField())
        .addLine("  return (%s) this;", datatype.getBuilder())
        .addLine("}");
  }

  private void addMutate(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Invokes {@code mutator} with the map to be returned from")
        .addLine(" * %s.", datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * <p>This method mutates the map in-place. {@code mutator} is a void")
        .addLine(" * consumer, so any value returned from a lambda will be ignored. Take care")
        .addLine(" * not to call pure functions, like %s.",
            Type.from(Collection.class).javadocNoArgMethodLink("stream"))
        .addLine(" *")
        .addLine(" * @return this {@code Builder} object")
        .addLine(" * @throws NullPointerException if {@code mutator} is null")
        .addLine(" */")
        .addLine("public %s %s(%s mutator) {",
            datatype.getBuilder(),
            mutator(property),
            mutatorType.getFunctionalInterface());
    if (overridesPutMethod) {
      code.addLine("  mutator.%s(new %s<>(%s, this::%s));",
          mutatorType.getMethodName(), CheckedMap.TYPE, property.getField(), putMethod(property));
    } else {
      code.addLine("  // If %s is overridden, this method will be updated to delegate to it",
              putMethod(property))
          .addLine("  mutator.%s(%s);", mutatorType.getMethodName(), property.getField());
    }
    code.addLine("  return (%s) this;", datatype.getBuilder())
        .addLine("}");
  }

  private void addClear(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Removes all of the mappings from the map to be returned from ")
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
        .addLine(" * Returns an unmodifiable view of the map that will be returned by")
        .addLine(" * %s.", datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" * Changes to this builder will be reflected in the view.")
        .addLine(" */")
        .addLine("public %s<%s, %s> %s() {", Map.class, keyType, valueType, getter(property))
        .addLine("  return %s.unmodifiableMap(%s);", Collections.class, property.getField())
        .addLine("}");
  }

  @Override
  public void addFinalFieldAssignment(SourceBuilder code, Excerpt finalField, String builder) {
    Excerpt immutableMapMethod;
    if (code.feature(GUAVA).isAvailable()) {
      immutableMapMethod = Excerpts.add("%s.copyOf", ImmutableMap.class);
    } else {
      immutableMapMethod = ImmutableMapMethod.REFERENCE;
    }
    code.addLine("%s = %s(%s);", finalField, immutableMapMethod, property.getField().on(builder));
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
  public void addSetFromResult(SourceBuilder code, Excerpt builder, Excerpt variable) {
    code.addLine("%s.%s(%s);", builder, putAllMethod(property), variable);
  }

  @Override
  public void addClearField(SourceBuilder code) {
    code.addLine("%s.clear();", property.getField());
  }

  private static class ImmutableMapMethod extends ValueType implements Excerpt {

    static final LazyName REFERENCE = LazyName.of("immutableMap", new ImmutableMapMethod());

    private ImmutableMapMethod() {}

    @Override
    public void addTo(SourceBuilder code) {
      code.addLine("")
          .addLine("private static <K, V> %1$s<K, V> %2$s(%1$s<K, V> entries) {",
              Map.class, REFERENCE)
          .addLine("  switch (entries.size()) {")
          .addLine("  case 0:")
          .addLine("    return %s.emptyMap();", Collections.class)
          .addLine("  case 1:")
          .addLine("    %s<K, V> entry = entries.entrySet().iterator().next();", Map.Entry.class)
          .addLine("    return %s.singletonMap(entry.getKey(), entry.getValue());",
              Collections.class)
          .addLine("  default:")
          .addLine("    return %s.unmodifiableMap(new %s<>(entries));",
              Collections.class, LinkedHashMap.class)
          .addLine("  }")
          .addLine("}");
    }

    @Override
    protected void addFields(FieldReceiver fields) {}
  }
}
