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

import java.util.Collection;
import java.util.Map.Entry;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.PropertyCodeGenerator.Config;
import org.inferred.freebuilder.processor.util.SourceBuilder;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

/**
 * {@link PropertyCodeGenerator.Factory} providing append-only semantics for {@link ListMultimap}
 * properties.
 */
public class ListMultimapPropertyFactory implements PropertyCodeGenerator.Factory {

  private static final String PUT_PREFIX = "put";
  private static final String PUT_ALL_PREFIX = "putAll";
  private static final String CLEAR_PREFIX = "clear";
  private static final String GET_PREFIX = "get";

  @Override
  public Optional<CodeGenerator> create(Config config) {
    // No @Nullable properties
    if (!config.getProperty().getNullableAnnotations().isEmpty()) {
      return Optional.absent();
    }

    if (config.getProperty().getType().getKind() == TypeKind.DECLARED) {
      DeclaredType type = (DeclaredType) config.getProperty().getType();
      if (erasesToAnyOf(type,
          Multimap.class,
          ImmutableMultimap.class,
          ListMultimap.class,
          ImmutableListMultimap.class)) {
        TypeMirror keyType = upperBound(config.getElements(), type.getTypeArguments().get(0));
        TypeMirror valueType = upperBound(config.getElements(), type.getTypeArguments().get(1));
        Optional<TypeMirror> unboxedKeyType = unboxed(config.getTypes(), keyType);
        Optional<TypeMirror> unboxedValueType = unboxed(config.getTypes(), valueType);
        return Optional.of(new CodeGenerator(
            config.getProperty(), keyType, unboxedKeyType, valueType, unboxedValueType));
      }
    }
    return Optional.absent();
  }

  private static class CodeGenerator extends PropertyCodeGenerator {

    private final TypeMirror keyType;
    private final Optional<TypeMirror> unboxedKeyType;
    private final TypeMirror valueType;
    private final Optional<TypeMirror> unboxedValueType;

    CodeGenerator(
        Property property,
        TypeMirror keyType,
        Optional<TypeMirror> unboxedKeyType,
        TypeMirror valueType,
        Optional<TypeMirror> unboxedValueType) {
      super(property);
      this.keyType = keyType;
      this.unboxedKeyType = unboxedKeyType;
      this.valueType = valueType;
      this.unboxedValueType = unboxedValueType;
    }

    @Override
    public void addBuilderFieldDeclaration(SourceBuilder code) {
      code.addLine("private %1$s<%2$s, %3$s> %4$s = %1$s.create();",
          LinkedListMultimap.class, keyType, valueType, property.getName());
    }

    @Override
    public void addBuilderFieldAccessors(SourceBuilder code, Metadata metadata) {
      // put(K key, V value)
      code.addLine("")
          .addLine("/**")
          .addLine(" * Adds a {@code key}-{@code value} mapping to the multimap to be returned")
          .addLine(" * from {@link %s#%s()}.", metadata.getType(), property.getGetterName())
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName());
      if (!unboxedKeyType.isPresent() || !unboxedValueType.isPresent()) {
        code.add(" * @throws NullPointerException if ");
        if (unboxedKeyType.isPresent()) {
          code.add("{@code value}");
        } else if (unboxedValueType.isPresent()) {
          code.add("{@code key}");
        } else {
          code.add("either {@code key} or {@code value}");
        }
        code.add(" is null\n");
      }
      code.addLine(" */")
          .addLine("public %s %s%s(%s key, %s value) {",
              metadata.getBuilder(),
              PUT_PREFIX,
              property.getCapitalizedName(),
              unboxedKeyType.or(keyType),
              unboxedValueType.or(valueType));
      if (!unboxedKeyType.isPresent()) {
        code.addLine("  %s.checkNotNull(key);", Preconditions.class);
      }
      if (!unboxedValueType.isPresent()) {
        code.addLine("  %s.checkNotNull(value);", Preconditions.class);
      }
      code.addLine("  this.%s.put(key, value);", property.getName())
          .addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");

      // putAll(K key, Iterable<? extends V> values)
      code.addLine("")
          .addLine("/**")
          .addLine(" * Adds a collection of {@code values} with the same {@code key} to the")
          .addLine(" * multimap to be returned from {@link %s#%s()}.",
              metadata.getType(), property.getGetterName())
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName());
      if (unboxedKeyType.isPresent()) {
        code.addLine(" * @throws NullPointerException if {@code values} is null or contains a"
            + " null element");
      } else {
        code.addLine(" * @throws NullPointerException if either {@code key} or {@code values} is")
            .addLine(" *     null, or if {@code values} contains a null element");
      }
      code.addLine(" */")
          .addLine("public %s %s%s(%s key, %s<? extends %s> values) {",
              metadata.getBuilder(),
              PUT_ALL_PREFIX,
              property.getCapitalizedName(),
              unboxedKeyType.or(keyType),
              Iterable.class,
              valueType)
          .addLine("  for (%s value : values) {", unboxedValueType.or(valueType))
          .addLine("    %s%s(key, value);", PUT_PREFIX, property.getCapitalizedName())
          .addLine("  }")
          .addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");

      // putAll(Multimap<? extends K, ? extends V> multimap)
      code.addLine("")
          .addLine("/**")
          .addLine(" * Adds each entry of {@code multimap} to the multimap to be returned from")
          .addLine(" * {@link %s#%s()}.", metadata.getType(), property.getGetterName())
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
          .addLine(" * @throws NullPointerException if {@code multimap} is null or contains a")
          .addLine(" *     null key or value")
          .addLine(" */")
          .addLine("public %s %s%s(%s<? extends %s, ? extends %s> multimap) {",
              metadata.getBuilder(),
              PUT_ALL_PREFIX,
              property.getCapitalizedName(),
              Multimap.class,
              keyType,
              valueType)
          .addLine("  for (%s<? extends %s, ? extends %s<? extends %s>> entry",
              Entry.class, keyType, Collection.class, valueType)
          .addLine("      : multimap.asMap().entrySet()) {")
          .addLine("    %s%s(entry.getKey(), entry.getValue());",
              PUT_ALL_PREFIX, property.getCapitalizedName())
          .addLine("  }")
          .addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");

      // clear()
      code.addLine("")
          .addLine("/**")
          .addLine(" * Removes all of the mappings from the multimap to be returned from")
          .addLine(" * {@link %s#%s()}.", metadata.getType(), property.getGetterName())
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
          .addLine(" */")
          .addLine("public %s %s%s() {",
              metadata.getBuilder(),
              CLEAR_PREFIX,
              property.getCapitalizedName())
          .addLine("  this.%s.clear();", property.getName())
          .addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");

      // get()
      code.addLine("")
          .addLine("/**")
          .addLine(" * Returns an unmodifiable view of the multimap that will be returned by")
          .addLine(" * {@link %s#%s()}.", metadata.getType(), property.getGetterName())
          .addLine(" * Changes to this builder will be reflected in the view.")
          .addLine(" */")
          .addLine("public %s<%s, %s> %s%s() {",
              ListMultimap.class,
              keyType,
              valueType,
              GET_PREFIX,
              property.getCapitalizedName())
          .addLine("  return %s.unmodifiableListMultimap(%s);",
              Multimaps.class, property.getName())
          .addLine("}");
    }

    @Override
    public void addFinalFieldAssignment(SourceBuilder code, String finalField, String builder) {
      code.addLine("%s = %s.copyOf(%s.%s);",
              finalField, ImmutableListMultimap.class, builder, property.getName());
    }

    @Override
    public void addMergeFromValue(SourceBuilder code, String value) {
      code.addLine("%s%s(%s.%s());",
          PUT_ALL_PREFIX, property.getCapitalizedName(), value, property.getGetterName());
    }

    @Override
    public void addMergeFromBuilder(SourceBuilder code, Metadata metadata, String builder) {
      code.addLine("%s%s(((%s) %s).%s);",
          PUT_ALL_PREFIX,
          property.getCapitalizedName(),
          metadata.getGeneratedBuilder(),
          builder,
          property.getName());
    }

    @Override
    public void addSetFromResult(SourceBuilder code, String builder, String variable) {
      code.addLine("%s.%s%s(%s);",
          builder, PUT_ALL_PREFIX, property.getCapitalizedName(), variable);
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
  }

  private static Optional<TypeMirror> unboxed(Types types, TypeMirror elementType) {
    Optional<TypeMirror> unboxedType;
    try {
      unboxedType = Optional.<TypeMirror>of(types.unboxedType(elementType));
    } catch (IllegalArgumentException e) {
      unboxedType = Optional.absent();
    }
    return unboxedType;
  }
}
