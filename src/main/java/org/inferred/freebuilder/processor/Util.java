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
package org.inferred.freebuilder.processor;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor6;

/** Utility class for common static methods. */
public class Util {

  private Util() { } // COV_NF_LINE

  /**
   * Returns the upper bound of {@code type}.<ul>
   * <li>T -> T
   * <li>? -> Object
   * <li>? extends T -> T
   * <li>? super T -> Object
   * </ul>
   */
  static TypeMirror upperBound(Elements elements, TypeMirror type) {
    if (type.getKind() == TypeKind.WILDCARD) {
      WildcardType wildcard = (WildcardType) type;
      type = wildcard.getExtendsBound();
      if (type == null) {
        type = elements.getTypeElement(Object.class.getName()).asType();
      }
    }
    return type;
  }

  /** Returns true if {@code type} erases to any of {@code possibilities}. */
  static boolean erasesToAnyOf(DeclaredType type, Class<?>... possibilities) {
    String erasedType = type.accept(new SimpleTypeVisitor6<String, Object>() {
      @Override
      public String visitDeclared(DeclaredType t, Object p) {
        return t.asElement().toString();
      }
    }, null);
    for (Class<?> possibility : possibilities) {
      if (possibility.getName().equals(erasedType)) {
        return true;
      }
    }
    return false;
  }
}
