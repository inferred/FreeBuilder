package org.inferred.freebuilder.processor.util;

import static com.google.common.collect.Sets.newHashSet;

import com.google.common.base.Optional;

import org.inferred.freebuilder.processor.util.ScopeHandler.ScopeState;
import org.inferred.freebuilder.processor.util.ScopeHandler.Visibility;

import java.io.IOException;
import java.util.Set;

class ScopeAwareTypeShortener implements TypeShortener {

  private final ImportManager importManager;
  private final String pkg;
  private final QualifiedName scope;
  private final ScopeHandler handler;

  ScopeAwareTypeShortener(ImportManager importManager, ScopeHandler handler, String pkg) {
    this.importManager = importManager;
    this.pkg = pkg;
    this.scope = null;
    this.handler = handler;
  }

  ScopeAwareTypeShortener(ImportManager importManager, QualifiedName scope, ScopeHandler handler) {
    this.importManager = importManager;
    this.pkg = scope.getPackage();
    this.scope = scope;
    this.handler = handler;
  }

  @Override
  public void appendShortened(Appendable a, QualifiedName type) throws IOException {
    switch (visibilityInScope(type)) {
      case IN_SCOPE:
        break;

      case IMPORTABLE:
        if (!type.isTopLevel()) {
          appendShortened(a, type.getEnclosingType());
          a.append('.');
        } else if (!importManager.add(type)) {
          a.append(type.getPackage()).append(".");
        }
        break;

      case HIDDEN:
        if (type.isTopLevel()) {
          a.append(type.getPackage());
        } else {
          appendShortened(a, type.getEnclosingType());
        }
        a.append('.');
        break;
    }
    a.append(type.getSimpleName());
  }

  public Optional<QualifiedName> lookup(String shortenedType) {
    String[] simpleNames = shortenedType.split("\\.");
    QualifiedName result;
    if (scope != null) {
      result = handler.typeInScope(scope, simpleNames[0]).orNull();
    } else {
      result = handler.typeInScope(pkg, simpleNames[0]).orNull();
    }
    if (result != null) {
      for (int i = 1; i < simpleNames.length; i++) {
        result = result.nestedType(simpleNames[i]);
      }
      return Optional.of(result);
    }
    result = importManager.lookup(shortenedType).orNull();
    if (result != null) {
      return Optional.of(result);
    }
    return handler.lookup(shortenedType);
  }

  public ScopeAwareTypeShortener inScope(String simpleName, Set<String> supertypes) {
    QualifiedName newScope = (scope == null)
        ? QualifiedName.of(pkg, simpleName)
        : scope.nestedType(simpleName);
    Set<QualifiedName> qualifiedSupertypes = newHashSet();
    for (String supertype : supertypes) {
      QualifiedName qualifiedSupertype = lookup(supertype).orNull();
      if (qualifiedSupertype != null) {
        qualifiedSupertypes.add(qualifiedSupertype);
      }
    }
    handler.declareGeneratedType(Visibility.UNKNOWN, newScope, qualifiedSupertypes);
    return new ScopeAwareTypeShortener(importManager, newScope, handler);
  }

  private ScopeState visibilityInScope(QualifiedName type) {
    if (scope != null) {
      return handler.visibilityIn(scope, type);
    } else {
      return handler.visibilityIn(pkg, type);
    }
  }
}
