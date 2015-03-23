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
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.getLast;

import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * A type reference holds the qualified name of a type, so it can be passed to
 * a {@link TypeShortener} without a Class or javax.lang.model reference.
 */
public class QualifiedName extends ValueType {

  /**
   * Returns a {@link QualifiedName} for a type in {@code packageName}. If {@code nestedTypes} is
   * empty, it is a top level type called {@code topLevelType}; otherwise, it is nested in that
   * type.
   */
  public static QualifiedName of(String packageName, String topLevelType, String... nestedTypes) {
    Preconditions.checkNotNull(!packageName.isEmpty());
    Preconditions.checkArgument(!topLevelType.isEmpty());
    return new QualifiedName(
        packageName, ImmutableList.<String>builder().add(topLevelType).add(nestedTypes).build());
  }

  /**
   * Returns a {@link QualifiedName} for {@code cls}.
   */
  public static QualifiedName of(Class<?> cls) {
    if (cls.getEnclosingClass() != null) {
      return QualifiedName.of(cls.getEnclosingClass()).nestedType(cls.getSimpleName());
    } else if (cls.getPackage() != null) {
      return QualifiedName.of(cls.getPackage().getName(), cls.getSimpleName());
    } else {
      return QualifiedName.of("", cls.getSimpleName());
    }
  }

  /**
   * Returns a {@link QualifiedName} for {@code type}.
   */
  public static QualifiedName of(TypeElement type) {
    if (type.getNestingKind().isNested()) {
      QualifiedName enclosingElement = QualifiedName.of((TypeElement) type.getEnclosingElement());
      return enclosingElement.nestedType(type.getSimpleName().toString());
    } else {
      PackageElement pkg = (PackageElement) type.getEnclosingElement();
      return QualifiedName.of(pkg.getQualifiedName().toString(), type.getSimpleName().toString());
    }
  }

  private final String packageName;
  private final ImmutableList<String> simpleNames;

  private QualifiedName(String packageName, Iterable<String> simpleNames) {
    this.packageName = packageName;
    this.simpleNames = ImmutableList.copyOf(simpleNames);
  }

  @Override
  public String toString() {
    return packageName + "." + Joiner.on('.').join(simpleNames);
  }

  public String getPackage() {
    return packageName;
  }

  public ImmutableList<String> getSimpleNames() {
    return simpleNames;
  }

  public String getSimpleName() {
    return getLast(simpleNames);
  }

  public boolean isTopLevel() {
    return simpleNames.size() == 1;
  }

  /**
   * Returns the {@link QualifiedName} of a type called {@code simpleName} nested in this one.
   */
  public QualifiedName nestedType(String simpleName) {
    return new QualifiedName(packageName, concat(simpleNames, ImmutableList.of(simpleName)));
  }

  public ParameterizedType withParameters(String... typeParameters) {
    return new ParameterizedType(this, ImmutableList.copyOf(typeParameters));
  }

  public ParameterizedType withParameters(Iterable<? extends TypeParameterElement> typeParameters) {
    return new ParameterizedType(this, ImmutableList.copyOf(typeParameters));
  }

  /**
   * Returns the {@link QualifiedName} of the type enclosing this one.
   *
   * @throws IllegalStateException if {@link #isTopLevel()} returns true
   */
  public QualifiedName getEnclosingType() {
    checkState(!isTopLevel(), "%s has no enclosing type", this);
    return new QualifiedName(packageName, simpleNames.subList(0, simpleNames.size() - 1));
  }

  @Override
  protected void addFields(FieldReceiver fields) {
    fields.add("packageName", packageName);
    fields.add("simpleNames", simpleNames);
  }
}

