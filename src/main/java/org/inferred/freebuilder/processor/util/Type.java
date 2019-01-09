/*
 * Copyright 2015 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.inferred.freebuilder.processor.util;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;

import static org.inferred.freebuilder.processor.util.ModelUtils.asElement;
import static org.inferred.freebuilder.processor.util.ModelUtils.maybeAsTypeElement;
import static org.inferred.freebuilder.processor.util.feature.SourceLevel.diamondOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.nCopies;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;

import org.inferred.freebuilder.processor.util.feature.SourceLevel;
import org.inferred.freebuilder.processor.util.feature.StaticFeatureSet;

import java.util.List;

import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

public abstract class Type extends Excerpt {

  public static Type from(TypeElement typeElement) {
    return new TypeImpl(QualifiedName.of(typeElement), typeElement.getTypeParameters());
  }

  public static Type from(DeclaredType declaredType) {
    return new TypeImpl(
        QualifiedName.of(asElement(declaredType)),
        declaredType.getTypeArguments());
  }

  public static Type from(Class<?> cls) {
    return new TypeImpl(QualifiedName.of(cls), asList(cls.getTypeParameters()));
  }

  /** Returns the qualified name of the type class. */
  public abstract QualifiedName getQualifiedName();

  protected abstract List<?> getTypeParameters();

  /** Returns the simple name of the type class. */
  public String getSimpleName() {
    return getQualifiedName().getSimpleName();
  }

  /** Returns true if the type class is generic. */
  public boolean isParameterized() {
    return !getTypeParameters().isEmpty();
  }

  /**
   * Returns a source excerpt suitable for constructing an instance of this type, including "new"
   * keyword but excluding brackets.
   *
   * <p>At {@link SourceLevel#JAVA_7} and above, we can use the diamond operator. Otherwise, we
   * write out the type parameters in full.
   */
  public Excerpt constructor() {
    return Excerpts.add("new %s%s", getQualifiedName(), typeParametersOrDiamondOperator());
  }

  /**
   * Returns a source excerpt suitable for declaring this type.
   *
   * <p>e.g. {@code MyType<N extends Number, C extends Consumer<N>>}
   */
  public Excerpt declaration() {
    return Excerpts.add("%s%s", getSimpleName(), declarationParameters());
  }

  /**
   * Returns a source excerpt of the type parameters of this type, including bounds and angle
   * brackets.
   *
   * <p>e.g. {@code <N extends Number, C extends Consumer<N>>}
   */
  public Excerpt declarationParameters() {
    return new DeclarationParameters(getTypeParameters());
  }

  /**
   * Returns a source excerpt of a JavaDoc link to this type.
   */
  public Excerpt javadocLink() {
    return Excerpts.add("{@link %s}", getQualifiedName());
  }

  /**
   * Returns a source excerpt of a JavaDoc link to a no-args method on this type.
   */
  public Excerpt javadocNoArgMethodLink(final String memberName) {
    return Excerpts.add("{@link %s#%s()}", getQualifiedName(), memberName);
  }

  /**
   * Returns a source excerpt of the type parameters of this type, including angle brackets.
   * Always an empty string if the type class is not generic.
   *
   * <p>e.g. {@code <N, C>}
   */
  public Excerpt typeParameters() {
    if (getTypeParameters().isEmpty()) {
      return Excerpts.empty();
    } else {
      return Excerpts.add("<%s>", Excerpts.join(", ", getTypeParameters()));
    }
  }

  /**
   * Returns a source excerpt equivalent to the diamond operator for this type.
   *
   * <p>Always an empty string if the type class is not generic. Matches {@link #typeParameters()}
   * for {@link SourceLevel#JAVA_6}.
   */
  public Excerpt typeParametersOrDiamondOperator() {
    return isParameterized()
        ? diamondOperator(Excerpts.join(", ", getTypeParameters()))
        : Excerpts.empty();
  }

  /**
   * Returns a new type of the same class, parameterized with wildcards ("?").
   *
   * <p>If the type class is not generic, the returned object will be equal to this one.
   */
  public Type withWildcards() {
    if (getTypeParameters().isEmpty()) {
      return this;
    }
    return new TypeImpl(getQualifiedName(), nCopies(getTypeParameters().size(), "?"));
  }

  @Override
  public String toString() {
    // Only used when debugging, so an empty feature set is fine.
    return SourceStringBuilder.compilable(new StaticFeatureSet()).add(this).toString();
  }

  @Override
  public void addTo(SourceBuilder source) {
    source.add("%s%s", getQualifiedName(), typeParameters());
  }

  private final class DeclarationParameters extends Excerpt {

    private final List<TypeParameterElement> typeParameters;

    DeclarationParameters(List<?> typeParameters) {
      this.typeParameters =
          FluentIterable.from(typeParameters).filter(TypeParameterElement.class).toList();
      checkState(this.typeParameters.size() == typeParameters.size(),
          "Not all parameters are TypeParameterElements");
    }

    @Override public void addTo(SourceBuilder source) {
      if (!typeParameters.isEmpty()) {
        String prefix = "<";
        for (TypeParameterElement typeParameter : typeParameters) {
          source.add("%s%s", prefix, typeParameter.getSimpleName());
          if (!extendsObject(typeParameter)) {
            String separator = " extends ";
            for (TypeMirror bound : typeParameter.getBounds()) {
              source.add("%s%s", separator, bound);
              separator = " & ";
            }
          }
          prefix = ", ";
        }
        source.add(">");
      }
    }

    @Override
    protected void addFields(FieldReceiver fields) {
      fields.add("typeParameters", typeParameters);
    }
  }

  private static boolean extendsObject(TypeParameterElement element) {
    if (element.getBounds().size() != 1) {
      return false;
    }
    TypeElement bound = maybeAsTypeElement(getOnlyElement(element.getBounds())).orNull();
    if (bound == null) {
      return false;
    }
    return bound.getQualifiedName().contentEquals(Object.class.getName());
  }

  static class TypeImpl extends Type {
    private final QualifiedName qualifiedName;
    private final List<?> typeParameters;

    TypeImpl(QualifiedName qualifiedName, List<?> typeParameters) {
      this.qualifiedName = checkNotNull(qualifiedName);
      this.typeParameters = ImmutableList.copyOf(typeParameters);
    }

    @Override
    public QualifiedName getQualifiedName() {
      return qualifiedName;
    }

    @Override
    protected List<?> getTypeParameters() {
      return typeParameters;
    }

    @Override
    protected void addFields(FieldReceiver fields) {
      fields.add("qualifiedName", qualifiedName);
      fields.add("typeParameters", typeParameters);
    }
  }
}
