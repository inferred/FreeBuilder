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
package org.inferred.freebuilder.processor.source;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getLast;

import static org.inferred.freebuilder.processor.source.Shading.unshadedName;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;

import org.inferred.freebuilder.processor.source.Type.TypeImpl;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.TypeMirror;

/**
 * The qualified name of a type. Lets us pass a type to a {@link TypeShortener} without a Class or
 * javax.lang.model reference.
 */
public class QualifiedName extends ValueType implements Comparable<QualifiedName> {

  /**
   * Returns a {@link QualifiedName} for a type in {@code packageName}. If {@code nestedTypes} is
   * empty, it is a top level type called {@code topLevelType}; otherwise, it is nested in that
   * type.
   */
  public static QualifiedName of(String packageName, String topLevelType, String... nestedTypes) {
    requireNonNull(!packageName.isEmpty());
    checkArgument(!topLevelType.isEmpty());
    return new QualifiedName(
        unshadedName(packageName),  // shadowJar modifies string literals; unshade them here
        ImmutableList.<String>builder().add(topLevelType).add(nestedTypes).build());
  }

  /**
   * Returns a {@link QualifiedName} for {@code cls}, unshading if necessary.
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
    switch (type.getNestingKind()) {
      case TOP_LEVEL:
        PackageElement pkg = (PackageElement) type.getEnclosingElement();
        return QualifiedName.of(pkg.getQualifiedName().toString(), type.getSimpleName().toString());

      case MEMBER:
        List<String> reversedNames = new ArrayList<String>();
        reversedNames.add(type.getSimpleName().toString());
        Element parent = type.getEnclosingElement();
        while (parent.getKind() != ElementKind.PACKAGE) {
          reversedNames.add(parent.getSimpleName().toString());
          parent = parent.getEnclosingElement();
        }
        return new QualifiedName(
            ((PackageElement) parent).getQualifiedName().toString(),
            ImmutableList.copyOf(Lists.reverse(reversedNames)));

      default:
        throw new IllegalArgumentException("Cannot determine qualified name of " + type);
    }
  }

  private final String packageName;
  private final ImmutableList<String> simpleNames;

  private QualifiedName(String packageName, ImmutableList<String> simpleNames) {
    this.packageName = packageName;
    this.simpleNames = simpleNames;
  }

  /**
   * Returns this qualified name as a string.
   *
   * <p>Returns the same as {@link Class#getName()} and {@link TypeElement#getQualifiedName()}
   * would for the same type, e.g. "java.lang.Integer" or "com.example.OuterType.InnerType".
   */
  @Override
  public String toString() {
    return packageName + "." + simpleNames.stream().collect(joining("."));
  }

  public String getPackage() {
    return packageName;
  }

  /**
   * Returns the {@link QualifiedName} of the type enclosing this one.
   *
   * @throws IllegalStateException if {@link #isTopLevel()} returns true
   */
  public QualifiedName enclosingType() {
    checkState(!isTopLevel(), "Cannot return enclosing type of top-level type");
    return new QualifiedName(packageName, simpleNames.subList(0, simpleNames.size() - 1));
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

  public boolean isNestedIn(QualifiedName other) {
    return packageName.equals(other.packageName)
        && (simpleNames.size() > other.simpleNames.size())
        && simpleNames.subList(0, other.simpleNames.size()).equals(other.simpleNames);
  }

  /**
   * Returns the {@link QualifiedName} of a type called {@code simpleName} nested in this one.
   */
  public QualifiedName nestedType(String simpleName) {
    return new QualifiedName(packageName,
        ImmutableList.<String>builder().addAll(simpleNames).add(simpleName).build());
  }

  public TypeClass withParameters(TypeParameterElement... parameters) {
    return new TypeClass(this, ImmutableList.copyOf(parameters));
  }

  public Type withParameters(TypeMirror first, TypeMirror... rest) {
    return new TypeImpl(
        this, ImmutableList.<TypeMirror>builder().add(first).add(rest).build());
  }

  public TypeClass withParameters(Iterable<? extends TypeParameterElement> typeParameters) {
    return new TypeClass(this, ImmutableList.copyOf(typeParameters));
  }

  @Override
  public int compareTo(QualifiedName o) {
    return ComparisonChain.start()
        .compare(packageName, o.packageName)
        .compare(simpleNames, o.simpleNames, Ordering.natural().lexicographical())
        .result();
  }

  @Override
  protected void addFields(FieldReceiver fields) {
    fields.add("packageName", packageName);
    fields.add("simpleNames", simpleNames);
  }
}

