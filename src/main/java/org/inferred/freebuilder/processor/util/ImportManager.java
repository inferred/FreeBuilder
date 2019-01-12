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

import static org.inferred.freebuilder.processor.util.Shading.unshadedName;

import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;

import org.inferred.freebuilder.processor.util.TypeShortener.AbstractTypeShortener;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Manages the imports for a source file, and produces short type references by adding extra
 * imports when possible.
 *
 * <p>To ensure we never import common names like 'Builder', nested classes are never directly
 * imported. This is necessarily less readable when types are used as namespaces, e.g. in proto2.
 */
class ImportManager extends AbstractTypeShortener {

  private static final String JAVA_LANG_PACKAGE = "java.lang";
  private static final String PACKAGE_PREFIX = "package ";

  /**
   * Builder of {@link ImportManager} instances.
   */
  public static class Builder {

    /**
     * Simple names of implicitly imported types, mapped to qualified name if that type is safe to
     * use, null otherwise.
     */
    private final SetMultimap<String, QualifiedName> implicitImports = LinkedHashMultimap.create();

    /**
     * Adds a type which is implicitly imported into the current compilation unit.
     */
    public Builder addImplicitImport(QualifiedName type) {
      implicitImports.put(type.getSimpleName(), type);
      return this;
    }

    public ImportManager build() {
      return new ImportManager(implicitImports);
    }
  }

  private static class ScopedShortener extends AbstractTypeShortener {

    private final ImportManager delegate;
    private final QualifiedName scope;

    ScopedShortener(ImportManager delegate, QualifiedName scope) {
      this.delegate = delegate;
      this.scope = scope;
    }

    @Override
    public TypeShortener inScope(QualifiedName scope) {
      throw new UnsupportedOperationException();
    }

    private static int firstMismatchingElement(List<?> a, List<?> b) {
      int i = 0;
      while (i < a.size() && i < b.size() && a.get(i).equals(b.get(i))) {
        i++;
      }
      return i;
    }

    @Override
    public void appendShortened(Appendable a, QualifiedName type) throws IOException {
      if (type.getPackage().equals(scope.getPackage())) {
        int mismatch = firstMismatchingElement(scope.getSimpleNames(), type.getSimpleNames());
        for (int i = Math.min(mismatch, type.getSimpleNames().size() - 1); i >= 0; i--) {
          if (!conflictsWithImplicitImport(type, i)) {
            a.append(type.getSimpleNames().get(i));
            for (int j = i + 1; j < type.getSimpleNames().size(); ++j) {
              a.append('.').append(type.getSimpleNames().get(j));
            }
            return;
          }
        }
      }
      delegate.appendShortened(a, type);
    }

    private boolean conflictsWithImplicitImport(QualifiedName type, int enclosingTypeIndex) {
      Set<QualifiedName> conflicts = delegate.implicitImports.get(
          type.getSimpleNames().get(enclosingTypeIndex));
      switch (conflicts.size()) {
        case 0:
          return false;

        case 1:
          QualifiedName conflict = Iterables.getOnlyElement(conflicts);
          int firstMismatchingElement = firstMismatchingElement(
              conflict.getSimpleNames(), type.getSimpleNames());
          return (firstMismatchingElement < conflict.getSimpleNames().size());

        default:
          return true;
      }
    }
  }

  private final Set<String> visibleSimpleNames = new HashSet<String>();
  private final ImmutableSetMultimap<String, QualifiedName> implicitImports;
  private final Set<String> explicitImports = new TreeSet<String>();

  private ImportManager(SetMultimap<String, QualifiedName> implicitImports) {
    this.implicitImports = ImmutableSetMultimap.copyOf(implicitImports);
    visibleSimpleNames.addAll(implicitImports.keySet());
  }

  public Set<String> getClassImports() {
    return Collections.unmodifiableSet(explicitImports);
  }

  @Override
  public TypeShortener inScope(QualifiedName scope) {
    return (scope == null) ? this : new ScopedShortener(this, scope);
  }

  @Override
  public void appendShortened(Appendable a, QualifiedName type) throws IOException {
    appendPackageForTopLevelClass(a, type.getPackage(), type.getSimpleNames().get(0));
    String prefix = "";
    for (String simpleName : type.getSimpleNames()) {
      a.append(prefix).append(simpleName);
      prefix = ".";
    }
  }

  private void appendPackageForTopLevelClass(Appendable a, String pkg, CharSequence name)
      throws IOException {
    if (pkg.startsWith(PACKAGE_PREFIX)) {
      pkg = pkg.substring(PACKAGE_PREFIX.length());
    }
    pkg = unshadedName(pkg);
    String qualifiedName = pkg + "." + name;
    if (explicitImports.contains(qualifiedName)) {
      // Append nothing
    } else if (visibleSimpleNames.contains(name.toString())) {
      a.append(pkg).append(".");
    } else if (pkg.equals(JAVA_LANG_PACKAGE)) {
      // Append nothing
    } else {
      visibleSimpleNames.add(name.toString());
      explicitImports.add(qualifiedName);
      // Append nothing
    }
  }
}
