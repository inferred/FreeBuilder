package org.inferred.freebuilder.processor.util;

import static org.inferred.freebuilder.processor.util.ModelUtils.maybeAsTypeElement;

import org.inferred.freebuilder.processor.util.ScopeHandler.Reflection;
import org.inferred.freebuilder.processor.util.ScopeHandler.TypeInfo;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;

/** Encapsulates the parts of {@link Elements} used by {@link ScopeHandler}. */
class CompilerReflection implements Reflection {

  private static class ElementsTypeInfo implements ScopeHandler.TypeInfo {
    private final Elements elements;
    private final TypeElement element;
    private final QualifiedName name;

    ElementsTypeInfo(Elements elements, TypeElement element) {
      this.elements = elements;
      this.element = element;
      this.name = QualifiedName.of(element);
    }

    @Override
    public QualifiedName name() {
      return name;
    }

    @Override
    public ScopeHandler.Visibility visibility() {
      Set<Modifier> modifiers = element.getModifiers();
      if (modifiers.contains(Modifier.PUBLIC)) {
        return ScopeHandler.Visibility.PUBLIC;
      } else if (modifiers.contains(Modifier.PROTECTED)) {
        return ScopeHandler.Visibility.PROTECTED;
      } else if (modifiers.contains(Modifier.PRIVATE)) {
        return ScopeHandler.Visibility.PRIVATE;
      } else  {
        return ScopeHandler.Visibility.PACKAGE;
      }
    }

    @Override
    public Stream<ScopeHandler.TypeInfo> supertypes() {
      return Stream.concat(
          create(element.getSuperclass()),
          element.getInterfaces().stream().flatMap(this::create));
    }

    @Override
    public Stream<ScopeHandler.TypeInfo> nestedTypes() {
      return ElementFilter.typesIn(element.getEnclosedElements())
          .stream()
          .map(element -> new ElementsTypeInfo(elements, element));
    }

    private Stream<ScopeHandler.TypeInfo> create(TypeMirror mirror) {
      TypeElement element = maybeAsTypeElement(mirror).orElse(null);
      if (element == null) {
        return Stream.of();
      }
      return Stream.of(new ElementsTypeInfo(elements, element));
    }
  }

  private final Elements elements;

  CompilerReflection(Elements elements) {
    this.elements = elements;
  }

  @Override
  public Optional<TypeInfo> find(String name) {
    TypeElement element = elements.getTypeElement(name);
    if (element == null) {
      return Optional.empty();
    }
    return Optional.of(new CompilerReflection.ElementsTypeInfo(elements, element));
  }
}