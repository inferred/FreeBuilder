package org.inferred.freebuilder.processor.util;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static org.inferred.freebuilder.processor.util.ClassTypeImpl.newTopLevelClass;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;

/**
 * Fake representation of a generic top-level class element.
 */
public class GenericElement implements TypeElement {

  /**
   * Builder of {@link GenericElement} instances.
   *
   * <p>Simple type parameters can be added with {@link #addTypeParameter(String, TypeMirror...)}.
   * For self-referential bounds, e.g. {@code Comparable<E extends Comparable<E>>}, add the
   * parameters first, then create the bounds with {@link #getTypeParameter(String)},
   * {@link GenericElementParameter#asType()} and {@link #asType()}.
   *
   * <pre>
   * GenericElement.Builder typeBuilder =
   *     new GenericElement.Builder(QualifiedName.of("java.lang", "Comparable"))
   *         .addTypeParameter("E");
   * typeBuilder.getTypeParameter("E").addBound(typeBuilder.asType());
   * GenericElement type = typeBuilder.build();
   */
  public static class Builder {
    private final QualifiedName qualifiedName;
    private final LinkedHashMap<String, GenericElementParameter.Builder> typeParameters =
        new LinkedHashMap<>();
    private AtomicReference<GenericElement> element;

    public Builder(QualifiedName qualifiedName) {
      checkArgument(qualifiedName.isTopLevel(),
          "GenericElement currently only supports creating top-level classes");
      this.qualifiedName = qualifiedName;
    }

    /**
     * Adds type parameter {@code simpleName} with lower bounds {@code bounds}.
     *
     * @throws IllegalStateException if asType() or build() have already been called
     * @throws IllegalStateException if a type parameter already exists with this name
     */
    public Builder addTypeParameter(String simpleName, TypeMirror... bounds) {
      checkState(element == null,
          "Cannot add a new type parameter after calling asType() or build()");
      checkState(!typeParameters.containsKey(simpleName),
          "Duplicate type parameter \"%s\"", simpleName);
      GenericElementParameter.Builder typeParameter =
          new GenericElementParameter.Builder(simpleName);
      for (TypeMirror bound : bounds) {
        typeParameter.addBound(bound);
      }
      typeParameters.put(simpleName, typeParameter);
      return this;
    }

    /**
     * Returns a builder for type parameter {@code simpleName}, creating one if necessary.
     */
    public GenericElementParameter.Builder getTypeParameter(String simpleName) {
      GenericElementParameter.Builder typeParameter = typeParameters.get(simpleName);
      if (typeParameter == null) {
        checkState(element == null,
            "Cannot add a new type parameter after calling asType() or build()");
        typeParameter = new GenericElementParameter.Builder(simpleName);
        typeParameters.put(simpleName, typeParameter);
      }
      return typeParameter;
    }

    public GenericMirror asType() {
      if (element == null) {
        element = new AtomicReference<>();
      }
      List<GenericElementParameter.TypeVariableImpl> typeArguments = new ArrayList<>();
      for (GenericElementParameter.Builder typeParameter : typeParameters.values()) {
        typeArguments.add(typeParameter.asType());
      }
      return new GenericMirror(element, typeArguments);
    }

    public GenericElement build() {
      if (element == null) {
        element = new AtomicReference<>();
      }
      checkState(element.get() == null, "Cannot call build() twice");
      GenericElement impl = new GenericElement(qualifiedName, typeParameters.values());
      element.set(impl);
      return impl;
    }
  }

  private final QualifiedName qualifiedName;
  private final ImmutableList<GenericElementParameter> typeParameters;

  GenericElement(
      QualifiedName qualifiedName,
      Iterable<? extends GenericElementParameter.Builder> typeParameterBuilders) {
    this.qualifiedName = qualifiedName;
    ImmutableList.Builder<GenericElementParameter> typeParametersBuilder = ImmutableList.builder();
    for (GenericElementParameter.Builder typeParameterBuilder : typeParameterBuilders) {
      typeParametersBuilder.add(typeParameterBuilder.build(this));
    }
    this.typeParameters = typeParametersBuilder.build();
  }

  @Override
  public GenericMirror asType() {
    List<TypeVariable> typeArguments = new ArrayList<>();
    for (GenericElementParameter typeParameter : typeParameters) {
      typeArguments.add(typeParameter.asType());
    }
    return new GenericMirror(new AtomicReference<>(this), typeArguments);
  }

  @Override
  public ElementKind getKind() {
    return ElementKind.CLASS;
  }

  @Override
  public List<? extends AnnotationMirror> getAnnotationMirrors() {
    return ImmutableList.of();
  }

  @Override
  public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
    return null;
  }

  @Override
  public ImmutableSet<Modifier> getModifiers() {
    return ImmutableSet.of();
  }

  @Override
  public <R, P> R accept(ElementVisitor<R, P> v, P p) {
    return v.visitType(this, p);
  }

  @Override
  public List<? extends Element> getEnclosedElements() {
    return ImmutableList.of();
  }

  @Override
  public NestingKind getNestingKind() {
    return NestingKind.TOP_LEVEL;
  }

  @Override
  public Name getQualifiedName() {
    return new NameImpl(qualifiedName.toString());
  }

  @Override
  public Name getSimpleName() {
    return new NameImpl(qualifiedName.getSimpleName());
  }

  @Override
  public TypeMirror getSuperclass() {
    return newTopLevelClass("java.lang.Object");
  }

  @Override
  public List<? extends TypeMirror> getInterfaces() {
    return ImmutableList.of();
  }

  @Override
  public ImmutableList<GenericElementParameter> getTypeParameters() {
    return typeParameters;
  }

  @Override
  public PackageElementImpl getEnclosingElement() {
    return new PackageElementImpl(qualifiedName.getPackage());
  }

}
