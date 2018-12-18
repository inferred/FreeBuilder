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

import static org.inferred.freebuilder.processor.util.ModelUtils.asElement;

import com.google.common.base.Joiner;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.SimpleTypeVisitor6;

/**
 * Produces type references for use in source code.
 */
interface TypeShortener {

  String shorten(TypeElement type);
  String shorten(TypeMirror mirror);
  String shorten(QualifiedName type);

  abstract class AbstractTypeShortener
      extends SimpleTypeVisitor6<StringBuilder, StringBuilder>
      implements TypeShortener {

    protected abstract void appendShortened(StringBuilder b, TypeElement type);

    @Override
    public String shorten(TypeElement type) {
      StringBuilder b = new StringBuilder();
      appendShortened(b, type);
      return b.toString();
    }

    @Override
    public String shorten(TypeMirror mirror) {
      return mirror.accept(this, new StringBuilder()).toString();
    }

    @Override
    public StringBuilder visitDeclared(DeclaredType mirror, StringBuilder b) {
      if (mirror.getEnclosingType().getKind() == TypeKind.NONE) {
        appendShortened(b, asElement(mirror));
      } else {
        mirror.getEnclosingType().accept(this, b);
        b.append('.').append(mirror.asElement().getSimpleName());
      }
      if (!mirror.getTypeArguments().isEmpty()) {
        String prefix = "<";
        for (TypeMirror typeArgument : mirror.getTypeArguments()) {
          b.append(prefix);
          typeArgument.accept(this, b);
          prefix = ", ";
        }
        b.append(">");
      }
      return b;
    }

    @Override
    public StringBuilder visitWildcard(WildcardType t, StringBuilder b) {
      b.append("?");
      if (t.getSuperBound() != null) {
        b.append(" super ");
        t.getSuperBound().accept(this, b);
      }
      if (t.getExtendsBound() != null) {
        b.append(" extends ");
        t.getExtendsBound().accept(this, b);
      }
      return b;
    }

    @Override
    protected StringBuilder defaultAction(TypeMirror mirror, StringBuilder b) {
      return b.append(mirror);
    }
  }

  /** A {@link TypeShortener} that never shortens types. */
  class NeverShorten extends AbstractTypeShortener {

    @Override
    public String shorten(QualifiedName type) {
      return type.toString();
    }

    @Override
    protected void appendShortened(StringBuilder b, TypeElement type) {
      b.append(type);
    }
  }

  /** A {@link TypeShortener} that always shortens types, even if that causes conflicts. */
  class AlwaysShorten extends AbstractTypeShortener {

    @Override
    public String shorten(QualifiedName type) {
      return Joiner.on('.').join(type.getSimpleNames());
    }

    @Override
    protected void appendShortened(StringBuilder b, TypeElement type) {
      if (type.getNestingKind().isNested()) {
        appendShortened(b, (TypeElement) type.getEnclosingElement());
        b.append('.');
      }
      b.append(type.getSimpleName());
    }
  }
}
