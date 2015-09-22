package org.inferred.freebuilder.processor.util;

import javax.lang.model.type.NullType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeVisitor;

public class NullTypeImpl implements NullType {

  public static final NullType NULL = new NullTypeImpl();

  private NullTypeImpl() {}

  @Override
  public TypeKind getKind() {
    return TypeKind.NULL;
  }

  @Override
  public <R, P> R accept(TypeVisitor<R, P> v, P p) {
    return v.visitNull(this, p);
  }

}
