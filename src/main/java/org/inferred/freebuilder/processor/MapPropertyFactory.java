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
import static org.inferred.freebuilder.processor.util.ModelUtils.maybeUnbox;
import static org.inferred.freebuilder.processor.util.feature.FunctionPackage.FUNCTION_PACKAGE;
import static org.inferred.freebuilder.processor.util.feature.GuavaLibrary.GUAVA;
import static org.inferred.freebuilder.processor.util.feature.SourceLevel.SOURCE_LEVEL;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.PropertyCodeGenerator.Config;
import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.ModelUtils;
import org.inferred.freebuilder.processor.util.ParameterizedType;
import org.inferred.freebuilder.processor.util.PreconditionExcerpts;
import org.inferred.freebuilder.processor.util.SourceBuilder;
import org.inferred.freebuilder.processor.util.feature.FunctionPackage;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/**
 * {@link PropertyCodeGenerator.Factory} providing append-only semantics for {@link Map}
 * properties.
 */
public class MapPropertyFactory implements PropertyCodeGenerator.Factory {

  private static final String PUT_PREFIX = "put";
  private static final String PUT_ALL_PREFIX = "putAll";
  private static final String REMOVE_PREFIX = "remove";
  private static final String MUTATE_PREFIX = "mutate";
  private static final String CLEAR_PREFIX = "clear";
  private static final String GET_PREFIX = "get";

  @Override
  public Optional<? extends PropertyCodeGenerator> create(Config config) {
    // No @Nullable properties
    if (!config.getProperty().getNullableAnnotations().isEmpty()) {
      return Optional.absent();
    }

    Optional<DeclaredType> type = ModelUtils.maybeDeclared(config.getProperty().getType());
    if (type.isPresent()) {
      if (erasesToAnyOf(type.get(), Map.class, ImmutableMap.class)) {
        return Optional.of(createForMapType(config, type.get()));
      }
    }
    return Optional.absent();
  }

  private static CodeGenerator createForMapType(Config config, DeclaredType type) {
    TypeMirror keyType = upperBound(config.getElements(), type.getTypeArguments().get(0));
    TypeMirror valueType = upperBound(config.getElements(), type.getTypeArguments().get(1));
    Optional<TypeMirror> unboxedKeyType = maybeUnbox(keyType, config.getTypes());
    Optional<TypeMirror> unboxedValueType = maybeUnbox(valueType, config.getTypes());
    boolean overridesPutMethod = hasPutMethodOverride(
        config, unboxedKeyType.or(keyType), unboxedValueType.or(valueType));
    return new CodeGenerator(
        config.getProperty(),
        overridesPutMethod,
        keyType,
        unboxedKeyType,
        valueType,
        unboxedValueType);
  }

  private static boolean hasPutMethodOverride(
      Config config, TypeMirror keyType, TypeMirror valueType) {
    if (!config.getBuilder().isPresent()) {
      return false;
    }
    return ModelUtils.overrides(
        config.getBuilder().get(),
        config.getTypes(),
        PUT_PREFIX + config.getProperty().getCapitalizedName(),
        keyType,
        valueType);
  }

  @VisibleForTesting
  static class CodeGenerator extends PropertyCodeGenerator {

    private final boolean overridesPutMethod;
    private final TypeMirror keyType;
    private final Optional<TypeMirror> unboxedKeyType;
    private final TypeMirror valueType;
    private final Optional<TypeMirror> unboxedValueType;

    CodeGenerator(
        Property property,
        boolean overridesPutMethod,
        TypeMirror keyType,
        Optional<TypeMirror> unboxedKeyType,
        TypeMirror valueType,
        Optional<TypeMirror> unboxedValueType) {
      super(property);
      this.overridesPutMethod = overridesPutMethod;
      this.keyType = keyType;
      this.unboxedKeyType = unboxedKeyType;
      this.valueType = valueType;
      this.unboxedValueType = unboxedValueType;
    }

    @Override
    public void addBuilderFieldDeclaration(SourceBuilder code) {
      code.add("private final %1$s<%2$s, %3$s> %4$s = new %1$s<",
          LinkedHashMap.class, keyType, valueType, property.getName());
      if (!code.feature(SOURCE_LEVEL).supportsDiamondOperator()) {
        code.add("%s, %s", keyType, valueType);
      }
      code.add(">();\n");
      FunctionPackage functionPackage = code.feature(FUNCTION_PACKAGE);
      if (overridesPutMethod && functionPackage.consumer().isPresent()) {
        code.addLine("private final %s<%s> putInto%s =",
                ThreadLocal.class,
                functionPackage.biConsumer().get().withParameters(keyType, valueType),
                property.getCapitalizedName())
            .addLine("    %s.withInitial(() -> (key, value) -> %s.put(key, value));",
                ThreadLocal.class, property.getName());
      }
    }

    @Override
    public void addBuilderFieldAccessors(SourceBuilder code, Metadata metadata) {
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
          .addLine(" * Duplicate keys are not allowed.")
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
      code.addLine(" * @throws IllegalArgumentException if {@code key} is already present")
          .addLine(" */")
          .addLine("public %s %s%s(%s key, %s value) {",
              metadata.getBuilder(),
              PUT_PREFIX,
              property.getCapitalizedName(),
              unboxedKeyType.or(keyType),
              unboxedValueType.or(valueType));
      if (!unboxedKeyType.isPresent()) {
        code.add(PreconditionExcerpts.checkNotNull("key"));
      }
      if (!unboxedValueType.isPresent()) {
        code.add(PreconditionExcerpts.checkNotNull("value"));
      }
      Excerpt keyNotPresent = new Excerpt() {
        @Override
        public void addTo(SourceBuilder source) {
          source.add("!%s.containsKey(key)", property.getName());
        }
      };
      code.add(PreconditionExcerpts.checkArgument(
          keyNotPresent, "Key already present in " + property.getName() + ": %s", "key"));
      if (overridesPutMethod && code.feature(FUNCTION_PACKAGE).consumer().isPresent()) {
        code.addLine("putInto%s.get().accept(key, value);", property.getCapitalizedName());
      } else {
        code.addLine("%s.put(key, value);", property.getName());
      }
      code.addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addPutAll(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Associates all of {@code map}'s keys and values in the map to be returned")
          .addLine(" * from %s.", metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" * Duplicate keys are not allowed.")
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
          .addLine(" * @throws NullPointerException if {@code map} is null or contains a")
          .addLine(" *     null key or value")
          .addLine(" * @throws IllegalArgumentException if any key is already present")
          .addLine(" */");
      addAccessorAnnotations(code);
      code.addLine("public %s %s%s(%s<? extends %s, ? extends %s> map) {",
              metadata.getBuilder(),
              PUT_ALL_PREFIX,
              property.getCapitalizedName(),
              Map.class,
              keyType,
              valueType)
          .addLine("  for (%s<? extends %s, ? extends %s> entry : map.entrySet()) {",
              Map.Entry.class, keyType, valueType)
          .addLine("    %s%s(entry.getKey(), entry.getValue());",
              PUT_PREFIX, property.getCapitalizedName())
          .addLine("  }")
          .addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addRemove(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Removes the mapping for {@code key} from the map to be returned from")
          .addLine(" * %s.", metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName());
      if (!unboxedKeyType.isPresent()) {
        code.addLine(" * @throws NullPointerException if {@code key} is null");
      }
      code.addLine(" * @throws IllegalArgumentException if {@code key} is not present")
          .addLine(" */")
          .addLine("public %s %s%s(%s key) {",
              metadata.getBuilder(),
              REMOVE_PREFIX,
              property.getCapitalizedName(),
              unboxedKeyType.or(keyType));
      if (!unboxedKeyType.isPresent()) {
        code.add(PreconditionExcerpts.checkNotNull("key"));
      }
      Excerpt keyPresent = new Excerpt() {
        @Override
        public void addTo(SourceBuilder source) {
          source.add("%s.containsKey(key)", property.getName());
        }
      };
      code.add(PreconditionExcerpts.checkArgument(
              keyPresent, "Key not present in " + property.getName() + ": %s", "key"))
          .addLine("  %s.remove(key);", property.getName())
          .addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addMutate(SourceBuilder code, Metadata metadata) {
      FunctionPackage functionPackage = code.feature(FUNCTION_PACKAGE);
      Optional<ParameterizedType> consumer = functionPackage.consumer();
      if (consumer.isPresent()) {
        code.addLine("public %s %s%s(%s<? super %s<%s, %s>> mutator) {",
            metadata.getBuilder(),
            MUTATE_PREFIX,
            property.getCapitalizedName(),
            consumer.get().getQualifiedName(),
            Map.class,
            keyType,
            valueType);
        if (overridesPutMethod) {
          code.addLine("  mutator.accept(new %s<%s, %s>() {",
                  AbstractMap.class, keyType, valueType)
              .addLine("    @Override public %s<%s<%s, %s>> entrySet() {",
                  Set.class, Map.Entry.class, keyType, valueType)
              .addLine("      return new %s<%s<%s, %s>>() {",
                  AbstractSet.class, Map.Entry.class, keyType, valueType)
              .addLine("        @Override public int size() {")
              .addLine("          return %s.size();", property.getName())
              .addLine("        }")
              .addLine("        @Override public %s<%s<%s, %s>> iterator() {",
                  Iterator.class, Map.Entry.class, keyType, valueType)
              .addLine("          return new %s<%s<%s, %s>>() {",
                  Iterator.class, Map.Entry.class, keyType, valueType)
              .addLine("            private final %s<%s<%s, %s>> iterator =",
                  Iterator.class, Map.Entry.class, keyType, valueType)
              .addLine("                %s.entrySet().iterator();", property.getName())
              .addLine("            @Override public boolean hasNext() {")
              .addLine("              return iterator.hasNext();")
              .addLine("            }")
              .addLine("            @Override public %s<%s, %s> next() {",
                  Map.Entry.class, keyType, valueType)
              .addLine("              return new %s<%s, %s>() {",
                  Map.Entry.class, keyType, valueType)
              .addLine("                private final %s<%s, %s> entry = iterator.next();",
                  Map.Entry.class, keyType, valueType)
              .addLine("                @Override public %s getKey() {", keyType)
              .addLine("                  return entry.getKey();")
              .addLine("                }")
              .addLine("                @Override public %s getValue() {", valueType)
              .addLine("                  return entry.getValue();")
              .addLine("                }")
              .addLine("                @Override public %1$s setValue(%1$s value) {", valueType)
              .addLine("                  %s key = entry.getKey();", keyType)
              .addLine("                  %s oldValue = entry.getValue();", valueType)
              .addLine("                  %1$s oldPutInto%2$s = putInto%2$s.get();",
                  functionPackage.biConsumer().get().withParameters(keyType, valueType),
                  property.getCapitalizedName())
              .addLine("                  putInto%s.set((k, v) -> {", property.getCapitalizedName())
              .addLine("                    if (k.equals(key)) {")
              .addLine("                      entry.setValue(v);")
              .addLine("                    } else {")
              .addLine("                      %s.put(k, v);", property.getName())
              .addLine("                    }")
              .addLine("                  });")
              .addLine("                  try {")
              .addLine("                    %s%s(entry.getKey(), value);",
                  PUT_PREFIX, property.getCapitalizedName())
              .addLine("                  } finally {")
              .addLine("                    putInto%1$s.set(oldPutInto%1$s);",
                  property.getCapitalizedName())
              .addLine("                  }")
              .addLine("                  return oldValue;")
              .addLine("                }")
              .addLine("              };")
              .addLine("            }")
              .addLine("          };")
              .addLine("        }")
              .addLine("      };")
              .addLine("    }")
              .addLine("  });");
        } else {
          code.addLine("  // If %s%s is overridden, this method will be updated to delegate to it",
                  PUT_PREFIX, property.getCapitalizedName())
              .addLine("  mutator.accept(%s);", property.getName());
        }
        code.addLine("  return (%s) this;", metadata.getBuilder())
            .addLine("}");
      }
    }

    private void addClear(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Removes all of the mappings from the map to be returned from ")
          .addLine(" * %s.", metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
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
          .addLine(" * Returns an unmodifiable view of the map that will be returned by")
          .addLine(" * %s.", metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" * Changes to this builder will be reflected in the view.")
          .addLine(" */")
          .addLine("public %s<%s, %s> %s%s() {",
              Map.class,
              keyType,
              valueType,
              GET_PREFIX,
              property.getCapitalizedName())
          .addLine("  return %s.unmodifiableMap(%s);", Collections.class, property.getName())
          .addLine("}");
    }

    @Override
    public void addFinalFieldAssignment(SourceBuilder code, String finalField, String builder) {
      code.add("%s = ", finalField);
      if (code.feature(GUAVA).isAvailable()) {
        code.add("%s.copyOf", ImmutableMap.class);
      } else {
        code.add("immutableMap");
      }
      code.add("(%s.%s);\n", builder, property.getName());
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

    @Override
    public Set<StaticMethod> getStaticMethods() {
      return ImmutableSet.copyOf(StaticMethod.values());
    }
  }

  private enum StaticMethod implements Excerpt {
    IMMUTABLE_MAP() {
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
              .addLine("    %s<K, V> entry = entries.entrySet().iterator().next();",
                  Map.Entry.class)
              .addLine("    return %s.singletonMap(entry.getKey(), entry.getValue());",
                  Collections.class)
              .addLine("  default:")
              .add("    return %s.unmodifiableMap(new %s<", Collections.class, LinkedHashMap.class);
          if (!code.feature(SOURCE_LEVEL).supportsDiamondOperator()) {
            code.add("K, V");
          }
          code.add(">(entries));\n")
              .addLine("  }")
              .addLine("}");
        }
      }
    }
  }
}
