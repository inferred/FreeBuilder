package org.inferred.freebuilder.processor.util;

import static org.inferred.freebuilder.processor.util.feature.SourceLevel.SOURCE_LEVEL;

import com.google.common.base.Preconditions;

import javax.lang.model.type.TypeKind;

public class ObjectsExcerpts {

  public enum Nullability {
    NULLABLE, NOT_NULLABLE;

    public boolean isNullable() {
      return this == NULLABLE;
    }
  }

  /**
   * Returns an Excerpt equivalent to {@code Objects.equals(a, b)}.
   *
   * <p>If Objects is not available, {@code kind} and {@code nullability} are needed to generate
   * the most idiomatic equivalent.
   */
  public static Excerpt equals(Object a, Object b, TypeKind kind, Nullability nullability) {
    return new EqualsExcerpt(true, a, b, kind, nullability);
  }

  /**
   * Returns an Excerpt equivalent to {@code !Objects.equals(a, b)}.
   *
   * <p>If Objects is not available, {@code kind} and {@code nullability} are needed to generate
   * the most idiomatic equivalent.
   */
  public static Excerpt notEquals(Object a, Object b, TypeKind kind, Nullability nullability) {
    return new EqualsExcerpt(false, a, b, kind, nullability);
  }

  private static class EqualsExcerpt extends Excerpt {

    private final boolean areEqual;
    private final Object a;
    private final Object b;
    private final TypeKind kind;
    private final Nullability nullability;

    EqualsExcerpt(boolean areEqual, Object a, Object b, TypeKind kind, Nullability nullability) {
      this.areEqual = areEqual;
      this.a = a;
      this.b = b;
      this.kind = kind;
      this.nullability = nullability;
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
          QualifiedName javaUtilObjects = code.feature(SOURCE_LEVEL).javaUtilObjects().orNull();
          if (javaUtilObjects != null) {
            code.add("%s%s.equals(%s, %s)", areEqual ? "" : "!", javaUtilObjects, a, b);
          } else if (nullability.isNullable()) {
            if (areEqual) {
              code.add("%1$s == %2$s || (%1$s != null && %1$s.equals(%2$s))", a, b);
            } else {
              code.add("%1$s != %2$s && (%1$s == null || !%1$s.equals(%2$s))", a, b);
            }
          } else {
            code.add("%s%s.equals(%s)", areEqual ? "" : "!", a, b);
          }
          return;
      }
    }

    @Override
    protected void addFields(FieldReceiver fields) {
      fields.add("areEqual", areEqual);
      fields.add("a", a);
      fields.add("b", b);
      fields.add("kind", kind);
      fields.add("nullable", nullability);
    }
  }

  private ObjectsExcerpts() {}
}
