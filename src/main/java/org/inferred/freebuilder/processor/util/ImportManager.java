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

import static com.google.common.collect.Maps.newLinkedHashMap;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSortedSet;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Manages the imports for a source file, and produces short type references by adding extra
 * imports when possible.
 *
 * <p>To ensure we never import common names like 'Builder', nested classes are never directly
 * imported. This is necessarily less readable when types are used as namespaces, e.g. in proto2.
 */
class ImportManager {

  /** Imported types, indexed by simple name. */
  private final Map<String, QualifiedName> imports = newLinkedHashMap();

  public Set<String> getClassImports() {
    ImmutableSortedSet.Builder<String> result = ImmutableSortedSet.naturalOrder();
    for (QualifiedName type : imports.values()) {
      result.add(type.toString());
    }
    return result.build();
  }

  public void appendShortened(Appendable a, QualifiedName type) throws IOException {
    appendPackageForTopLevelClass(a, type.getPackage(), type.getSimpleNames().get(0));
    String prefix = "";
    for (String simpleName : type.getSimpleNames()) {
      a.append(prefix).append(simpleName);
      prefix = ".";
    }
  }

  public Optional<QualifiedName> lookup(String shortenedType) {
    String[] simpleNames = shortenedType.split("\\.");
    QualifiedName result = imports.get(simpleNames[0]);
    if (result == null) {
      return Optional.absent();
    }
    for (int i = 1; i < simpleNames.length; i++) {
      result = result.nestedType(simpleNames[i]);
    }
    return Optional.of(result);
  }

  private void appendPackageForTopLevelClass(Appendable a, String pkg, String simpleName)
      throws IOException {
    QualifiedName qualifiedName = QualifiedName.of(pkg, simpleName);
    if (!imports.containsKey(simpleName)) {
      imports.put(simpleName.toString(), qualifiedName);
      // Append nothing
    } else if (imports.get(simpleName).equals(qualifiedName)) {
      // Append nothing
    } else {
      a.append(pkg).append(".");
    }
  }
}
