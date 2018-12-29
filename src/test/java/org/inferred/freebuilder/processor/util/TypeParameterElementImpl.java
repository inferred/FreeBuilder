package org.inferred.freebuilder.processor.util;

import com.google.common.collect.ImmutableList;

import java.util.List;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;

public abstract class TypeParameterElementImpl implements TypeParameterElement {

  public static TypeParameterElementImpl newTypeParameterElement(String variableName) {
    return Partial.of(TypeParameterElementImpl.class, variableName);
  }

  private final String variableName;

  TypeParameterElementImpl(String variableName) {
    this.variableName = variableName;
  }

  @Override
  public TypeVariable asType() {
    return TypeVariableImpl.newTypeVariable(variableName);
  }

  @Override
  public ElementKind getKind() {
    return ElementKind.TYPE_PARAMETER;
  }

  @Override
  public Name getSimpleName() {
    return new NameImpl(variableName);
  }

  @Override
  public <R, P> R accept(ElementVisitor<R, P> v, P p) {
    return v.visitTypeParameter(this, p);
  }

  @Override
  public List<TypeMirror> getBounds() {
    return ImmutableList.of();
  }

  @Override
  public String toString() {
    return variableName;
  }
}
