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
package org.inferred.freebuilder.processor.util;

import static java.util.function.Predicate.isEqual;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toMap;

import org.inferred.freebuilder.processor.util.ScopeHandler.ScopeState;

import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Adds imports to a compilation unit.
 *
 * <p>To ensure we never import common names like 'Builder', nested classes are never directly
 * imported. This is necessarily less readable when types are used as namespaces, e.g. in proto2.
 */
class ImportManager {

  public static String shortenReferences(
      CharSequence codeWithQualifiedNames,
      int importsIndex,
      List<TypeUsage> usages,
      ScopeHandler scopeHandler) {
    // Run through all type usages, determining what is in scope
    SortedSet<QualifiedName> imports = usages.stream()
        .flatMap(usage -> imports(scopeHandler, usage))
        .collect(toMap(QualifiedName::getSimpleName, $ -> $, (a, b) -> a.equals(b) ? a : CONFLICT))
        .values()
        .stream()
        .filter(isEqual(CONFLICT).negate())
        .collect(toCollection(TreeSet::new));

    StringBuilder result = new StringBuilder()
        .append(codeWithQualifiedNames, 0, importsIndex);
    if (!imports.isEmpty()) {
      result.append("\n");
      imports.forEach(type -> result.append("import ").append(type).append(";\n"));
      result.append("\n");
    }
    int i = importsIndex;
    for (TypeUsage usage : usages) {
      result.append(codeWithQualifiedNames, i, usage.start());
      appendUsage(result, usage, scopeHandler, imports);
      i = usage.end();
    }
    return result
        .append(codeWithQualifiedNames, i, codeWithQualifiedNames.length())
        .toString();
  }

  /** Impossible type to use in place of null (which toMap goes odd over). */
  private static final QualifiedName CONFLICT = QualifiedName.of("", "import");

  private static Function<QualifiedName, ScopeState> visibilityIn(
      ScopeHandler scopeHandler, TypeUsage usage) {
    if (usage.scope().isPresent()) {
      return t -> scopeHandler.visibilityIn(usage.scope().get(), t);
    } else {
      return t -> scopeHandler.visibilityIn(usage.pkg(), t);
    }
  }

  private static Stream<QualifiedName> imports(ScopeHandler scopeHandler, TypeUsage usage) {
    return imports(visibilityIn(scopeHandler, usage), usage.type());
  }

  private static Stream<QualifiedName> imports(
      Function<QualifiedName, ScopeState> visibility,
      QualifiedName type) {
    for (QualifiedName candidate = type; true; candidate = candidate.enclosingType()) {
      switch (visibility.apply(candidate)) {
        case IN_SCOPE:
          return Stream.of();

        case IMPORTABLE:
          if (candidate.isTopLevel()) {
            return Stream.of(candidate);
          }
          break;

        case HIDDEN:
          if (candidate.isTopLevel()) {
            return Stream.of();
          }
          break;
      }
    }
  }

  private static void appendUsage(
      StringBuilder result,
      TypeUsage usage,
      ScopeHandler scopeHandler,
      Set<QualifiedName> imports) {
    appendUsage(result, visibilityIn(scopeHandler, usage), usage.type(), imports);
  }

  private static void appendUsage(
      StringBuilder result,
      Function<QualifiedName, ScopeState> visibility,
      QualifiedName type,
      Set<QualifiedName> imports) {
    if (!isVisible(visibility.apply(type), imports.contains(type))) {
      if (type.isTopLevel()) {
        result.append(type.getPackage());
      } else {
        appendUsage(result, visibility, type.enclosingType(), imports);
      }
      result.append('.');
    }
    result.append(type.getSimpleName());
  }

  private static boolean isVisible(ScopeState state, boolean imported) {
    switch (state) {
      case IN_SCOPE:
        return true;

      case IMPORTABLE:
        return imported;

      case HIDDEN:
        return false;
    }
    throw new IllegalStateException("Unexpected state " + state);
  }

  private ImportManager() { }
}
