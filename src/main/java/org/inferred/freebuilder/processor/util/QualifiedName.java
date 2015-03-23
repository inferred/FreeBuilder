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

import static com.google.common.base.Preconditions.checkState;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

import com.google.common.base.Preconditions;

/**
 * The qualified name of a type. Lets us pass a type to a {@link TypeShortener} without a Class or
 * javax.lang.model reference.
 */
public class QualifiedName extends ValueType {

  /**
   * Returns a {@link QualifiedName} for a type in {@code packageName}. If {@code nestedTypes} is
   * empty, it is a top level type called {@code topLevelType}; otherwise, it is nested in that
   * type.
   */
  public static QualifiedName of(String packageName, String topLevelType, String... nestedTypes) {
    Preconditions.checkArgument(!packageName.isEmpty());
    Preconditions.checkArgument(!topLevelType.isEmpty());
    StringBuilder nestedSuffix = new StringBuilder();
    for (String nestedType : nestedTypes) {
      Preconditions.checkArgument(!nestedType.isEmpty());
      nestedSuffix.append(".").append(nestedType);
    }
    return new QualifiedName(packageName, topLevelType, nestedSuffix.toString());
  }

  /**
   * Returns a {@link QualifiedName} for {@code type}.
   */
  public static QualifiedName of(TypeElement type) {
    if (type.getEnclosingElement().getKind() == ElementKind.PACKAGE) {
      PackageElement pkg = (PackageElement) type.getEnclosingElement();
      return new QualifiedName(
          pkg.getQualifiedName().toString(), type.getSimpleName().toString(), "");
    } else {
      QualifiedName enclosingElement = QualifiedName.of((TypeElement) type.getEnclosingElement());
      return enclosingElement.nestedType(type.getSimpleName().toString());
    }
  }

  // Currently, only top-level types are implemented.
  private final String packageName;
  private final String topLevelType;
  private final String nestedSuffix;

  private QualifiedName(String packageName, String topLevelType, String nestedSuffix) {
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

  public String getSimpleName() {
    return nestedSuffix.isEmpty()
        ? topLevelType
        : nestedSuffix.substring(nestedSuffix.lastIndexOf('.') + 1);
  }

  public boolean isTopLevel() {
    return nestedSuffix.isEmpty();
  }

  public QualifiedName nestedType(String simpleName) {
    return new QualifiedName(packageName, topLevelType, nestedSuffix + "." + simpleName);
  }

  /**
   * Returns a {@link QualifiedName} to the type enclosing this one.
   *
   * @throws IllegalStateException if {@link #isTopLevel()} returns true
   */
  public QualifiedName getEnclosingType() {
    checkState(!isTopLevel(), "%s has no enclosing type", this);
    return new QualifiedName(
        packageName, topLevelType, nestedSuffix.substring(0, nestedSuffix.lastIndexOf('.')));
  }

  @Override
  public String toString() {
    return packageName + "." + topLevelType + nestedSuffix;
  }
}

