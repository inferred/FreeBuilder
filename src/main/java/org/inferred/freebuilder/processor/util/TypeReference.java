/*
 * Copyright 2015 Google Inc. All rights reserved.
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

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

import com.google.common.base.Preconditions;

/**
 * A type reference holds the qualified name of a type, so it can be passed to
 * a {@link TypeShortener} without a Class or javax.lang.model reference.
 */
public class TypeReference extends ValueType {

  /**
   * Returns a {@link TypeReference} for a type in {@code packageName}. If {@code nestedTypes} is
   * empty, it is a top level type called {@code topLevelType}; otherwise, it is nested in that
   * type.
   */
  public static TypeReference to(String packageName, String topLevelType, String... nestedTypes) {
    Preconditions.checkArgument(!packageName.isEmpty());
    Preconditions.checkArgument(!topLevelType.isEmpty());
    StringBuilder nestedSuffix = new StringBuilder();
    for (String nestedType : nestedTypes) {
      Preconditions.checkArgument(!nestedType.isEmpty());
      nestedSuffix.append(".").append(nestedType);
    }
    return new TypeReference(packageName, topLevelType, nestedSuffix.toString());
  }

  /**
   * Returns a {@link TypeReference} for {@code type}.
   */
  public static TypeReference to(TypeElement type) {
    if (type.getEnclosingElement().getKind() == ElementKind.PACKAGE) {
      PackageElement pkg = (PackageElement) type.getEnclosingElement();
      return new TypeReference(
          pkg.getQualifiedName().toString(), type.getSimpleName().toString(), "");
    } else {
      TypeReference enclosingElement = TypeReference.to((TypeElement) type.getEnclosingElement());
      return enclosingElement.nestedType(type.getSimpleName().toString());
    }
  }

  // Currently, only top-level types are implemented.
  private final String packageName;
  private final String topLevelType;
  private final String nestedSuffix;

  private TypeReference(String packageName, String topLevelType, String nestedSuffix) {
    this.packageName = packageName;
    this.topLevelType = topLevelType;
    this.nestedSuffix = nestedSuffix;
  }

  @Override
  protected void addFields(FieldReceiver fields) {
    fields.add("packageName", packageName);
    fields.add("topLevelType", topLevelType);
    fields.add("nestedSuffix", nestedSuffix);
  }

  public String getPackage() {
    return packageName;
  }

  public String getTopLevelTypeSimpleName() {
    return topLevelType;
  }

  /**
   * The part of the qualified name that comes after the top level type, including the period.
   * Empty if this is a top-level type.
   */
  public String getNestedSuffix() {
    return nestedSuffix;
  }

  public String getQualifiedName() {
    return packageName + "." + topLevelType + nestedSuffix;
  }

  public String getSimpleName() {
    return nestedSuffix.isEmpty()
        ? topLevelType
        : nestedSuffix.substring(nestedSuffix.lastIndexOf('.') + 1);
  }

  public boolean isTopLevel() {
    return nestedSuffix.isEmpty();
  }

  public TypeReference nestedType(String simpleName) {
    return new TypeReference(packageName, topLevelType, nestedSuffix + "." + simpleName);
  }
}

