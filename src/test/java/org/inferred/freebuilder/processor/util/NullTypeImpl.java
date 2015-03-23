package org.inferred.freebuilder.processor.util;

import javax.lang.model.type.NullType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeVisitor;

public abstract class NullTypeImpl implements NullType {

  public static final NullType NULL_TYPE = Partial.of(NullTypeImpl.class);

  @Override
  public <R, P> R accept(TypeVisitor<R, P> v, P p) {
    return v.visitNull(this, p);
  }

  @Override
  public TypeKind getKind() {
    return TypeKind.NULL;
  }
}
