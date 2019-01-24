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
package org.inferred.freebuilder.processor.source;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import org.inferred.freebuilder.processor.source.ScopeHandler.ScopeState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Adds imports and applies scope visibility rules to a compilation unit.
 *
 * <p>To ensure we never import common names like 'Builder', nested classes must have at least one
 * non-lowercase letter (apart from the first) in their name.
 */
class ImportManager {

  static String shortenReferences(
      CharSequence codeWithQualifiedNames,
      String pkg,
      int importsIndex,
      List<TypeUsage> typeUsages,
      ScopeHandler scopeHandler) {
    ImportManager importManager = new ImportManager(typeUsages, scopeHandler, pkg);
    importManager.selectImports();
    StringBuilder result = new StringBuilder()
        .append(codeWithQualifiedNames, 0, importsIndex);
    importManager.appendImports(result);
    int offset = importsIndex;
    for (TypeUsage usage : typeUsages) {
      result.append(codeWithQualifiedNames, offset, usage.start());
      importManager.appendUsage(result, usage);
      offset = usage.end();
    }
    return result
        .append(codeWithQualifiedNames, offset, codeWithQualifiedNames.length())
        .toString();
  }

  /** Impossible typename, to use instead of null (which toMap goes odd over). */
  private static final QualifiedName CONFLICT = QualifiedName.of("", "import");

  /** Type usages to process. */
  private final Deque<TypeUsage> todo = new ArrayDeque<>();
  /** Simple name → type, or {@link #CONFLICT} if multiple types conflicted for that name. */
  private final Map<String, QualifiedName> namespace = new HashMap<>();
  /** Types to import, and for which usages. */
  private final Multimap<QualifiedName, TypeUsage> imports = ArrayListMultimap.create();
  /** Which type, imported or in scope, to use to shorten each type usage. */
  private final Map<TypeUsage, QualifiedName> resolutions = new HashMap<>();
  private final ScopeHandler scopeHandler;
  private final String pkg;


  private ImportManager(List<TypeUsage> usages, ScopeHandler scopeHandler, String pkg) {
    this.scopeHandler = scopeHandler;
    this.pkg = pkg;
    todo.addAll(usages);
  }

  private void selectImports() {
    while (!todo.isEmpty()) {
      resolveUsage(todo.removeLast());
    }
    imports.asMap().forEach((name, usages) -> {
      usages.forEach(usage -> {
        resolutions.put(usage, name);
      });
    });
  }

  private void appendImports(StringBuilder result) {
    checkState(todo.isEmpty());
    if (!imports.isEmpty()) {
      result.append("\n");
      imports.keySet()
          .stream()
          .sorted()
          .forEach(type -> result.append("import ").append(type).append(";\n"));
      result.append("\n");
    }
  }

  private void appendUsage(StringBuilder result, TypeUsage usage) {
    checkState(todo.isEmpty());
    QualifiedName name = resolutions.get(usage);
    if (name == null) {
      result.append(usage.type());
    } else {
      result.append(name.getSimpleName());
      usage.type()
          .getSimpleNames()
          .stream()
          .skip(name.getSimpleNames().size())
          .forEach(simpleName -> result.append('.').append(simpleName));
    }
  }

  private boolean reserveName(QualifiedName name) {
    QualifiedName conflict = namespace.putIfAbsent(name.getSimpleName(), name);
    if (conflict != null && !conflict.equals(name)) {
      namespace.put(name.getSimpleName(), CONFLICT);
      todo.addAll(imports.removeAll(conflict));
      return false;
    }
    return true;
  }

  private void rejectName(QualifiedName name) {
    QualifiedName conflict = namespace.putIfAbsent(name.getSimpleName(), CONFLICT);
    todo.addAll(imports.removeAll(conflict));
  }

  private boolean addImport(QualifiedName name, TypeUsage usage) {
    if (reserveName(name)) {
      imports.put(name, usage);
      return true;
    }
    return false;
  }

  private void resolveUsage(TypeUsage usage) {
    Function<QualifiedName, ScopeState> visibility = visibilityIn(scopeHandler, usage);
    for (QualifiedName name = usage.type(); true; name = name.enclosingType()) {
      switch (visibility.apply(name)) {
        case IN_SCOPE:
          // Consider using this identifier for imports everywhere
          reserveName(name);
          resolutions.put(usage, name);
          return;

        case IMPORTABLE:
          if (isSensibleImport(name) && addImport(name, usage)) {
            return;
          }
          break;

        case HIDDEN:
          // Don't use this identifier for imports elsewhere either, it will be confusing
          rejectName(name);
          break;
      }
      if (name.isTopLevel()) {
        return;
      }
    }
  }

  private Function<QualifiedName, ScopeState> visibilityIn(
      ScopeHandler scopeHandler, TypeUsage usage) {
    if (usage.scope().isPresent()) {
      return t -> scopeHandler.visibilityIn(usage.scope().get(), t);
    } else {
      return t -> scopeHandler.visibilityIn(pkg, t);
    }
  }

  private static boolean isSensibleImport(QualifiedName name) {
    if (name.isTopLevel()) {
      return true;
    }
    String simpleName = name.getSimpleName();
    for (int i = 1; i < simpleName.length(); i++) {
      if (!Character.isLowerCase(simpleName.charAt(i))) {
        return true;
      }
    }
    return false;
  }
}
