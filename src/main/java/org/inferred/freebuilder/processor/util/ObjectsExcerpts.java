package org.inferred.freebuilder.processor.util;

import java.util.Objects;

import com.google.common.base.Preconditions;

import javax.lang.model.type.TypeKind;

public class ObjectsExcerpts {

  /**
   * Returns an Excerpt equivalent to {@code Objects.equals(a, b)}.
   *
   * <p>Uses == for primitive types, as this avoids boxing.
   */
  public static Excerpt equals(Object a, Object b, TypeKind kind) {
    return new EqualsExcerpt(true, a, b, kind);
  }

  /**
   * Returns an Excerpt equivalent to {@code !Objects.equals(a, b)}.
   *
   * <p>Uses != for primitive types, as this avoids boxing.
   */
  public static Excerpt notEquals(Object a, Object b, TypeKind kind) {
    return new EqualsExcerpt(false, a, b, kind);
  }

  private static class EqualsExcerpt extends Excerpt {

    private final boolean areEqual;
    private final Object a;
    private final Object b;
    private final TypeKind kind;

    EqualsExcerpt(boolean areEqual, Object a, Object b, TypeKind kind) {
      this.areEqual = areEqual;
      this.a = a;
      this.b = b;
      this.kind = kind;
    }

    @Override
    public void addTo(SourceBuilder code) {
      switch (kind) {
        case FLOAT:
          code.add("%1$s.floatToIntBits(%2$s) %3$s %1$s.floatToIntBits(%4$s)",
              Float.class, a, areEqual ? "==" : "!=", b);
          return;

        case DOUBLE:
          code.add("%1$s.doubleToLongBits(%2$s) %3$s %1$s.doubleToLongBits(%4$s)",
              Double.class, a, areEqual ? "==" : "!=", b);
          return;

        case BOOLEAN:
        case BYTE:
        case SHORT:
        case INT:
        case LONG:
        case CHAR:
          code.add("%s %s %s", a, areEqual ? "==" : "!=", b);
          return;

        default:
          Preconditions.checkState(!kind.isPrimitive(), "Unexpected primitive type " + kind);
          code.add("%s%s.equals(%s, %s)", areEqual ? "" : "!", Objects.class, a, b);
          return;
      }
    }

    @Override
    protected void addFields(FieldReceiver fields) {
      fields.add("areEqual", areEqual);
      fields.add("a", a);
      fields.add("b", b);
      fields.add("kind", kind);
    }
  }

  private ObjectsExcerpts() {}
}
