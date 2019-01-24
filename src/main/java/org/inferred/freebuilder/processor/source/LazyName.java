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
package org.inferred.freebuilder.processor.source;

import static java.util.stream.Collectors.toList;

import org.inferred.freebuilder.processor.source.Scope.Level;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LazyName extends ValueType implements Excerpt, Scope.Key<LazyName.Declaration> {

  /**
   * Finds all lazily-declared classes and methods and adds their definitions to the source.
   */
  public static void addLazyDefinitions(SourceBuilder code) {
    Set<Declaration> defined = new HashSet<>();

    // Definitions may lazily declare new names; ensure we add them all
    List<Declaration> declarations =
        code.scope().keysOfType(Declaration.class).stream().sorted().collect(toList());
    while (!defined.containsAll(declarations)) {
      for (Declaration declaration : declarations) {
        if (defined.add(declaration)) {
          code.add(code.scope().get(declaration).definition);
        }
      }
      declarations = code.scope().keysOfType(Declaration.class).stream().sorted().collect(toList());
    }
  }

  public static <E extends ValueType & Excerpt> LazyName of(String preferredName, E definition) {
    return new LazyName(preferredName, definition);
  }

  private final String preferredName;
  private final Excerpt definition;

  /**
   * A LazyName, when first used, determines a unique name, using {@code preferredName} if
   * still available, and registers the {@code definition} to be added later.
   */
  private LazyName(String preferredName, Excerpt definition) {
    this.preferredName = preferredName;
    this.definition = definition;
  }

  @Override
  protected void addFields(FieldReceiver fields) {
    fields.add("preferredName", preferredName);
    fields.add("definition", definition);
  }

  @Override
  public Level level() {
    return Level.FILE;
  }

  @Override
  public void addTo(SourceBuilder code) {
    Declaration declaration = code.scope().computeIfAbsent(this, () -> {
      // Search for an unused name, trying the preferred name first
      int attempt = 1;
      Declaration name = new Declaration(preferredName);
      while (true) {
        LazyName existingExcerpt = code.scope().putIfAbsent(name, this);
        if (existingExcerpt == null) {
          return name;
        }
        attempt++;
        name = new Declaration(preferredName + attempt);
      }
    });
    code.add(declaration.name);
  }

  /**
   * A Declaration maps a unique static class name to its static excerpt in a scope.
   */
  static class Declaration extends ValueType
      implements Comparable<Declaration>, Scope.Key<LazyName> {

    private final String name;

    Declaration(String name) {
      this.name = name;
    }

    @Override
    public Level level() {
      return Level.FILE;
    }

    @Override
    protected void addFields(FieldReceiver fields) {
      fields.add("name", name);
    }

    @Override
    public int compareTo(Declaration other) {
      return name.compareTo(other.name);
    }
  }
}
