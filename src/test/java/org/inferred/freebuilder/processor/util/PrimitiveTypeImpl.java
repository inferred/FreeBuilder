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

import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeVisitor;

/**
 * Fake implementation of {@link PrimitiveType} for unit tests.
 */
public abstract class PrimitiveTypeImpl implements PrimitiveType {

  public static final PrimitiveType CHAR = Partial.of(PrimitiveTypeImpl.class, TypeKind.CHAR);
  public static final PrimitiveType INT = Partial.of(PrimitiveTypeImpl.class, TypeKind.INT);
  public static final PrimitiveType FLOAT = Partial.of(PrimitiveTypeImpl.class, TypeKind.FLOAT);
  public static final PrimitiveType DOUBLE = Partial.of(PrimitiveTypeImpl.class, TypeKind.DOUBLE);

  private final TypeKind kind;

  PrimitiveTypeImpl(TypeKind kind) {
    checkState(kind.isPrimitive());
    this.kind = kind;
  }

  @Override
  public TypeKind getKind() {
    return kind;
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
