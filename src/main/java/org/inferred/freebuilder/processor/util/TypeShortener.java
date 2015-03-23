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

import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleTypeVisitor6;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

/**
 * Produces type references for use in source code.
 */
interface TypeShortener {

  String shorten(Class<?> cls);
  String shorten(TypeElement type);
  String shorten(TypeMirror mirror);
  String shorten(QualifiedName type);

  /** A {@link TypeShortener} that never shortens types. */
  class NeverShorten
      extends SimpleTypeVisitor6<String, Void>
      implements Function<TypeMirror, String>, TypeShortener {

    @Override
    public String shorten(Class<?> cls) {
      return cls.getCanonicalName();
    }

    @Override
    public String shorten(TypeElement type) {
      return type.getQualifiedName().toString();
    }

    @Override
    public String shorten(TypeMirror mirror) {
      return mirror.accept(this, null);
    }

    @Override
    public String shorten(QualifiedName type) {
      return type.toString();
    }

    @Override
    public String apply(TypeMirror mirror) {
      return mirror.accept(this, null);
    }

    @Override
    public String visitDeclared(DeclaredType mirror, Void p) {
      Name name = mirror.asElement().getSimpleName();
      final String prefix;
      if (mirror.getEnclosingType().getKind() == TypeKind.NONE) {
        prefix = ((PackageElement) mirror.asElement().getEnclosingElement()).getQualifiedName()
            + ".";
      } else {
        prefix = visit(mirror.getEnclosingType()) + ".";
      }
      final String suffix;
      if (!mirror.getTypeArguments().isEmpty()) {
        List<String> shortTypeArguments = Lists.transform(mirror.getTypeArguments(), this);
        suffix = "<" + Joiner.on(", ").join(shortTypeArguments) + ">";
      } else {
        suffix = "";
      }
      return prefix + name + suffix;
    }

    @Override
    protected String defaultAction(TypeMirror mirror, Void p) {
      return mirror.toString();
    }
  }

  /** A {@link TypeShortener} that always shortens types, even if that causes conflicts. */
  class AlwaysShorten
      extends SimpleTypeVisitor6<String, Void>
      implements Function<TypeMirror, String>, TypeShortener {

    @Override
    public String shorten(Class<?> cls) {
      if (cls.getEnclosingClass() != null) {
        return shorten(cls.getEnclosingClass()) + "." + cls.getSimpleName();
      } else {
        return cls.getSimpleName();
      }
    }

    @Override
    public String shorten(TypeElement type) {
      Element parent = type.getEnclosingElement();
      if (parent.getKind().isInterface() || parent.getKind().isClass()) {
        return shorten((TypeElement) parent) + "." + type.getSimpleName();
      } else {
        return type.getSimpleName().toString();
      }
    }

    @Override
    public String shorten(TypeMirror mirror) {
      return mirror.accept(this, null);
    }

    @Override
    public String shorten(QualifiedName type) {
      return type.toString().substring(type.getPackage().length() + 1);
    }

    @Override
    public String apply(TypeMirror mirror) {
      return mirror.accept(this, null);
    }

    @Override
    public String visitDeclared(DeclaredType mirror, Void p) {
      Name name = mirror.asElement().getSimpleName();
      final String prefix;
      if (mirror.getEnclosingType().getKind() == TypeKind.NONE) {
        prefix = "";
      } else {
        prefix = visit(mirror.getEnclosingType()) + ".";
      }
      final String suffix;
      if (!mirror.getTypeArguments().isEmpty()) {
        List<String> shortTypeArguments = Lists.transform(mirror.getTypeArguments(), this);
        suffix = "<" + Joiner.on(", ").join(shortTypeArguments) + ">";
      } else {
        suffix = "";
      }
      return prefix + name + suffix;
    }

    @Override
    protected String defaultAction(TypeMirror mirror, Void p) {
      return mirror.toString();
    }
  }
}
