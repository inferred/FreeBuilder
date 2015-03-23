package org.inferred.freebuilder.processor.util;

import static org.inferred.freebuilder.processor.util.ClassTypeImpl.newTopLevelClass;

import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.TypeVisitor;

/**
 * Fake implementation of {@link TypeVariable} for unit tests.
 */
public abstract class TypeVariableImpl implements TypeVariable {

  public static TypeVariable newTypeVariable(String variableName) {
    return Partial.of(TypeVariableImpl.class, variableName);
  }

  private final String variableName;

  TypeVariableImpl(String variableName) {
    this.variableName = variableName;
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
  public TypeMirror getUpperBound() {
    return newTopLevelClass("java.lang.Object");
  }

  @Override
  public TypeMirror getLowerBound() {
    return NullTypeImpl.NULL_TYPE;
  }

  @Override
  public String toString() {
    return variableName;
  }
}
