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

import com.google.common.base.Optional;

import java.io.IOException;

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

  void appendShortened(Appendable a, TypeElement type) throws IOException;
  void appendShortened(Appendable a, TypeMirror mirror) throws IOException;
  void appendShortened(Appendable a, QualifiedName type) throws IOException;

  Optional<QualifiedName> lookup(String shortenedType);

  abstract class AbstractTypeShortener
      extends SimpleTypeVisitor6<Void, Appendable>
      implements TypeShortener {

    private static class IOExceptionWrapper extends RuntimeException {
      IOExceptionWrapper(IOException cause) {
        super(cause);
      }

      @Override
      public synchronized IOException getCause() {
        return (IOException) super.getCause();
      }
    }

    @Override
    public void appendShortened(Appendable a, TypeMirror mirror) throws IOException {
      try {
        mirror.accept(this, a);
      } catch (IOExceptionWrapper e) {
        throw e.getCause();
      }
    }

    @Override
    public Void visitDeclared(DeclaredType mirror, Appendable a) {
      try {
        if (mirror.getEnclosingType().getKind() == TypeKind.NONE) {
          appendShortened(a, asElement(mirror));
        } else {
          mirror.getEnclosingType().accept(this, a);
          a.append('.').append(mirror.asElement().getSimpleName());
        }
        if (!mirror.getTypeArguments().isEmpty()) {
          String prefix = "<";
          for (TypeMirror typeArgument : mirror.getTypeArguments()) {
            a.append(prefix);
            typeArgument.accept(this, a);
            prefix = ", ";
          }
          a.append(">");
        }
        return null;
      } catch (IOException e) {
        throw new IOExceptionWrapper(e);
      }
    }

    @Override
    public Void visitWildcard(WildcardType t, Appendable a) {
      try {
        a.append("?");
        if (t.getSuperBound() != null) {
          a.append(" super ");
          t.getSuperBound().accept(this, a);
        }
        if (t.getExtendsBound() != null) {
          a.append(" extends ");
          t.getExtendsBound().accept(this, a);
        }
        return null;
      } catch (IOException e) {
        throw new IOExceptionWrapper(e);
      }
    }

    @Override
    protected Void defaultAction(TypeMirror mirror, Appendable a) {
      try {
        a.append(mirror.toString());
        return null;
      } catch (IOException e) {
        throw new IOExceptionWrapper(e);
      }
    }
  }

  /** A {@link TypeShortener} that never shortens types. */
  class NeverShorten extends AbstractTypeShortener {

    @Override
    public void appendShortened(Appendable a, QualifiedName type) throws IOException {
      a.append(type.getPackage());
      String separator = (type.getPackage().isEmpty()) ? "" : ".";
      for (String simpleName : type.getSimpleNames()) {
        a.append(separator).append(simpleName);
        separator = ".";
      }
    }

    @Override
    public void appendShortened(Appendable a, TypeElement type) throws IOException {
      a.append(type.toString());
    }

    @Override
    public Optional<QualifiedName> lookup(String shortenedType) {
      throw new UnsupportedOperationException();
    }
  }

  /** A {@link TypeShortener} that always shortens types, even if that causes conflicts. */
  class AlwaysShorten extends AbstractTypeShortener {

    @Override
    public void appendShortened(Appendable a, QualifiedName type) throws IOException {
      String separator = "";
      for (String simpleName : type.getSimpleNames()) {
        a.append(separator).append(simpleName);
        separator = ".";
      }
    }

    @Override
    public void appendShortened(Appendable a, TypeElement type) throws IOException {
      if (type.getNestingKind().isNested()) {
        appendShortened(a, (TypeElement) type.getEnclosingElement());
        a.append('.');
      }
      a.append(type.getSimpleName());
    }

    @Override
    public Optional<QualifiedName> lookup(String shortenedType) {
      throw new UnsupportedOperationException();
    }
  }
}
