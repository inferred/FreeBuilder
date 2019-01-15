package org.inferred.freebuilder.processor.util;

import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.collect.Sets.newHashSet;

import static org.inferred.freebuilder.processor.util.ModelUtils.asElement;

import com.google.common.base.Optional;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.SetMultimap;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor6;

/**
 * Handles the byzantine rules of Java scoping.
 */
class ScopeHandler {

  enum ScopeState {
    /** Type is already visible due to scoping rules. */
    IN_SCOPE,
    /** Type is hidden by another type of the same name. */
    HIDDEN,
    /** Type can safely be imported. */
    IMPORTABLE
  }
  enum Visibility { PUBLIC, PROTECTED, PACKAGE, PRIVATE, UNKNOWN }

  private final Elements elements;

  /** Type ↦ visibility in parent scope */
  private final Map<QualifiedName, Visibility> typeVisibility = newHashMap();
  /** Package ↦ simple names */
  private final Map<String, Set<String>> topLevelTypes = newHashMap();
  /** Scope ↦ simple name ↦ type */
  private final Map<QualifiedName, SetMultimap<String, QualifiedName>> visibleTypes = newHashMap();
  /** Qualified name as string ↦ qualified name */
  private final Map<String, QualifiedName> generatedTypes = newHashMap();

  ScopeHandler(Elements elements) {
    this.elements = elements;
  }

  /**
   * Returns whether {@code type} is visible in, or can be imported into, a compilation unit in
   * {@code pkg}.
   */
  ScopeState visibilityIn(String pkg, QualifiedName type) {
    if (!typesInPackage(pkg).contains(type.getSimpleName())) {
      return ScopeState.IMPORTABLE;
    } else if (type.isTopLevel() && type.getPackage().equals(pkg)) {
      return ScopeState.IN_SCOPE;
    } else {
      return ScopeState.HIDDEN;
    }
  }

  /**
   * Returns whether {@code type} is visible in, or can be imported into, the body of {@code type}.
   */
  ScopeState visibilityIn(QualifiedName scope, QualifiedName type) {
    Set<QualifiedName> possibleConflicts = typesInScope(scope).get(type.getSimpleName());
    if (possibleConflicts.equals(ImmutableSet.of(type))) {
      return ScopeState.IN_SCOPE;
    } else if (!possibleConflicts.isEmpty()) {
      return ScopeState.HIDDEN;
    } else if (!scope.isTopLevel()) {
      return visibilityIn(scope.enclosingType(), type);
    } else {
      return visibilityIn(scope.getPackage(), type);
    }
  }

  Optional<QualifiedName> typeInScope(String pkg, String simpleName) {
    if (typesInPackage(pkg).contains(simpleName)) {
      return Optional.of(QualifiedName.of(pkg, simpleName));
    } else {
      return Optional.absent();
    }
  }

  Optional<QualifiedName> typeInScope(QualifiedName scope, String simpleName) {
    Set<QualifiedName> possibleTypes = typesInScope(scope).get(simpleName);
    switch (possibleTypes.size()) {
      case 0:
        if (scope.isTopLevel()) {
          return typeInScope(scope.getPackage(), simpleName);
        } else {
          return typeInScope(scope.getEnclosingType(), simpleName);
        }

      case 1:
        return Optional.of(Iterables.getOnlyElement(possibleTypes));

      default:
        return Optional.absent();
    }
  }

  void predeclareGeneratedType(QualifiedName generatedType) {
    declareGeneratedType(Visibility.UNKNOWN, generatedType, ImmutableSet.<QualifiedName>of());
  }

  void declareGeneratedType(
      Visibility visibility,
      QualifiedName generatedType,
      Set<QualifiedName> supertypes) {
    generatedTypes.put(generatedType.toString(), generatedType);
    typeVisibility.put(generatedType, visibility);
    if (generatedType.isTopLevel()) {
      typesInPackage(generatedType.getPackage()).add(generatedType.getSimpleName());
    } else {
      get(visibleTypes, generatedType.enclosingType())
          .put(generatedType.getSimpleName(), generatedType);
    }
    SetMultimap<String, QualifiedName> visibleInScope = get(visibleTypes, generatedType);
    for (QualifiedName supertype : supertypes) {
      for (QualifiedName type : typesInScope(supertype).values()) {
        if (maybeVisibleInScope(generatedType, type)) {
          visibleInScope.put(type.getSimpleName(), type);
        }
      }
    }
  }

  Optional<QualifiedName> lookup(String typename) {
    if (generatedTypes.containsKey(typename)) {
      return Optional.of(generatedTypes.get(typename));
    }
    TypeElement scopeElement = elements.getTypeElement(typename);
    if (scopeElement != null) {
      return Optional.of(QualifiedName.of(scopeElement));
    }
    return Optional.absent();
  }

  private static <K1, K2, V> SetMultimap<K2, V> get(Map<K1, SetMultimap<K2, V>> map, K1 key) {
    SetMultimap<K2, V> result = map.get(key);
    if (result == null) {
      result = HashMultimap.create();
      map.put(key, result);
    }
    return result;
  }

  private Set<String> typesInPackage(String pkg) {
    Set<String> result = topLevelTypes.get(pkg);
    if (result == null) {
      result = newHashSet();
      PackageElement packageElement = elements.getPackageElement(pkg);
      if (packageElement != null) {
        for (TypeElement type : ElementFilter.typesIn(packageElement.getEnclosedElements())) {
          result.add(type.getSimpleName().toString());
        }
      }
      topLevelTypes.put(pkg, result);
    }
    return result;
  }

  private SetMultimap<String, QualifiedName> typesInScope(QualifiedName scope) {
    SetMultimap<String, QualifiedName> result = visibleTypes.get(scope);
    if (result != null) {
      return result;
    }
    TypeElement scopeElement = elements.getTypeElement(scope.toString());
    return cacheTypesInScope(scope, scopeElement);
  }

  private SetMultimap<String, QualifiedName> cacheTypesInScope(
      QualifiedName scope,
      TypeElement element) {
    SetMultimap<String, QualifiedName> visibleInScope = HashMultimap.create();
    if (element != null) {
      for (QualifiedName type : TYPES_IN_SCOPE.visit(element.getSuperclass(), this)) {
        if (maybeVisibleInScope(scope, type)) {
          visibleInScope.put(type.getSimpleName(), type);
        }
      }
      for (TypeMirror iface : element.getInterfaces()) {
        for (QualifiedName type : TYPES_IN_SCOPE.visit(iface, this)) {
          if (maybeVisibleInScope(scope, type)) {  // In case interfaces ever get private members
            visibleInScope.put(type.getSimpleName(), type);
          }
        }
      }
      for (TypeElement nested : ElementFilter.typesIn(element.getEnclosedElements())) {
        visibleInScope.put(nested.getSimpleName().toString(), QualifiedName.of(nested));
      }
    }
    visibleTypes.put(scope, visibleInScope);
    return visibleInScope;
  }

  private boolean maybeVisibleInScope(QualifiedName scope, QualifiedName type) {
    switch (visibilityOf(type)) {
      case PUBLIC:
      case PROTECTED:
        // type is either nested in scope or a supertype of scope.
        // Either way, it's visible.
        return true;
      case PACKAGE:
        return scope.getPackage().equals(type.getPackage());
      case PRIVATE:
        // Private types are only visible in their enclosing type.
        // Inheriting from the enclosing type is not sufficient.
        return type.enclosingType().equals(scope);
      case UNKNOWN:
        return true;
    }
    throw new IllegalStateException("Unknown visibility " + visibilityOf(type));
  }

  private Visibility visibilityOf(QualifiedName type) {
    Visibility visibility = typeVisibility.get(type);
    if (visibility == null) {
      TypeElement element = elements.getTypeElement(type.toString());
      Set<Modifier> modifiers = element.getModifiers();
      if (modifiers.contains(Modifier.PUBLIC)) {
        visibility = Visibility.PUBLIC;
      } else if (modifiers.contains(Modifier.PROTECTED)) {
        visibility = Visibility.PROTECTED;
      } else if (modifiers.contains(Modifier.PRIVATE)) {
        visibility = Visibility.PRIVATE;
      } else  {
        visibility = Visibility.PACKAGE;
      }
      typeVisibility.put(type, visibility);
    }
    return visibility;
  }

  private static final TypeVisitor<Collection<QualifiedName>, ScopeHandler>
      TYPES_IN_SCOPE =
          new SimpleTypeVisitor6<Collection<QualifiedName>, ScopeHandler>() {
            @Override
            public Collection<QualifiedName> visitDeclared(
                DeclaredType type,
                ScopeHandler scopeHandler) {
              QualifiedName typename = QualifiedName.of(asElement(type));
              SetMultimap<String, QualifiedName> visibleInScope =
                  scopeHandler.visibleTypes.get(typename);
              if (visibleInScope != null) {
                return visibleInScope.values();
              }
              return scopeHandler.cacheTypesInScope(typename, asElement(type)).values();
            }

            @Override
            protected Collection<QualifiedName> defaultAction(TypeMirror e, ScopeHandler p) {
              return ImmutableSet.of();
            }
          };
}
