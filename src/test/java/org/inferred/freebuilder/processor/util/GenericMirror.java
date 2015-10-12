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

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVisitor;

/**
 * Fake representation of a generic top-level type.
 */
public class GenericMirror implements DeclaredType {

  private final AtomicReference<GenericElement> element;
  private final ImmutableList<TypeMirror> typeArguments;

  GenericMirror(
      AtomicReference<GenericElement> element, Iterable<? extends TypeMirror> typeArguments) {
    this.element = element;
    this.typeArguments = ImmutableList.copyOf(typeArguments);
  }

  @Override
  public TypeKind getKind() {
    return TypeKind.DECLARED;
  }

  @Override
  public <R, P> R accept(TypeVisitor<R, P> v, P p) {
    return v.visitDeclared(this, p);
  }

  @Override
  public GenericElement asElement() {
    GenericElement impl = element.get();
    checkState(impl != null,
        "Cannot call asElement() on a GenericMirror referencing an unbuilt GenericType");
    return impl;
  }

  @Override
  public NoType getEnclosingType() {
    return NoTypes.NONE;
  }

  @Override
  public List<? extends TypeMirror> getTypeArguments() {
    return typeArguments;
  }
}
