package org.inferred.freebuilder.processor.util;

import static org.inferred.freebuilder.processor.util.ModelUtils.asElement;

import java.io.IOException;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.SimpleTypeVisitor6;

class TypeMirrorShortener extends SimpleTypeVisitor6<Void, TypeShortener> {

  private static class IOExceptionWrapper extends RuntimeException {
    IOExceptionWrapper(IOException cause) {
      super(cause);
    }

    @Override
    public synchronized IOException getCause() {
      return (IOException) super.getCause();
    }
  }

  private final Appendable a;

  TypeMirrorShortener(Appendable a) {
    this.a = a;
  }

  public void appendShortened(TypeShortener s, TypeMirror mirror) throws IOException {
    try {
      mirror.accept(this, s);
    } catch (IOExceptionWrapper e) {
      throw e.getCause();
    }
  }

  @Override
  public Void visitDeclared(DeclaredType mirror, TypeShortener s) {
    try {
      if (mirror.getEnclosingType().getKind() == TypeKind.NONE) {
        s.appendShortened(a, QualifiedName.of(asElement(mirror)));
      } else {
        mirror.getEnclosingType().accept(this, s);
        a.append('.').append(mirror.asElement().getSimpleName());
      }
      if (!mirror.getTypeArguments().isEmpty()) {
        String prefix = "<";
        for (TypeMirror typeArgument : mirror.getTypeArguments()) {
          a.append(prefix);
          typeArgument.accept(this, s);
          prefix = ", ";
        }
        a.append(">");
      }
      return null;
    } catch (IOException e) {
      throw new IOExceptionWrapper(e);
    }
  }

  @Override
  public Void visitWildcard(WildcardType t, TypeShortener s) {
    try {
      a.append("?");
      if (t.getSuperBound() != null) {
        a.append(" super ");
        t.getSuperBound().accept(this, s);
      }
      if (t.getExtendsBound() != null) {
        a.append(" extends ");
        t.getExtendsBound().accept(this, s);
      }
      return null;
    } catch (IOException e) {
      throw new IOExceptionWrapper(e);
    }
  }

  @Override
  protected Void defaultAction(TypeMirror mirror, TypeShortener s) {
    try {
      a.append(mirror.toString());
      return null;
    } catch (IOException e) {
      throw new IOExceptionWrapper(e);
    }
  }
}
