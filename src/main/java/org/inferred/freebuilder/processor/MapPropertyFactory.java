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

import static org.inferred.freebuilder.processor.BuilderMethods.checkMethod;
import static org.inferred.freebuilder.processor.BuilderMethods.clearMethod;
import static org.inferred.freebuilder.processor.BuilderMethods.getter;
import static org.inferred.freebuilder.processor.BuilderMethods.mutator;
import static org.inferred.freebuilder.processor.BuilderMethods.putAllMethod;
import static org.inferred.freebuilder.processor.BuilderMethods.putMethod;
import static org.inferred.freebuilder.processor.BuilderMethods.removeMethod;
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

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;

/**
 * {@link PropertyCodeGenerator.Factory} providing append-only semantics for {@link Map}
 * properties.
 */
public class MapPropertyFactory implements PropertyCodeGenerator.Factory {

  @Override
  public Optional<? extends PropertyCodeGenerator> create(Config config) {
    DeclaredType type = ModelUtils.maybeDeclared(config.getProperty().getType()).orNull();
    if (type == null || !erasesToAnyOf(type, Map.class, ImmutableMap.class)) {
      return Optional.absent();
    }
    return Optional.of(createForMapType(config, type));
  }

  private static CodeGenerator createForMapType(Config config, DeclaredType type) {
    TypeMirror keyType = upperBound(config.getElements(), type.getTypeArguments().get(0));
    TypeMirror valueType = upperBound(config.getElements(), type.getTypeArguments().get(1));
    Optional<TypeMirror> unboxedKeyType = maybeUnbox(keyType, config.getTypes());
    Optional<TypeMirror> unboxedValueType = maybeUnbox(valueType, config.getTypes());
    boolean overridesCheckMethod = hasCheckMethodOverride(
        config, unboxedKeyType.or(keyType), unboxedValueType.or(valueType));
    Optional<ExecutableElement> putMethod = getPutMethodOverride(
        config, unboxedKeyType.or(keyType), unboxedValueType.or(valueType));
    if (putMethod.isPresent() && !overridesCheckMethod) {
      config.getMessager().printMessage(
          Kind.MANDATORY_WARNING,
          "Overriding put methods on @FreeBuilder types is deprecated; please override "
              + checkMethod(config.getProperty()) + " instead",
          putMethod.get());
    }
    return new CodeGenerator(
        config.getProperty(),
        overridesCheckMethod,
        putMethod.isPresent(),
        keyType,
        unboxedKeyType,
        valueType,
        unboxedValueType);
  }

  private static boolean hasCheckMethodOverride(
      Config config, TypeMirror keyType, TypeMirror valueType) {
    if (!config.getBuilder().isPresent()) {
      return false;
    }
    return ModelUtils.overrides(
        config.getBuilder().get(),
        config.getTypes(),
        checkMethod(config.getProperty()),
        keyType,
        valueType);
  }

  private static Optional<ExecutableElement> getPutMethodOverride(
      Config config, TypeMirror keyType, TypeMirror valueType) {
    if (!config.getBuilder().isPresent()) {
      return Optional.absent();
    }
    return ModelUtils.findMethod(
        config.getBuilder().get(),
        config.getTypes(),
        putMethod(config.getProperty()),
        keyType,
        valueType);
  }

  @VisibleForTesting
  static class CodeGenerator extends PropertyCodeGenerator {

    private final boolean overridesCheckMethod;
    private final boolean overridesPutMethod;
    private final TypeMirror keyType;
    private final Optional<TypeMirror> unboxedKeyType;
    private final TypeMirror valueType;
    private final Optional<TypeMirror> unboxedValueType;

    CodeGenerator(
        Property property,
        boolean overridesCheckMethod,
        boolean overridesPutMethod,
        TypeMirror keyType,
        Optional<TypeMirror> unboxedKeyType,
        TypeMirror valueType,
        Optional<TypeMirror> unboxedValueType) {
      super(property);
      this.overridesCheckMethod = overridesCheckMethod;
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
    }

    @Override
    public void addBuilderFieldAccessors(SourceBuilder code, Metadata metadata) {
      addPut(code, metadata);
      addPutAll(code, metadata);
      addRemove(code, metadata);
      addMutate(code, metadata);
      addClear(code, metadata);
      addGetter(code, metadata);
      addCheck(code, metadata);
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
          .addLine("public %s %s(%s key, %s value) {",
              metadata.getBuilder(),
              putMethod(property),
              unboxedKeyType.or(keyType),
              unboxedValueType.or(valueType));
      if (!unboxedKeyType.isPresent()) {
        code.add(PreconditionExcerpts.checkNotNull("key"));
      }
      if (!unboxedValueType.isPresent()) {
        code.add(PreconditionExcerpts.checkNotNull("value"));
      }
      code.addLine("  %s(key, value);", checkMethod(property));
      Excerpt keyNotPresent = new Excerpt() {
        @Override
        public void addTo(SourceBuilder source) {
          source.add("!%s.containsKey(key)", property.getName());
        }
      };
      code.add(PreconditionExcerpts.checkArgument(
              keyNotPresent, "Key already present in " + property.getName() + ": %s", "key"))
          .addLine("  %s.put(key, value);", property.getName())
          .addLine("  return (%s) this;", metadata.getBuilder())
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
          .addLine(" * %s.", metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName());
      if (!unboxedKeyType.isPresent()) {
        code.addLine(" * @throws NullPointerException if {@code key} is null");
      }
      code.addLine(" * @throws IllegalArgumentException if {@code key} is not present")
          .addLine(" */")
          .addLine("public %s %s(%s key) {",
              metadata.getBuilder(),
              removeMethod(property),
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
      if (overridesPutMethod && !overridesCheckMethod) {
        return;
      }
      FunctionPackage functionPackage = code.feature(FUNCTION_PACKAGE);
      Optional<ParameterizedType> consumer = functionPackage.consumer();
      if (consumer.isPresent()) {
        code.addLine("public %s %s(%s<? super %s<%s, %s>> mutator) {",
            metadata.getBuilder(),
            mutator(property),
            consumer.get().getQualifiedName(),
            Map.class,
            keyType,
            valueType);
        if (overridesCheckMethod) {
          code.addLine("  mutator.accept(new CheckedDelegatingMap<%s, %s>(", keyType, valueType)
              .addLine("      %s, %s.this::%s));",
                  property.getName(),
                  metadata.getGeneratedBuilder().getQualifiedName(),
                  checkMethod(property));
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
          .addLine(" * Removes all of the mappings from the map to be returned from ")
          .addLine(" * %s.", metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
          .addLine(" */")
          .addLine("public %s %s() {", metadata.getBuilder(), clearMethod(property))
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
          .addLine("public %s<%s, %s> %s() {", Map.class, keyType, valueType, getter(property))
          .addLine("  return %s.unmodifiableMap(%s);", Collections.class, property.getName())
          .addLine("}");
    }

    private void addCheck(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Checks that {@code key} and {@code value} can be put into the map")
          .addLine(" * to be returned from %s.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * <p>Override this to perform argument validation, throwing an")
          .addLine(" * %s if validation fails.", IllegalArgumentException.class)
          .addLine(" */")
          .addLine("@%s(\"unused\")  // key and value may be used in an overriding method",
              SuppressWarnings.class)
          .addLine("void %s(%s key, %s value) {}",
              checkMethod(property),
              unboxedKeyType.or(keyType),
              unboxedValueType.or(valueType));
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
      code.addLine("%s(%s.%s());", putAllMethod(property), value, property.getGetterName());
    }

    @Override
    public void addMergeFromBuilder(SourceBuilder code, Metadata metadata, String builder) {
      code.addLine("%s(((%s) %s).%s);",
          putAllMethod(property),
          metadata.getGeneratedBuilder(),
          builder,
          property.getName());
    }

    @Override
    public void addSetFromResult(SourceBuilder code, String builder, String variable) {
      code.addLine("%s.%s(%s);", builder, putAllMethod(property), variable);
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
      ImmutableSet.Builder<Excerpt> result = ImmutableSet.builder();
      result.add(StaticMethod.values());
      if (overridesCheckMethod) {
        result.add(CheckedDelegatingTypes.values());
      }
      return result.build();
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

  private enum CheckedDelegatingTypes implements Excerpt {
    CHECKED_DELEGATING_ENTRY() {
      @Override
      public void addTo(SourceBuilder code) {
        Optional<ParameterizedType> biConsumer = code.feature(FUNCTION_PACKAGE).biConsumer();
        if (biConsumer.isPresent()) {
          code.addLine("")
              .addLine("private static class CheckedDelegatingEntry<K, V> implements %s<K, V> {",
                  Map.Entry.class)
              .addLine("")
              .addLine("  private final %s<K, V> delegate;", Map.Entry.class)
              .addLine("  private final %s<K, V> check;", biConsumer.get().getQualifiedName())
              .addLine("")
              .addLine("  CheckedDelegatingEntry(%s<K, V> delegate, %s<K, V> check) {",
                  Map.Entry.class, biConsumer.get().getQualifiedName())
              .addLine("    this.delegate = delegate;")
              .addLine("    this.check = check;")
              .addLine("  }")
              .addLine("")
              .addLine("  @Override public K getKey() {")
              .addLine("    return delegate.getKey();")
              .addLine("  }")
              .addLine("")
              .addLine("  @Override public V getValue() {")
              .addLine("    return delegate.getValue();")
              .addLine("  }")
              .addLine("")
              .addLine("  @Override public V setValue(V value) {")
              .add(PreconditionExcerpts.checkNotNull("value"))
              .addLine("    check.accept(delegate.getKey(), value);")
              .addLine("    return delegate.setValue(value);")
              .addLine("  }")
              .addLine("")
              .addLine("  @Override public boolean equals(Object o) {")
              .addLine("    return delegate.equals(o);")
              .addLine("  }")
              .addLine("")
              .addLine("  @Override public int hashCode() {")
              .addLine("    return delegate.hashCode();")
              .addLine("  }")
              .addLine("}");
        }
      }
    },
    CHECKED_DELEGATING_ENTRY_ITERATOR() {
      @Override
      public void addTo(SourceBuilder code) {
        Optional<ParameterizedType> biConsumer = code.feature(FUNCTION_PACKAGE).biConsumer();
        if (biConsumer.isPresent()) {
          code.addLine("")
              .addLine("private static class CheckedDelegatingEntryIterator<K, V>")
              .addLine("    implements %s<%s<K, V>> {", Iterator.class, Map.Entry.class)
              .addLine("")
              .addLine("  private final %s<%s<K, V>> delegate;", Iterator.class, Map.Entry.class)
              .addLine("  private final %s<K, V> check;", biConsumer.get().getQualifiedName())
              .addLine("")
              .addLine("  CheckedDelegatingEntryIterator(")
              .addLine("      %s<%s<K, V>> delegate,", Iterator.class, Map.Entry.class)
              .addLine("      %s<K, V> check) {", biConsumer.get().getQualifiedName())
              .addLine("    this.delegate = delegate;")
              .addLine("    this.check = check;")
              .addLine("  }")
              .addLine("")
              .addLine("  @Override public boolean hasNext() {")
              .addLine("    return delegate.hasNext();")
              .addLine("  }")
              .addLine("")
              .addLine("  @Override public %s<K, V> next() {", Map.Entry.class)
              .addLine("    return new CheckedDelegatingEntry<K, V>(delegate.next(), check);")
              .addLine("  }")
              .addLine("")
              .addLine("  @Override public void remove() {")
              .addLine("    delegate.remove();")
              .addLine("  }")
              .addLine("}");
        }
      }
    },
    CHECKED_DELEGATING_ENTRY_SET() {
      @Override
      public void addTo(SourceBuilder code) {
        Optional<ParameterizedType> biConsumer = code.feature(FUNCTION_PACKAGE).biConsumer();
        if (biConsumer.isPresent()) {
          code.addLine("")
              .addLine("private static class CheckedDelegatingEntrySet<K, V>")
              .addLine("   extends %s<%s<K, V>> {", AbstractSet.class, Map.Entry.class)
              .addLine("")
              .addLine("  private final %s<%s<K, V>> delegate;", Set.class, Map.Entry.class)
              .addLine("  private final %s<K, V> check;", biConsumer.get().getQualifiedName())
              .addLine("")
              .addLine("  CheckedDelegatingEntrySet(")
              .addLine("      %s<%s<K, V>> delegate,", Set.class, Map.Entry.class)
              .addLine("      %s<K, V> check) {", biConsumer.get().getQualifiedName())
              .addLine("    this.delegate = delegate;")
              .addLine("    this.check = check;")
              .addLine("  }")
              .addLine("")
              .addLine("  @Override public int size() {")
              .addLine("    return delegate.size();")
              .addLine("  }")
              .addLine("")
              .addLine("  @Override public %s<%s<K, V>> iterator() {",
                  Iterator.class, Map.Entry.class)
              .addLine("    return new CheckedDelegatingEntryIterator<K, V>(")
              .addLine("        delegate.iterator(), check);")
              .addLine("  }")
              .addLine("")
              .addLine("  @Override public boolean contains(Object o) {")
              .addLine("    return delegate.contains(o);")
              .addLine("  }")
              .addLine("")
              .addLine("  @Override public boolean remove(Object o) {")
              .addLine("    return delegate.remove(o);")
              .addLine("  }")
              .addLine("")
              .addLine("  @Override public void clear() {")
              .addLine("    delegate.clear();")
              .addLine("  }")
              .addLine("}");
        }
      }
    },
    CHECKED_DELEGATING_MAP() {
      @Override
      public void addTo(SourceBuilder code) {
        Optional<ParameterizedType> biConsumer = code.feature(FUNCTION_PACKAGE).biConsumer();
        if (biConsumer.isPresent()) {
          code.addLine("")
              .addLine("private static class CheckedDelegatingMap<K, V> extends %s<K, V> {",
                  AbstractMap.class)
              .addLine("")
              .addLine("  private final %s<K, V> delegate;", Map.class)
              .addLine("  private final %s<K, V> check;", biConsumer.get().getQualifiedName())
              .addLine("")
              .addLine("  CheckedDelegatingMap(")
              .addLine("      %s<K, V> delegate,", Map.class)
              .addLine("      %s<K, V> check) {", biConsumer.get().getQualifiedName())
              .addLine("    this.delegate = delegate;")
              .addLine("    this.check = check;")
              .addLine("  }")
              .addLine("")
              .addLine("  @Override public V get(Object key) {")
              .addLine("    return delegate.get(key);")
              .addLine("  }")
              .addLine("")
              .addLine("  @Override public boolean containsKey(Object key) {")
              .addLine("    return delegate.containsKey(key);")
              .addLine("  }")
              .addLine("")
              .addLine("  @Override public V put(K key, V value) {")
              .addLine("    check.accept(key, value);")
              .addLine("    return delegate.put(key, value);")
              .addLine("  }")
              .addLine("")
              .addLine("  @Override public V remove(Object key) {")
              .addLine("    return delegate.remove(key);")
              .addLine("  }")
              .addLine("")
              .addLine("  @Override public void clear() {")
              .addLine("    delegate.clear();")
              .addLine("  }")
              .addLine("")
              .addLine("  @Override public %s<%s<K, V>> entrySet() {",
                  Set.class, Map.Entry.class)
              .addLine("    return new CheckedDelegatingEntrySet<K, V>(")
              .addLine("        delegate.entrySet(), check);")
              .addLine("  }")
              .addLine("}");
        }
      }
    },
  }
}
