package org.inferred.freebuilder.processor;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.util.AbstractElementVisitor6;

import org.inferred.freebuilder.processor.util.NameImpl;
import org.inferred.freebuilder.processor.util.NoTypes;
import org.inferred.freebuilder.processor.util.PackageElementImpl;
import org.inferred.freebuilder.processor.util.Partial;
import org.inferred.freebuilder.processor.util.ValueType;

import com.google.common.collect.ImmutableList;

abstract class GenericTypeElementImpl extends ValueType
    implements javax.lang.model.element.TypeElement {

  static GenericTypeElementImpl newTopLevelGenericType(String qualifiedName) {
    String pkg = qualifiedName.substring(0, qualifiedName.lastIndexOf('.'));
    String simpleName = qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
    PackageElement enclosingElement = new PackageElementImpl(pkg);
    return Partial.of(GenericTypeElementImpl.class, enclosingElement, NoTypes.NONE, simpleName);
  }

  private final Element enclosingElement;
  private final TypeMirror enclosingType;
  private final String simpleName;

  public GenericTypeElementImpl(
      Element enclosingElement, TypeMirror enclosingType, String simpleName) {
    this.enclosingElement = enclosingElement;
    this.enclosingType = enclosingType;
    this.simpleName = simpleName;
  }

  @Override
  protected void addFields(FieldReceiver fields) {
    fields.add("enclosingElement", enclosingElement);
    fields.add("enclosingType", enclosingType);
    fields.add("simpleName", simpleName);
  }

  public GenericTypeMirrorImpl newMirror(TypeMirror... typeArguments) {
    return new GenericTypeMirrorImpl(ImmutableList.copyOf(typeArguments));
  }

  @Override
  public ElementKind getKind() {
    return ElementKind.CLASS;
  }

  @Override
  public <R, P> R accept(ElementVisitor<R, P> v, P p) {
    return v.visitType(this, p);
  }

  @Override
  public NestingKind getNestingKind() {
    return (enclosingElement.getKind() == ElementKind.PACKAGE)
        ? NestingKind.TOP_LEVEL : NestingKind.MEMBER;
  }

  @Override
  public Name getQualifiedName() {
    return new NameImpl(GET_QUALIFIED_NAME.visit(enclosingElement) + "." + simpleName);
  }

  @Override
  public Name getSimpleName() {
    return new NameImpl(simpleName);
  }

  @Override
  public Element getEnclosingElement() {
    return enclosingElement;
  }

  class GenericTypeMirrorImpl extends ValueType implements DeclaredType {
    private final ImmutableList<TypeMirror> typeArguments;

    GenericTypeMirrorImpl(ImmutableList<TypeMirror> typeArguments) {
      this.typeArguments = typeArguments;
    }

    @Override
    protected void addFields(FieldReceiver fields) {
      fields.add("GenericTypeElementImpl.this", GenericTypeElementImpl.this);
      fields.add("typeArguments", typeArguments);
    }

    @Override
    public TypeKind getKind() {
      return TypeKind.DECLARED;
    }

    @Override
    public <R, P> R accept(TypeVisitor<R, P> v, P p) {
      return v.visitDeclared(this, p);
    }

    @Override
    public GenericTypeElementImpl asElement() {
      return GenericTypeElementImpl.this;
    }

    @Override
    public TypeMirror getEnclosingType() {
      return enclosingType;
    }

    @Override
    public ImmutableList<TypeMirror> getTypeArguments() {
      return typeArguments;
    }
  }

  private static final AbstractElementVisitor6<Name, ?> GET_QUALIFIED_NAME =
      new AbstractElementVisitor6<Name, Void>() {

        @Override
        public Name visitPackage(PackageElement e, Void p) {
          return e.getQualifiedName();
        }

        @Override
        public Name visitType(TypeElement e, Void p) {
          return e.getQualifiedName();
        }

        @Override
        public Name visitVariable(VariableElement e, Void p) {
          throw new IllegalArgumentException();
        }

        @Override
        public Name visitExecutable(ExecutableElement e, Void p) {
          throw new IllegalArgumentException();
        }

        @Override
        public Name visitTypeParameter(TypeParameterElement e, Void p) {
          throw new IllegalArgumentException();
        }
      };
}
