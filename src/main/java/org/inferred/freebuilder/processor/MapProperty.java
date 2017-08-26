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

import static org.inferred.freebuilder.processor.BuilderMethods.clearMethod;
import static org.inferred.freebuilder.processor.BuilderMethods.getter;
import static org.inferred.freebuilder.processor.BuilderMethods.mutator;
import static org.inferred.freebuilder.processor.BuilderMethods.putAllMethod;
import static org.inferred.freebuilder.processor.BuilderMethods.putMethod;
import static org.inferred.freebuilder.processor.BuilderMethods.removeMethod;
import static org.inferred.freebuilder.processor.Util.erasesToAnyOf;
import static org.inferred.freebuilder.processor.Util.upperBound;
import static org.inferred.freebuilder.processor.util.Block.methodBody;
import static org.inferred.freebuilder.processor.util.ModelUtils.maybeDeclared;
import static org.inferred.freebuilder.processor.util.ModelUtils.maybeUnbox;
import static org.inferred.freebuilder.processor.util.ModelUtils.overrides;
import static org.inferred.freebuilder.processor.util.StaticExcerpt.Type.METHOD;
import static org.inferred.freebuilder.processor.util.feature.FunctionPackage.FUNCTION_PACKAGE;
import static org.inferred.freebuilder.processor.util.feature.GuavaLibrary.GUAVA;
import static org.inferred.freebuilder.processor.util.feature.SourceLevel.diamondOperator;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.excerpt.CheckedMap;
import org.inferred.freebuilder.processor.util.Block;
import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.Excerpts;
import org.inferred.freebuilder.processor.util.ParameterizedType;
import org.inferred.freebuilder.processor.util.PreconditionExcerpts;
import org.inferred.freebuilder.processor.util.QualifiedName;
import org.inferred.freebuilder.processor.util.SourceBuilder;
import org.inferred.freebuilder.processor.util.StaticExcerpt;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/**
 * {@link PropertyCodeGenerator} providing fluent methods for {@link Map} properties.
 */
class MapProperty extends PropertyCodeGenerator {

  static class Factory implements PropertyCodeGenerator.Factory {

    @Override
    public Optional<MapProperty> create(Config config) {
      DeclaredType type = maybeDeclared(config.getProperty().getType()).orNull();
      if (type == null || !erasesToAnyOf(type, Map.class, ImmutableMap.class)) {
        return Optional.absent();
      }
      TypeMirror keyType = upperBound(config.getElements(), type.getTypeArguments().get(0));
      TypeMirror valueType = upperBound(config.getElements(), type.getTypeArguments().get(1));
      Optional<TypeMirror> unboxedKeyType = maybeUnbox(keyType, config.getTypes());
      Optional<TypeMirror> unboxedValueType = maybeUnbox(valueType, config.getTypes());
      boolean overridesPutMethod = hasPutMethodOverride(
          config, unboxedKeyType.or(keyType), unboxedValueType.or(valueType));
      return Optional.of(new MapProperty(
          config.getMetadata(),
          config.getProperty(),
          overridesPutMethod,
          keyType,
          unboxedKeyType,
          valueType,
          unboxedValueType));
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
  }

  private static final ParameterizedType COLLECTION =
      QualifiedName.of(Collection.class).withParameters("E");

  private final boolean overridesPutMethod;
  private final TypeMirror keyType;
  private final Optional<TypeMirror> unboxedKeyType;
  private final TypeMirror valueType;
  private final Optional<TypeMirror> unboxedValueType;

  MapProperty(
      Metadata metadata,
      Property property,
      boolean overridesPutMethod,
      TypeMirror keyType,
      Optional<TypeMirror> unboxedKeyType,
      TypeMirror valueType,
      Optional<TypeMirror> unboxedValueType) {
    super(metadata, property);
    this.overridesPutMethod = overridesPutMethod;
    this.keyType = keyType;
    this.unboxedKeyType = unboxedKeyType;
    this.valueType = valueType;
    this.unboxedValueType = unboxedValueType;
  }

  @Override
  public void addBuilderFieldDeclaration(SourceBuilder code) {
    code.addLine("private final %1$s<%2$s, %3$s> %4$s = new %1$s%5$s();",
        LinkedHashMap.class,
        keyType,
        valueType,
        property.getField(),
        diamondOperator(Excerpts.add("%s, %s", keyType, valueType)));
  }

  @Override
  public void addBuilderFieldAccessors(SourceBuilder code) {
    addPut(code, metadata);
    addPutAll(code, metadata);
    addRemove(code, metadata);
    addMutate(code, metadata);
    addClear(code, metadata);
    addGetter(code, metadata);
  }

  private void addPut(SourceBuilder code, Metadata metadata) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Associates {@code key} with {@code value} in the map to be returned from")
        .addLine(" * %s.", metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" * If the map previously contained a mapping for the key,")
        .addLine(" * the old value is replaced by the specified value.")
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName());
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
            metadata.getBuilder(),
            putMethod(property),
            unboxedKeyType.or(keyType),
            unboxedValueType.or(valueType));
    Block body = methodBody(code, "key", "value");
    if (!unboxedKeyType.isPresent()) {
      body.add(PreconditionExcerpts.checkNotNull("key"));
    }
    if (!unboxedValueType.isPresent()) {
      body.add(PreconditionExcerpts.checkNotNull("value"));
    }
    body.addLine("  %s.put(key, value);", property.getField())
        .addLine("  return (%s) this;", metadata.getBuilder());
    code.add(body)
        .addLine("}");
  }

  private void addPutAll(SourceBuilder code, Metadata metadata) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Copies all of the mappings from {@code map} to the map to be returned from")
        .addLine(" * %s.", metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
        .addLine(" * @throws NullPointerException if {@code map} is null or contains a")
        .addLine(" *     null key or value")
        .addLine(" */");
    addAccessorAnnotations(code);
    code.addLine("public %s %s(%s<? extends %s, ? extends %s> map) {",
            metadata.getBuilder(),
            putAllMethod(property),
            Map.class,
            keyType,
            valueType)
        .addLine("  for (%s<? extends %s, ? extends %s> entry : map.entrySet()) {",
            Map.Entry.class, keyType, valueType)
        .addLine("    %s(entry.getKey(), entry.getValue());", putMethod(property))
        .addLine("  }")
        .addLine("  return (%s) this;", metadata.getBuilder())
        .addLine("}");
  }

  private void addRemove(SourceBuilder code, Metadata metadata) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Removes the mapping for {@code key} from the map to be returned from")
        .addLine(" * %s, if one is present.",
            metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName());
    if (!unboxedKeyType.isPresent()) {
      code.addLine(" * @throws NullPointerException if {@code key} is null");
    }
    code.addLine(" */")
        .addLine("public %s %s(%s key) {",
            metadata.getBuilder(),
            removeMethod(property),
            unboxedKeyType.or(keyType));
    Block body = methodBody(code, "key");
    if (!unboxedKeyType.isPresent()) {
      body.add(PreconditionExcerpts.checkNotNull("key"));
    }
    body.addLine("  %s.remove(key);", property.getField())
        .addLine("  return (%s) this;", metadata.getBuilder());
    code.add(body)
        .addLine("}");
  }

  private void addMutate(SourceBuilder code, Metadata metadata) {
    ParameterizedType consumer = code.feature(FUNCTION_PACKAGE).consumer().orNull();
    if (consumer == null) {
      return;
    }
    code.addLine("")
        .addLine("/**")
        .addLine(" * Invokes {@code mutator} with the map to be returned from")
        .addLine(" * %s.", metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * <p>This method mutates the map in-place. {@code mutator} is a void")
        .addLine(" * consumer, so any value returned from a lambda will be ignored. Take care")
        .addLine(" * not to call pure functions, like %s.",
            COLLECTION.javadocNoArgMethodLink("stream"))
        .addLine(" *")
        .addLine(" * @return this {@code Builder} object")
        .addLine(" * @throws NullPointerException if {@code mutator} is null")
        .addLine(" */")
        .addLine("public %s %s(%s<? super %s<%s, %s>> mutator) {",
            metadata.getBuilder(),
            mutator(property),
            consumer.getQualifiedName(),
            Map.class,
            keyType,
            valueType);
    Block body = methodBody(code, "mutator");
    if (overridesPutMethod) {
      body.addLine("  mutator.accept(new CheckedMap<>(%s, this::%s));",
          property.getField(), putMethod(property));
    } else {
      body.addLine("  // If %s is overridden, this method will be updated to delegate to it",
              putMethod(property))
          .addLine("  mutator.accept(%s);", property.getField());
    }
    body.addLine("  return (%s) this;", metadata.getBuilder());
    code.add(body)
        .addLine("}");
  }

  private void addClear(SourceBuilder code, Metadata metadata) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Removes all of the mappings from the map to be returned from ")
        .addLine(" * %s.", metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
        .addLine(" */")
        .addLine("public %s %s() {", metadata.getBuilder(), clearMethod(property))
        .addLine("  %s.clear();", property.getField())
        .addLine("  return (%s) this;", metadata.getBuilder())
        .addLine("}");
  }

  private void addGetter(SourceBuilder code, Metadata metadata) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Returns an unmodifiable view of the map that will be returned by")
        .addLine(" * %s.", metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" * Changes to this builder will be reflected in the view.")
        .addLine(" */")
        .addLine("public %s<%s, %s> %s() {", Map.class, keyType, valueType, getter(property))
        .addLine("  return %s.unmodifiableMap(%s);", Collections.class, property.getField())
        .addLine("}");
  }

  @Override
  public void addFinalFieldAssignment(SourceBuilder code, Excerpt finalField, String builder) {
    code.add("%s = ", finalField);
    if (code.feature(GUAVA).isAvailable()) {
      code.add("%s.copyOf", ImmutableMap.class);
    } else {
      code.add("immutableMap");
    }
    code.add("(%s);\n", property.getField().on(builder));
  }

  @Override
  public void addMergeFromValue(Block code, String value) {
    code.addLine("%s(%s.%s());", putAllMethod(property), value, property.getGetterName());
  }

  @Override
  public void addMergeFromBuilder(Block code, String builder) {
    Excerpt base = Declarations.upcastToGeneratedBuilder(code, metadata, builder);
    code.addLine("%s(%s);", putAllMethod(property), property.getField().on(base));
  }

  @Override
  public void addSetFromResult(SourceBuilder code, Excerpt builder, Excerpt variable) {
    code.addLine("%s.%s(%s);", builder, putAllMethod(property), variable);
  }

  @Override
  public void addClearField(Block code) {
    code.addLine("%s.clear();", property.getField());
  }

  @Override
  public Set<StaticExcerpt> getStaticExcerpts() {
    ImmutableSet.Builder<StaticExcerpt> result = ImmutableSet.builder();
    result.add(IMMUTABLE_MAP);
    if (overridesPutMethod) {
      result.addAll(CheckedMap.excerpts());
    }
    return result.build();
  }

  private static final StaticExcerpt IMMUTABLE_MAP = new StaticExcerpt(METHOD, "immutableMap") {
    @Override
    public void addTo(SourceBuilder code) {
      if (!code.feature(GUAVA).isAvailable()) {
        code.addLine("")
            .addLine("private static <K, V> %1$s<K, V> immutableMap(%1$s<K, V> entries) {",
                Map.class)
            .addLine("  switch (entries.size()) {")
            .addLine("  case 0:")
            .addLine("    return %s.emptyMap();", Collections.class)
            .addLine("  case 1:")
            .addLine("    %s<K, V> entry = entries.entrySet().iterator().next();", Map.Entry.class)
            .addLine("    return %s.singletonMap(entry.getKey(), entry.getValue());",
                Collections.class)
            .addLine("  default:")
            .addLine("    return %s.unmodifiableMap(new %s%s(entries));",
                Collections.class, LinkedHashMap.class, diamondOperator("K, V"))
            .addLine("  }")
            .addLine("}");
      }
    }
  };
}
