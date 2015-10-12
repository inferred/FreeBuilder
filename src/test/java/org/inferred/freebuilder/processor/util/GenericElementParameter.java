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
import static com.google.common.collect.Iterables.getOnlyElement;
import static org.inferred.freebuilder.processor.util.ClassTypeImpl.newTopLevelClass;
import static org.inferred.freebuilder.processor.util.NullTypeImpl.NULL;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.TypeVisitor;

/**
 * Fake implementation of a formal type parameter of a {@link GenericElement}.
 */
public class GenericElementParameter implements TypeParameterElement {

  /**
   * Builder of {@link GenericElementParameter} instances.
   */
  public static class Builder {
    private final String simpleName;
    private final List<TypeMirror> bounds = new ArrayList<>();
    private final AtomicReference<GenericElementParameter> element = new AtomicReference<>();

    Builder(String simpleName) {
      this.simpleName = simpleName;
    }

    public Builder addBound(TypeMirror bound) {
      checkState(element.get() == null,
          "Cannot modify a %s after calling build()", Builder.class.getName());
      bounds.add(bound);
      return this;
    }

    public TypeVariableImpl asType() {
      return new TypeVariableImpl(element);
    }

    GenericElementParameter build(GenericElement genericElement) {
      GenericElementParameter impl =
          new GenericElementParameter(genericElement, simpleName, bounds);
      boolean notYetSet = element.compareAndSet(null, impl);
      checkState(notYetSet, "Cannot call build() twice on a %s", Builder.class.getName());
      return impl;
    }

  }

  private final GenericElement genericElement;
  private final String simpleName;
  private final ImmutableList<TypeMirror> bounds;

  private GenericElementParameter(
      GenericElement genericElement, String simpleName, Iterable<? extends TypeMirror> bounds) {
    this.genericElement = genericElement;
    this.simpleName = simpleName;
    this.bounds = ImmutableList.copyOf(bounds);
  }

  @Override
  public TypeVariableImpl asType() {
    return new TypeVariableImpl(new AtomicReference<>(this));
  }

  @Override
  public ElementKind getKind() {
    return ElementKind.TYPE_PARAMETER;
  }

  @Override
  public List<? extends AnnotationMirror> getAnnotationMirrors() {
    return ImmutableList.of();
  }

  @Override
  public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
    return null;
  }

  @Override
  public Set<Modifier> getModifiers() {
    return ImmutableSet.of();
  }

  @Override
  public Name getSimpleName() {
    return new NameImpl(simpleName);
  }

  @Override
  public List<? extends Element> getEnclosedElements() {
    return ImmutableList.of();
  }

  @Override
  public <R, P> R accept(ElementVisitor<R, P> v, P p) {
    return v.visitTypeParameter(this, p);
  }

  @Override
  public GenericElement getGenericElement() {
    return genericElement;
  }

  @Override
  public List<? extends TypeMirror> getBounds() {
    return bounds;
  }

  @Override
  public GenericElement getEnclosingElement() {
    return genericElement;
  }

  @Override
  public String toString() {
    return simpleName;
  }

  /**
   * Fake implementation of a type variable declared by a {@link GenericElement}.
   */
  public static class TypeVariableImpl implements TypeVariable {

    private final AtomicReference<GenericElementParameter> element;

    private TypeVariableImpl(AtomicReference<GenericElementParameter> element) {
      this.element = element;
    }

    @Override
    public TypeKind getKind() {
      return TypeKind.TYPEVAR;
    }

    @Override
    public <R, P> R accept(TypeVisitor<R, P> v, P p) {
      return v.visitTypeVariable(this, p);
    }

    @Override
    public GenericElementParameter asElement() {
      GenericElementParameter impl = getImpl("asElement()");
      return impl;
    }

    @Override
    public TypeMirror getUpperBound() {
      GenericElementParameter impl = getImpl("getUpperBound()");
      switch (impl.bounds.size()) {
      case 0:
        return newTopLevelClass("java.lang.Object");
      case 1:
        return getOnlyElement(impl.bounds);
      default:
        throw new UnsupportedOperationException();
      }
    }

    @Override
    public TypeMirror getLowerBound() {
      return NULL;
    }

    @Override
    public String toString() {
      return getImpl("toString()").simpleName;
    }

    private GenericElementParameter getImpl(String calledMethod) {
      GenericElementParameter impl = element.get();
      checkState(impl != null,
          "Cannot call %s on a TypeVariable returned from a %s before it is built",
          calledMethod,
          GenericElementParameter.Builder.class.getName());
      return impl;
    }

  }

}
