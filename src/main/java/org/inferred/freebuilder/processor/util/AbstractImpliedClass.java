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

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleElementVisitor6;

/**
 * Abstract superclass of a {@link TypeElement} representing a class that needs to be generated.
 *
 * <p>This type implements {@link TypeElement}, as the API is stable, well-known, and lets
 * processors treat to-be-generated and user-supplied types the same way. However, not all methods
 * are implemented (those that are not are marked {@link Deprecated @Deprecated}); additionally,
 * compilers are free to implement {@link Elements} and {@link javax.lang.model.util.Types Types}
 * in a manner hostile to third-party classes (e.g. by casting a {@link TypeElement} to a private
 * internal class), so they are, unfortunately, not fully interchangeable at present.
 */
abstract class AbstractImpliedClass<E extends Element> implements TypeElement {

  private final E enclosingElement;
  private final Name qualifiedName;
  private final Name simpleName;

  AbstractImpliedClass(
      E enclosingElement,
      CharSequence simpleName,
      Elements elementUtils) {
    this.enclosingElement = enclosingElement;
    this.qualifiedName = elementUtils.getName(
        GET_QUALIFIED_NAME.visit(enclosingElement) + "." + simpleName);
    this.simpleName = elementUtils.getName(simpleName);
  }

  @Override
  public <R, P> R accept(ElementVisitor<R, P> v, P p) {
    return v.visitType(this, p);
  }

  @Override
  @Deprecated
  public TypeMirror asType() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public List<? extends AnnotationMirror> getAnnotationMirrors() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ElementKind getKind() {
    return ElementKind.CLASS;
  }

  @Override
  @Deprecated
  public Set<Modifier> getModifiers() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public List<? extends Element> getEnclosedElements() {
    throw new UnsupportedOperationException();
  }

  @Override
  public E getEnclosingElement() {
    return enclosingElement;
  }

  @Override
  @Deprecated
  public List<? extends TypeMirror> getInterfaces() {
    throw new UnsupportedOperationException();
  }

  @Override
  public NestingKind getNestingKind() {
    return (enclosingElement.getKind() == ElementKind.PACKAGE)
        ? NestingKind.TOP_LEVEL : NestingKind.MEMBER;
  }

  @Override
  public Name getQualifiedName() {
    return qualifiedName;
  }

  @Override
  public Name getSimpleName() {
    return simpleName;
  }

  @Override
  @Deprecated
  public TypeMirror getSuperclass() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public List<? extends TypeParameterElement> getTypeParameters() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String toString() {
    return "class " + qualifiedName;
  }

  @Override
  public int hashCode() {
    return qualifiedName.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return ((obj instanceof AbstractImpliedClass)
        && ((TypeElement) obj).getQualifiedName().equals(qualifiedName));
  }

  // JDK8 Compatibility:

  public <A extends Annotation> A[] getAnnotationsByType(
      @SuppressWarnings("unused") Class<A> annotationType) {
    throw new UnsupportedOperationException();
  }

  private static final ElementVisitor<CharSequence, ?> GET_QUALIFIED_NAME =
      new SimpleElementVisitor6<CharSequence, Void>() {
        @Override
        public CharSequence visitPackage(PackageElement e, Void p) {
          return e.getQualifiedName();
        }

        @Override
        public CharSequence visitType(TypeElement e, Void p) {
          return e.getQualifiedName();
        }

        @Override
        protected CharSequence defaultAction(Element e, Void p) {
          throw new IllegalArgumentException("Unsupported enclosing element " + e.getKind());
        }
      };
}
