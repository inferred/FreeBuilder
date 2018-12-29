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

import static com.google.common.collect.Iterables.addAll;
import static com.google.common.collect.Iterables.getOnlyElement;
import static org.inferred.freebuilder.processor.util.Shading.unshadedName;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;

import org.inferred.freebuilder.processor.util.TypeShortener.AbstractTypeShortener;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

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
      Set<String> nonConflictingImports = new LinkedHashSet<>();
      for (Set<QualifiedName> importGroup : Multimaps.asMap(implicitImports).values()) {
        if (importGroup.size() == 1) {
          QualifiedName implicitImport = getOnlyElement(importGroup);
          if (implicitImport.isTopLevel()) {
            nonConflictingImports.add(implicitImport.toString());
          }
        }
      }
      return new ImportManager(implicitImports.keySet(), nonConflictingImports);
    }
  }

  private final Set<String> visibleSimpleNames = new HashSet<>();
  private final ImmutableSet<String> implicitImports;
  private final Set<String> explicitImports = new TreeSet<>();

  private ImportManager(Iterable<String> visibleSimpleNames, Iterable<String> implicitImports) {
    addAll(this.visibleSimpleNames, visibleSimpleNames);
    this.implicitImports = ImmutableSet.copyOf(implicitImports);
  }

  public Set<String> getClassImports() {
    return Collections.unmodifiableSet(explicitImports);
  }

  @Override
  public String shorten(QualifiedName type) {
    StringBuilder b = new StringBuilder();
    appendPackageForTopLevelClass(b, type.getPackage(), type.getSimpleNames().get(0));
    String prefix = "";
    for (String simpleName : type.getSimpleNames()) {
      b.append(prefix).append(simpleName);
      prefix = ".";
    }
    return b.toString();
  }

  @Override
  protected void appendShortened(StringBuilder b, TypeElement type) {
    if (type.getNestingKind().isNested()) {
      appendShortened(b, (TypeElement) type.getEnclosingElement());
      b.append('.');
    } else {
      PackageElement pkg = (PackageElement) type.getEnclosingElement();
      Name name = type.getSimpleName();
      appendPackageForTopLevelClass(b, pkg.getQualifiedName().toString(), name);
    }
    b.append(type.getSimpleName());
  }

  private void appendPackageForTopLevelClass(StringBuilder b, String pkg, CharSequence name) {
    if (pkg.startsWith(PACKAGE_PREFIX)) {
      pkg = pkg.substring(PACKAGE_PREFIX.length());
    }
    pkg = unshadedName(pkg);
    String qualifiedName = pkg + "." + name;
    if (implicitImports.contains(qualifiedName) || explicitImports.contains(qualifiedName)) {
      // Append nothing
    } else if (visibleSimpleNames.contains(name.toString())) {
      b.append(pkg).append(".");
    } else if (pkg.equals(JAVA_LANG_PACKAGE)) {
      // Append nothing
    } else {
      visibleSimpleNames.add(name.toString());
      explicitImports.add(qualifiedName);
      // Append nothing
    }
  }
}
