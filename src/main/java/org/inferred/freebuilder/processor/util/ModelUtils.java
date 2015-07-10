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
import java.util.Map.Entry;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleElementVisitor6;
import javax.lang.model.util.SimpleTypeVisitor6;

import com.google.common.base.Optional;

/**
 * Utility methods for the javax.lang.model package.
 */
public class ModelUtils {

  /**
   * Returns an {@link AnnotationMirror} for the annotation of type {@code annotationClass} on
   * {@code element}, or {@link Optional#absent()} if no such annotation exists.
   */
  public static Optional<AnnotationMirror> findAnnotationMirror(
      Element element, Class<? extends Annotation> annotationClass) {
    return findAnnotationMirror(element, Shading.unshadedName(annotationClass.getName()));
  }

  /**
   * Returns an {@link AnnotationMirror} for the annotation of type {@code annotationClassName} on
   * {@code element}, or {@link Optional#absent()} if no such annotation exists.
   */
  public static Optional<AnnotationMirror> findAnnotationMirror(
      Element element, String annotationClassName) {
    for (AnnotationMirror annotationMirror : element.getAnnotationMirrors()) {
      TypeElement annotationTypeElement =
          (TypeElement) (annotationMirror.getAnnotationType().asElement());
      if (annotationTypeElement.getQualifiedName().contentEquals(annotationClassName)) {
        return Optional.of(annotationMirror);
      }
    }
    return Optional.absent();
  }

  public static Optional<AnnotationValue> findProperty(AnnotationMirror annotation, String propertyName) {
    for (Entry<? extends ExecutableElement, ? extends AnnotationValue> element
        : annotation.getElementValues().entrySet()) {
      if (element.getKey().getSimpleName().contentEquals(propertyName)) {
        return Optional.<AnnotationValue>of(element.getValue());
      }
    }
    return Optional.absent();
  }

  /** Returns {@code element} as a {@link TypeElement}, if it is one. */
  public static Optional<TypeElement> maybeType(Element element) {
    return TYPE_ELEMENT_VISITOR.visit(element);
  }

  /** Returns {@code type} as a {@link DeclaredType}, if it is one. */
  public static Optional<DeclaredType> maybeDeclared(TypeMirror type) {
    return DECLARED_TYPE_VISITOR.visit(type);
  }

  /** Returns the {@link TypeElement} corresponding to {@code type}, if there is one. */
  public static Optional<TypeElement> maybeAsTypeElement(TypeMirror type) {
    Optional<DeclaredType> declaredType = maybeDeclared(type);
    if (declaredType.isPresent()) {
      return maybeType(declaredType.get().asElement());
    } else {
      return Optional.absent();
    }
  }

  private static final SimpleElementVisitor6<Optional<TypeElement>, ?> TYPE_ELEMENT_VISITOR =
      new SimpleElementVisitor6<Optional<TypeElement>, Void>() {

        @Override
        public Optional<TypeElement> visitType(TypeElement e, Void p) {
          return Optional.of(e);
        }

        @Override
        protected Optional<TypeElement> defaultAction(Element e, Void p) {
          return Optional.absent();
        }
      };

  private static final SimpleTypeVisitor6<Optional<DeclaredType>, ?> DECLARED_TYPE_VISITOR =
      new SimpleTypeVisitor6<Optional<DeclaredType>, Void>() {

        @Override
        public Optional<DeclaredType> visitDeclared(DeclaredType t, Void p) {
          return Optional.of(t);
        }

        @Override
        protected Optional<DeclaredType> defaultAction(TypeMirror e, Void p) {
          return Optional.absent();
        }
      };
}

