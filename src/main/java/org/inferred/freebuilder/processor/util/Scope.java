/*
 * Copyright 2017 Google Inc. All rights reserved.
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
package org.inferred.freebuilder.processor.util;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;

import java.util.ConcurrentModificationException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * An append-only, hierarchical map with key-specific value typing.
 *
 * <p>Scopes allow source generators to cooperate with each other&mdash;to avoid namespace clashes
 * or reuse variables, for example&mdash;in a decoupled fashion using a shared key-value space.
 * The key type dictates the type of the value that can be stored in the map, as well as at what
 * level in the source the key should be unique at: {@code FILE} keys are unique within a
 * compilation unit, while {@code METHOD} keys are scoped to the current method.
 *
 * <p>While a Scope has to be mutable, it limits the potential for complex bugs by only permitting
 * key-values pairs to be inserted, not modified or removed.
 */
public abstract class Scope {

  public enum Level {
    FILE, METHOD;
  }

  @SuppressWarnings("unused")
  public interface Key<V> {
    Level level();
  }

  private static final Object RECURSION_SENTINEL = new Object();

  private final Map<Key<?>, Object> entries = new LinkedHashMap<>();
  private final Scope parent;

  protected Scope() {
    this.parent = null;
  }

  protected Scope(Scope parent) {
    this.parent = parent;
  }

  protected abstract boolean canStore(Key<?> key);

  public boolean isEmpty() {
    return (parent == null || parent.isEmpty()) && entries.isEmpty();
  }

  public boolean contains(Key<?> key) {
    return get(key) != null;
  }

  public <V> V get(Key<V> key) {
    @SuppressWarnings("unchecked")
    V value = (V) entries.get(key);
    if (value == RECURSION_SENTINEL) {
      throw new ConcurrentModificationException(
          "Cannot access scope key " + key + " while computing its value");
    } else if (value != null) {
      return value;
    } else if (parent != null) {
      return parent.get(key);
    } else {
      return null;
    }
  }

  /**
   * If {@code key} is not already associated with a value, computes its value using
   * {@code supplier} and enters it into the scope.
   *
   * @return the current (existing or computed) value associated with {@code key}
   */
  public <V> V computeIfAbsent(Key<V> key, Supplier<V> supplier) {
    V value = get(key);
    if (value != null) {
      return value;
    } else if (canStore(key)) {
      entries.put(key, RECURSION_SENTINEL);
      value = supplier.get();
      entries.put(key, requireNonNull(value));
      return value;
    } else if (parent != null) {
      return parent.computeIfAbsent(key, supplier);
    } else {
      throw new IllegalStateException(
          "Not in " + key.level().toString().toLowerCase() + " scope");
    }
  }

  public <V> Set<V> keysOfType(Class<V> keyType) {
    ImmutableSet.Builder<V> keys = ImmutableSet.builder();
    if (parent != null) {
      keys.addAll(parent.keysOfType(keyType));
    }
    keys.addAll(FluentIterable.from(entries.keySet()).filter(keyType).toSet());
    return keys.build();
  }

  /**
   * If {@code key} is not already associated with a value, associates it with {@code value}.
   *
   * @return the original value, or {@code null} if there was no value associated
   */
  public <V> V putIfAbsent(Key<V> key, V value) {
    requireNonNull(key);
    requireNonNull(value);
    if (canStore(key)) {
      @SuppressWarnings("unchecked")
      V existingValue = (V) entries.get(key);
      if (value == RECURSION_SENTINEL) {
        throw new ConcurrentModificationException(
            "Cannot access scope key " + key + " while computing its value");
      } else if (existingValue == null) {
        entries.put(key, value);
      }
      return existingValue;
    } else if (parent != null) {
      return parent.putIfAbsent(key, value);
    } else {
      throw new IllegalStateException(
          "Not in " + key.level().toString().toLowerCase() + " scope");
    }
  }

  static class FileScope extends Scope {
    @Override
    protected boolean canStore(Key<?> key) {
      return key.level() == Level.FILE;
    }
  }

  static class MethodScope extends Scope {
    MethodScope(Scope parent) {
      super(parent);
    }

    @Override
    protected boolean canStore(Key<?> key) {
      return key.level() == Level.METHOD;
    }
  }
}
