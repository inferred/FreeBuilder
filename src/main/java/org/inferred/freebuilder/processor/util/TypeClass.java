package org.inferred.freebuilder.processor.util;

import static com.google.common.collect.Iterables.getOnlyElement;

import static org.inferred.freebuilder.processor.util.ModelUtils.maybeAsTypeElement;

import com.google.common.collect.ImmutableList;

import org.inferred.freebuilder.processor.util.feature.StaticFeatureSet;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/**
 * Representation of a class or interface element.
 *
 * <p>When used as an excerpt or {@link Type}, it acts as a <i>prototypical</i> type of the class.
 * This is the element's invocation on the type variables corresponding to its own formal type
 * parameters. For example, the {@code TypeClass} of {@link EnumSet} would appear in code as
 * {@code EnumSet<E>}. This is probably the most common use of a type class in code generation.
 *
 * <p>The {@link #declaration()} and {@link #declarationParameters()} methods, on the other hand,
 * return excerpts reflecting the type element. For example, the declaration of {@link EnumSet}
 * would be {@code EnumSet<E extends Enum<E>>}.
 *
 * <p>A hybrid of a {@link TypeElement} and its prototypical {@link DeclaredType}.
 *
 * @see Element#asType()
 */
public class TypeClass extends Type {

  public static TypeClass from(TypeElement typeElement) {
    return new TypeClass(QualifiedName.of(typeElement), typeElement.getTypeParameters());
  }

  private final QualifiedName qualifiedName;
  private final List<TypeParameterElement> typeParameters;

  TypeClass(
      QualifiedName qualifiedName,
      Collection<? extends TypeParameterElement> typeParameters) {
    this.qualifiedName = qualifiedName;
    this.typeParameters = ImmutableList.copyOf(typeParameters);
  }

  @Override
  public QualifiedName getQualifiedName() {
    return qualifiedName;
  }

  /**
   * Returns a source excerpt suitable for declaring this type element.
   *
   * <p>e.g. {@code MyType<N extends Number, C extends Consumer<N>>}
   */
  public Excerpt declaration() {
    return Excerpts.add("%s%s", getSimpleName(), declarationParameters());
  }

  /**
   * Returns a source excerpt of the type parameters of this type element, including bounds and
   * angle brackets.
   *
   * <p>e.g. {@code <N extends Number, C extends Consumer<N>>}
   */
  public Excerpt declarationParameters() {
    return new DeclarationParameters(getTypeParameters());
  }

  @Override
  protected List<TypeParameterElement> getTypeParameters() {
    return typeParameters;
  }

  @Override
  protected void addFields(FieldReceiver fields) {
    fields.add("qualifiedName", qualifiedName);
    fields.add("typeParameters", typeParameters);
  }

  @Override
  public String toString() {
    // Only used when debugging, so an empty feature set is fine.
    return SourceStringBuilder.compilable(new StaticFeatureSet()).add(declaration()).toString();
  }

  private static class DeclarationParameters extends Excerpt {

    private final List<TypeParameterElement> typeParameters;

    DeclarationParameters(List<TypeParameterElement> typeParameters) {
      this.typeParameters = typeParameters;
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
  }
}
