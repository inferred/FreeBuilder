package org.inferred.freebuilder.processor.source;

import static org.inferred.freebuilder.processor.model.ModelUtils.asElement;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.SimpleTypeVisitor8;

class TypeMirrorAppender extends SimpleTypeVisitor8<Void, QualifiedNameAppendable> {

  private static final TypeMirrorAppender INSTANCE = new TypeMirrorAppender();

  public static void appendShortened(TypeMirror mirror, QualifiedNameAppendable a) {
    mirror.accept(INSTANCE, a);
  }

  private TypeMirrorAppender() {}

  @Override
  public Void visitDeclared(DeclaredType mirror, QualifiedNameAppendable a) {
    if (!isInnerClass(mirror)) {
      a.append(QualifiedName.of(asElement(mirror)));
    } else {
      mirror.getEnclosingType().accept(this, a);
      a.append('.');
      a.append(mirror.asElement().getSimpleName());
    }
    if (!mirror.getTypeArguments().isEmpty()) {
      String prefix = "<";
      for (TypeMirror typeArgument : mirror.getTypeArguments()) {
        a.append(prefix);
        typeArgument.accept(this, a);
        prefix = ", ";
      }
      a.append(">");
    }
    return null;
  }

  private static boolean isInnerClass(DeclaredType mirror) {
    if (mirror.getEnclosingType().getKind() == TypeKind.NONE) {
      return false;
    }
    // Work around a little Eclipse bug
    if (asElement(mirror).getModifiers().contains(Modifier.STATIC)) {
      return false;
    }
    return true;
  }

  @Override
  public Void visitWildcard(WildcardType t, QualifiedNameAppendable a) {
    a.append("?");
    if (t.getSuperBound() != null) {
      a.append(" super ");
      t.getSuperBound().accept(this, a);
    }
    if (t.getExtendsBound() != null) {
      a.append(" extends ");
      t.getExtendsBound().accept(this, a);
    }
    return null;
  }

  @Override
  protected Void defaultAction(TypeMirror mirror, QualifiedNameAppendable a) {
    a.append(mirror.toString());
    return null;
  }
}
