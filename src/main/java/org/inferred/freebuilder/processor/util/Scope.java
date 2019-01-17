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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public abstract class Scope {

  public enum Level {
    FILE, METHOD;
  }

  @SuppressWarnings("unused")
  public interface Key<V> {
    Level level();
  }

  private final Map<Key<?>, Object> elements = new LinkedHashMap<>();
  private final Scope parent;
  private final Level level;

  private Scope(Scope parent, Level level) {
    this.parent = parent;
    this.level = level;
  }

  public boolean contains(Key<?> key) {
    return get(key) != null;
  }

  public <V> V get(Key<V> key) {
    @SuppressWarnings("unchecked")
    V value = (V) elements.get(key);
    if (value != null) {
      return value;
    } else if (parent != null) {
      return parent.get(key);
    } else {
      return null;
    }
  }

  public <V> V computeIfAbsent(Key<V> key, Supplier<V> supplier) {
    V value = get(key);
    if (value != null) {
      return value;
    } else if (level == key.level()) {
      value = supplier.get();
      elements.put(key, value);
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
    keys.addAll(FluentIterable.from(elements.keySet()).filter(keyType).toSet());
    return keys.build();
  }

  public <V> V putIfAbsent(Key<V> key, V value) {
    requireNonNull(key);
    requireNonNull(value);
    if (level == key.level()) {
      @SuppressWarnings("unchecked")
      V existingValue = (V) elements.get(key);
      if (existingValue == null) {
        elements.put(key, value);
      }
      return existingValue;
    } else if (parent != null) {
      return parent.putIfAbsent(key, value);
    }
    return null;
  }

  static class FileScope extends Scope {
    FileScope() {
      super(null, Level.FILE);
    }
  }

  static class MethodScope extends Scope {
    MethodScope(Scope parent) {
      super(parent, Level.METHOD);
    }
  }
}
