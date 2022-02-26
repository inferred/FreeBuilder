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
package org.inferred.freebuilder.processor.model;

import static org.inferred.freebuilder.processor.model.ClassTypeImpl.newTopLevelClass;

import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.TypeVisitor;
import org.inferred.freebuilder.processor.source.Partial;

/** Fake implementation of {@link TypeVariable} for unit tests. */
public abstract class TypeVariableImpl implements TypeVariable {

  public static TypeVariable newTypeVariable(String variableName) {
    return Partial.of(TypeVariableImpl.class, variableName);
  }

  private final String variableName;

  TypeVariableImpl(String variableName) {
    this.variableName = variableName;
  }

  @Override
  public TypeKind getKind() {
    return TypeKind.TYPEVAR;
  }

  @Override
  public <R, P> R accept(TypeVisitor<R, P> v, P p) {
    return v.visitTypeVariable(this, p);
  }

  @Override
  public TypeMirror getUpperBound() {
    return newTopLevelClass("java.lang.Object");
  }

  @Override
  public TypeMirror getLowerBound() {
    return NullTypeImpl.NULL;
  }

  @Override
  public String toString() {
    return variableName;
  }

  @Override
  public TypeParameterElement asElement() {
    return Partial.of(ElementImpl.class, this);
  }

  abstract class ElementImpl implements TypeParameterElement {
    @Override
    public List<? extends TypeMirror> getBounds() {
      return ImmutableList.of();
    }
  }
}
