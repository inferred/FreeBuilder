package org.inferred.freebuilder.processor.util;

import com.google.common.base.Preconditions;

import java.util.Objects;

import javax.lang.model.type.TypeKind;

public class ObjectsExcerpts {

  /**
   * Returns an Excerpt equivalent to {@code Objects.equals(a, b)}.
   *
   * <p>Uses == for primitive types, as this avoids boxing.
   */
  public static Excerpt equals(Object a, Object b, TypeKind kind) {
    return equalsExcerpt(true, a, b, kind);
  }

  /**
   * Returns an Excerpt equivalent to {@code !Objects.equals(a, b)}.
   *
   * <p>Uses != for primitive types, as this avoids boxing.
   */
  public static Excerpt notEquals(Object a, Object b, TypeKind kind) {
    return equalsExcerpt(false, a, b, kind);
  }

  private static Excerpt equalsExcerpt(boolean areEqual, Object a, Object b, TypeKind kind) {
    switch (kind) {
      case FLOAT:
        return code -> code.add("%1$s.floatToIntBits(%2$s) %3$s %1$s.floatToIntBits(%4$s)",
            Float.class, a, areEqual ? "==" : "!=", b);

      case DOUBLE:
        return code -> code.add("%1$s.doubleToLongBits(%2$s) %3$s %1$s.doubleToLongBits(%4$s)",
            Double.class, a, areEqual ? "==" : "!=", b);

      case BOOLEAN:
      case BYTE:
      case SHORT:
      case INT:
      case LONG:
      case CHAR:
        return code -> code.add("%s %s %s", a, areEqual ? "==" : "!=", b);

      default:
        Preconditions.checkState(!kind.isPrimitive(), "Unexpected primitive type " + kind);
        return code -> code.add("%s%s.equals(%s, %s)", areEqual ? "" : "!", Objects.class, a, b);
    }
  }

  private ObjectsExcerpts() {}
}
