package org.inferred.freebuilder.processor.model;

import org.inferred.freebuilder.processor.source.Partial;
import org.inferred.freebuilder.processor.source.ValueType;

import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.type.WildcardType;

public abstract class WildcardTypeImpl extends ValueType implements WildcardType {

  public static WildcardType wildcard() {
    return Partial.of(WildcardTypeImpl.class, null, null);
  }

  public static WildcardType wildcardSuper(TypeMirror superBound) {
    return Partial.of(WildcardTypeImpl.class, superBound, null);
  }

  public static WildcardType wildcardExtends(TypeMirror extendsBound) {
    return Partial.of(WildcardTypeImpl.class, null, extendsBound);
  }

  private final TypeMirror superBound;
  private final TypeMirror extendsBound;

  WildcardTypeImpl(TypeMirror superBound, TypeMirror extendsBound) {
    this.superBound = superBound;
    this.extendsBound = extendsBound;
  }

  @Override
  public TypeKind getKind() {
    return TypeKind.WILDCARD;
  }

  @Override
  public <R, P> R accept(TypeVisitor<R, P> v, P p) {
    return v.visitWildcard(this, p);
  }

  @Override
  public TypeMirror getExtendsBound() {
    return extendsBound;
  }

  @Override
  public TypeMirror getSuperBound() {
    return superBound;
  }

  @Override
  protected void addFields(FieldReceiver fields) {
    fields.add("superBound", superBound);
    fields.add("extendsBound", extendsBound);
  }
}
