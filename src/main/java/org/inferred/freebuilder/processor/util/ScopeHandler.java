package org.inferred.freebuilder.processor.util;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

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

  interface Reflection {
    Optional<TypeInfo> find(String typename);
  }
  interface TypeInfo {
    QualifiedName name();
    Visibility visibility();
    Stream<TypeInfo> supertypes();
    Stream<TypeInfo> nestedTypes();
  }

  private static final String UNIVERSALLY_VISIBLE_PACKAGE = "java.lang";

  private final Reflection reflect;

  /** Type ↦ visibility in parent scope */
  private final Map<QualifiedName, Visibility> typeVisibility = new HashMap<>();
  /** Scope ↦ simple name ↦ type */
  private final Map<QualifiedName, SetMultimap<String, QualifiedName>> visibleTypes =
      new HashMap<>();
  /** Qualified name as string ↦ qualified name */
  private final Map<String, QualifiedName> generatedTypes = new HashMap<>();

  ScopeHandler(Reflection reflect) {
    this.reflect = reflect;
  }

  /**
   * Returns whether {@code type} is visible in, or can be imported into, a compilation unit in
   * {@code pkg}.
   */
  ScopeState visibilityIn(String pkg, QualifiedName type) {
    if (isTopLevelType(pkg, type.getSimpleName())) {
      if (type.isTopLevel() && type.getPackage().equals(pkg)) {
        return ScopeState.IN_SCOPE;
      } else {
        return ScopeState.HIDDEN;
      }
    } else if (!pkg.equals(UNIVERSALLY_VISIBLE_PACKAGE)) {
      return visibilityIn(UNIVERSALLY_VISIBLE_PACKAGE, type);
    } else {
      return ScopeState.IMPORTABLE;
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

  void declareGeneratedType(
      Visibility visibility,
      QualifiedName generatedType,
      Set<String> supertypes) {
    generatedTypes.put(generatedType.toString(), generatedType);
    typeVisibility.put(generatedType, visibility);
    if (!generatedType.isTopLevel()) {
      get(visibleTypes, generatedType.enclosingType())
          .put(generatedType.getSimpleName(), generatedType);
    }
    SetMultimap<String, QualifiedName> visibleInScope = get(visibleTypes, generatedType);
    supertypes.stream().flatMap(this::lookup).forEach(supertype -> {
      for (QualifiedName type : typesInScope(supertype).values()) {
        if (maybeVisibleInScope(generatedType, visibilityOf(type), type)) {
          visibleInScope.put(type.getSimpleName(), type);
        }
      }
    });
  }

  private Stream<QualifiedName> lookup(String typename) {
    if (generatedTypes.containsKey(typename)) {
      return Stream.of(generatedTypes.get(typename));
    }
    return reflect.find(typename).map(TypeInfo::name).map(Stream::of).orElse(Stream.of());
  }

  private boolean isTopLevelType(String pkg, String simpleName) {
    String name = pkg + "." + simpleName;
    return generatedTypes.containsKey(name) || reflect.find(name).isPresent();
  }

  private static <K1, K2, V> SetMultimap<K2, V> get(Map<K1, SetMultimap<K2, V>> map, K1 key) {
    SetMultimap<K2, V> result = map.get(key);
    if (result == null) {
      result = HashMultimap.create();
      map.put(key, result);
    }
    return result;
  }

  private SetMultimap<String, QualifiedName> typesInScope(QualifiedName scope) {
    return Optional.ofNullable(visibleTypes.get(scope)).orElseGet(() -> computeTypesInScope(scope));
  }

  private SetMultimap<String, QualifiedName> computeTypesInScope(QualifiedName scope) {
    SetMultimap<String, QualifiedName> visibleInScope = HashMultimap.create();
    visibleTypes.put(scope, visibleInScope);
    reflect.find(scope.toString()).ifPresent(element -> {
      element.supertypes().forEach(supertype -> {
        typesInScope(supertype.name()).values().forEach(type -> {
          if (maybeVisibleInScope(scope, visibilityOf(type), type)) {
            visibleInScope.put(type.getSimpleName(), type);
          }
        });
      });
      element.nestedTypes().forEach(nested -> {
        visibleInScope.put(nested.name().getSimpleName().toString(), nested.name());
      });
    });
    return visibleInScope;
  }

  private static boolean maybeVisibleInScope(
      QualifiedName scope,
      Visibility visibility,
      QualifiedName type) {
    switch (visibility) {
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
    throw new IllegalStateException("Unknown visibility " + visibility);
  }

  private Visibility visibilityOf(QualifiedName type) {
    return typeVisibility.computeIfAbsent(type, t -> reflect.find(t.toString()).get().visibility());
  }
}
