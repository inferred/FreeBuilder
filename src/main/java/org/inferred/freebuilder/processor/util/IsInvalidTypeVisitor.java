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

import static com.google.common.collect.Iterables.any;

import com.google.common.base.Predicate;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.NullType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.AbstractTypeVisitor6;

/** A type visitor that returns true if the type will be invalid if we write it out. */
public class IsInvalidTypeVisitor
    extends AbstractTypeVisitor6<Boolean, Void> implements Predicate<TypeMirror> {

  /** Handles self-referential types like Comparable<E extends Comparable<E>>. */
  private final Map<DeclaredType, Boolean> invalidity = new LinkedHashMap<DeclaredType, Boolean>();

  /** Returns true if input is neither null nor invalid. */
  @Override
  public boolean apply(TypeMirror input) {
    return input != null && input.accept(this, null);
  }

  @Override
  public Boolean visitPrimitive(PrimitiveType t, Void p) {
    return false;
  }

  @Override
  public Boolean visitNull(NullType t, Void p) {
    return false;
  }

  @Override
  public Boolean visitArray(ArrayType t, Void p) {
    return apply(t.getComponentType());
  }

  @Override
  public Boolean visitDeclared(DeclaredType t, Void p) {
    if (invalidity.containsKey(t)) {
      return invalidity.get(t);
    }
    invalidity.put(t, false);
    boolean isInvalid = any(t.getTypeArguments(), this);
    invalidity.put(t, isInvalid);
    return isInvalid;
  }

  @Override
  public Boolean visitError(ErrorType t, Void p) {
    return true;
  }

  @Override
  public Boolean visitTypeVariable(TypeVariable t, Void p) {
    TypeParameterElement element = (TypeParameterElement) t.asElement();
    return any(element.getBounds(), this);
  }

  @Override
  public Boolean visitWildcard(WildcardType t, Void p) {
    return apply(t.getExtendsBound()) || apply(t.getSuperBound());
  }

  @Override
  public Boolean visitExecutable(ExecutableType t, Void p) {
    return any(t.getParameterTypes(), this)
        || apply(t.getReturnType())
        || any(t.getThrownTypes(), this)
        || any(t.getTypeVariables(), this);
  }

  @Override
  public Boolean visitNoType(NoType t, Void p) {
    return false;
  }
}
