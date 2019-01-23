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

import static javax.lang.model.util.ElementFilter.methodsIn;

import com.google.common.collect.ImmutableSet;

import java.lang.annotation.Annotation;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.SimpleElementVisitor8;
import javax.lang.model.util.SimpleTypeVisitor8;
import javax.lang.model.util.Types;

/**
 * Utility methods for the javax.lang.model package.
 */
public class ModelUtils {

  /**
   * Returns an {@link AnnotationMirror} for the annotation of type {@code annotationClass} on
   * {@code element}, or {@link Optional#empty()} if no such annotation exists.
   */
  public static Optional<AnnotationMirror> findAnnotationMirror(
      Element element, Class<? extends Annotation> annotationClass) {
    return findAnnotationMirror(element, Shading.unshadedName(annotationClass.getName()));
  }

  /**
   * Returns an {@link AnnotationMirror} for the annotation of type {@code annotationClass} on
   * {@code element}, or {@link Optional#empty()} if no such annotation exists.
   */
  public static Optional<AnnotationMirror> findAnnotationMirror(
      Element element, QualifiedName annotationClass) {
    return findAnnotationMirror(element, Shading.unshadedName(annotationClass.toString()));
  }

  /**
   * Returns an {@link AnnotationMirror} for the annotation of type {@code annotationClassName} on
   * {@code element}, or {@link Optional#empty()} if no such annotation exists.
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
    return Optional.empty();
  }

  public static Optional<AnnotationValue> findProperty(
      AnnotationMirror annotation,
      String propertyName) {
    return annotation
        .getElementValues()
        .entrySet()
        .stream()
        .filter(element -> element.getKey().getSimpleName().contentEquals(propertyName))
        .findAny()
        .map(Entry::getValue);
  }

  /** Returns {@code element} as a {@link TypeElement}, if it is one. */
  public static Optional<TypeElement> maybeType(Element element) {
    return TYPE_ELEMENT_VISITOR.visit(element);
  }

  /** Returns {@code type} as a {@link DeclaredType}, if it is one. */
  public static Optional<DeclaredType> maybeDeclared(TypeMirror type) {
    return DECLARED_TYPE_VISITOR.visit(type);
  }

  public static Optional<TypeVariable> maybeVariable(TypeMirror type) {
    return TYPE_VARIABLE_VISITOR.visit(type);
  }

  /** Returns the {@link TypeElement} corresponding to {@code type}, if there is one. */
  public static Optional<TypeElement> maybeAsTypeElement(TypeMirror type) {
    Optional<DeclaredType> declaredType = maybeDeclared(type);
    if (declaredType.isPresent()) {
      return maybeType(declaredType.get().asElement());
    } else {
      return Optional.empty();
    }
  }

  /** Returns the {@link TypeElement} corresponding to {@code type}. */
  public static TypeElement asElement(DeclaredType type) {
    return maybeType(type.asElement()).get();
  }

  /** Applies unboxing conversion to {@code mirror}, if it can be unboxed. */
  public static Optional<TypeMirror> maybeUnbox(TypeMirror mirror, Types types) {
    try {
      return Optional.of(types.unboxedType(mirror));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  /** Returns the method on {@code type} that overrides method {@code methodName(params)}. */
  public static Optional<ExecutableElement> override(
      TypeElement type, Types types, String methodName, TypeMirror... params) {
    return methodsIn(type.getEnclosedElements())
        .stream()
        .filter(method -> signatureMatches(method, types, methodName, params))
        .findAny();
  }

  /** Returns the method on {@code type} that overrides method {@code methodName(params)}. */
  public static Optional<ExecutableElement> override(
      DeclaredType type, Types types, String methodName, TypeMirror... params) {
    return override(asElement(type), types, methodName, params);
  }

  /** Returns whether {@code type} overrides method {@code methodName(params)}. */
  public static boolean overrides(
      TypeElement type, Types types, String methodName, TypeMirror... params) {
    return override(type, types, methodName, params).isPresent();
  }

  /** Returns whether {@code type} overrides method {@code methodName(params)}. */
  public static boolean overrides(
      DeclaredType type, Types types, String methodName, TypeMirror... params) {
    return overrides(asElement(type), types, methodName, params);
  }

  /**
   * Returns true if a method with a variable number of {@code elementType} arguments needs a
   * {@code &#64;SafeVarargs} annotation to avoid compiler warnings in Java 7+.
   */
  public static boolean needsSafeVarargs(TypeMirror elementType) {
    return elementType.accept(new SimpleTypeVisitor8<Boolean, Void>() {
      @Override
      public Boolean visitDeclared(DeclaredType t, Void p) {
        // Set<?>... does not need @SafeVarargs; Set<Integer>... or Set<? extends Number> does.
        for (TypeMirror typeArgument : t.getTypeArguments()) {
          if (!isPlainWildcard(typeArgument)) {
            return true;
          }
        }
        return false;
      }

      @Override
      public Boolean visitTypeVariable(TypeVariable t, Void p) {
        return true;
      }

      @Override
      protected Boolean defaultAction(TypeMirror e, Void p) {
        return false;
      }

      @Override
      public Boolean visitUnknown(TypeMirror t, Void p) {
        return false;
      }
    }, null);
  }

  public static Set<ExecutableElement> only(Modifier modifier, Set<ExecutableElement> methods) {
    ImmutableSet.Builder<ExecutableElement> result = ImmutableSet.builder();
    for (ExecutableElement method : methods) {
      if (method.getModifiers().contains(modifier)) {
        result.add(method);
      }
    }
    return result.build();
  }

  private static boolean isPlainWildcard(TypeMirror type) {
    return type.accept(new SimpleTypeVisitor8<Boolean, Void>() {
      @Override
      public Boolean visitWildcard(WildcardType t, Void p) {
        return (t.getExtendsBound() == null) && (t.getSuperBound() == null);
      }

      @Override
      protected Boolean defaultAction(TypeMirror e, Void p) {
        return false;
      }

      @Override
      public Boolean visitUnknown(TypeMirror t, Void p) {
        return false;
      }
    }, null);
  }

  private static boolean signatureMatches(
      ExecutableElement method, Types types, String name, TypeMirror... params) {
    if (!method.getSimpleName().contentEquals(name)) {
      return false;
    }
    if (method.getParameters().size() != params.length) {
      return false;
    }
    for (int i = 0; i < params.length; ++i) {
      TypeMirror expected = types.erasure(params[i]);
      TypeMirror actual = types.erasure(method.getParameters().get(i).asType());
      if (!types.isSameType(expected, actual)) {
        return false;
      }
    }
    return true;
  }

  private static final SimpleElementVisitor8<Optional<TypeElement>, ?> TYPE_ELEMENT_VISITOR =
      new SimpleElementVisitor8<Optional<TypeElement>, Void>() {

        @Override
        public Optional<TypeElement> visitType(TypeElement e, Void p) {
          return Optional.of(e);
        }

        @Override
        protected Optional<TypeElement> defaultAction(Element e, Void p) {
          return Optional.empty();
        }
      };

  private static final SimpleTypeVisitor8<Optional<DeclaredType>, ?> DECLARED_TYPE_VISITOR =
      new SimpleTypeVisitor8<Optional<DeclaredType>, Void>() {

        @Override
        public Optional<DeclaredType> visitDeclared(DeclaredType t, Void p) {
          return Optional.of(t);
        }

        @Override
        protected Optional<DeclaredType> defaultAction(TypeMirror e, Void p) {
          return Optional.empty();
        }
      };

  private static final SimpleTypeVisitor8<Optional<TypeVariable>, ?> TYPE_VARIABLE_VISITOR =
      new SimpleTypeVisitor8<Optional<TypeVariable>, Void>() {

        @Override
        public Optional<TypeVariable> visitTypeVariable(TypeVariable t, Void p) {
          return Optional.of(t);
        }

        @Override
        protected Optional<TypeVariable> defaultAction(TypeMirror e, Void p) {
          return Optional.empty();
        }
      };

  /**
   * Determines the return type of {@code method}, if called on an instance of type {@code type}.
   *
   * <p>For instance, in this example, myY.getProperty() returns List&lt;T&gt;, not T:<pre><code>
   *    interface X&lt;T&gt; {
   *      T getProperty();
   *    }
   *    &#64;FreeBuilder interface Y&lt;T&gt; extends X&lt;List&lt;T&gt;&gt; { }</code></pre>
   *
   * <p>(Unfortunately, a bug in Eclipse prevents us handling these cases correctly at the moment.
   * javac works fine.)
   */
  public static TypeMirror getReturnType(TypeElement type, ExecutableElement method, Types types) {
    try {
      ExecutableType executableType = (ExecutableType)
          types.asMemberOf((DeclaredType) type.asType(), method);
      return executableType.getReturnType();
    } catch (IllegalArgumentException e) {
      // Eclipse incorrectly throws an IllegalArgumentException here:
      //    "element is not valid for the containing declared type"
      // As a workaround for the common case, fall back to the declared return type.
      return method.getReturnType();
    }
  }

}
