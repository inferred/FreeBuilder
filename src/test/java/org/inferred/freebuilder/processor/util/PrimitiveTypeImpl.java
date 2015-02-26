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

import java.lang.annotation.Annotation;
import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeVisitor;

/**
 * Fake implementation of {@link PrimitiveType} for unit tests.
 */
public enum PrimitiveTypeImpl implements PrimitiveType {

  CHAR(TypeKind.CHAR),
  INT(TypeKind.INT),
  FLOAT(TypeKind.FLOAT),
  DOUBLE(TypeKind.DOUBLE),
  ;

  private final TypeKind kind;

  PrimitiveTypeImpl(TypeKind kind) {
    checkState(kind.isPrimitive());
    this.kind = kind;
  }

  @Override
  public TypeKind getKind() {
    return kind;
  }

  // Override
  public <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationType) {
    throw new UnsupportedOperationException();
  }

  // Override
  public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
    throw new UnsupportedOperationException();
  }

  // Override
  public List<? extends AnnotationMirror> getAnnotationMirrors() {
    throw new UnsupportedOperationException();
  }

  @Override
  public <R, P> R accept(TypeVisitor<R, P> v, P p) {
    return v.visitPrimitive(this, p);
  }

  @Override
  public String toString() {
    return kind.toString().toLowerCase();
  }
}

