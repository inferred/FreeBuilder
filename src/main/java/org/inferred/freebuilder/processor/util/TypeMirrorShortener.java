package org.inferred.freebuilder.processor.util;

import static org.inferred.freebuilder.processor.util.ModelUtils.asElement;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.SimpleTypeVisitor6;

class TypeMirrorShortener
    extends SimpleTypeVisitor6<Void, TypeMirrorShortener.QualifiedNameAppendable<?>> {

  interface QualifiedNameAppendable<B extends QualifiedNameAppendable<B>> {
    B append(char c);
    B append(CharSequence csq);
    B append(QualifiedName type);
  }

  private static TypeMirrorShortener INSTANCE = new TypeMirrorShortener();

  public static void appendShortened(TypeMirror mirror, QualifiedNameAppendable<?> a) {
    mirror.accept(INSTANCE, a);
  }

  private TypeMirrorShortener() { }

  @Override
  public Void visitDeclared(DeclaredType mirror, QualifiedNameAppendable<?> a) {
    if (mirror.getEnclosingType().getKind() == TypeKind.NONE) {
      a.append(QualifiedName.of(asElement(mirror)));
    } else {
      mirror.getEnclosingType().accept(this, a);
      a.append('.').append(mirror.asElement().getSimpleName());
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

  @Override
  public Void visitWildcard(WildcardType t, QualifiedNameAppendable<?> a) {
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
  protected Void defaultAction(TypeMirror mirror, QualifiedNameAppendable<?> a) {
    a.append(mirror.toString());
    return null;
  }
}
