package org.inferred.freebuilder.processor.util;

import java.util.Objects;

import javax.lang.model.type.TypeKind;

public class ObjectsExcerpts {

  /**
   * Returns an Excerpt equivalent to {@code Objects.equals(a, b)}.
   *
   * <p>Uses == for appropriate primitive types, as this is generally more readable.
   */
  public static Excerpt equals(Object a, Object b, TypeKind kind) {
    return new EqualsExcerpt(true, a, b, kind);
  }

  /**
   * Returns an Excerpt equivalent to {@code !Objects.equals(a, b)}.
   *
   * <p>Uses != for appropriate primitive types, as this is generally more readable.
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
      if (isComparableWithOperator(kind)) {
        code.add("%s %s %s", a, areEqual ? "==" : "!=", b);
      } else {
        code.add("%s%s.equals(%s, %s)", areEqual ? "" : "!", Objects.class, a, b);
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

  private static boolean isComparableWithOperator(TypeKind kind) {
    switch (kind) {
      case BOOLEAN:
      case BYTE:
      case SHORT:
      case INT:
      case LONG:
      case CHAR:
        return true;

      default:
        return false;
    }
  }

  private ObjectsExcerpts() {}
}
