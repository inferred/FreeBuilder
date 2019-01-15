package org.inferred.freebuilder.processor.util;

import static com.google.common.collect.Sets.newHashSet;

import com.google.common.base.Optional;

import org.inferred.freebuilder.processor.util.ScopeHandler.ScopeState;
import org.inferred.freebuilder.processor.util.ScopeHandler.Visibility;
import org.inferred.freebuilder.processor.util.TypeShortener.AbstractTypeShortener;

import java.io.IOException;
import java.util.Set;

import javax.lang.model.element.TypeElement;

class ScopeAwareTypeShortener extends AbstractTypeShortener {

  private final TypeShortener delegate;
  private final String pkg;
  private final QualifiedName scope;
  private final ScopeHandler handler;

  ScopeAwareTypeShortener(TypeShortener delegate, ScopeHandler handler, String pkg) {
    this.delegate = delegate;
    this.pkg = pkg;
    this.scope = null;
    this.handler = handler;
  }

  ScopeAwareTypeShortener(TypeShortener delegate, QualifiedName scope, ScopeHandler handler) {
    this.delegate = delegate;
    this.pkg = scope.getPackage();
    this.scope = scope;
    this.handler = handler;
  }

  @Override
  public void appendShortened(Appendable a, QualifiedName type) throws IOException {
    if (scope == null) {
      delegate.appendShortened(a, type);
      return;
    }
    ScopeState scopeState = handler.visibilityIn(scope, type);
    if (scopeState == ScopeState.IMPORTABLE) {
      delegate.appendShortened(a, type);
    } else if (type.isTopLevel() && scopeState == ScopeState.IN_SCOPE) {
      delegate.appendShortened(a, type);
    } else {
      if (type.isTopLevel()) {
        a.append(type.getPackage());
      } else {
        appendShortened(a, type.getEnclosingType());
      }
      a.append('.').append(type.getSimpleName());
    }
  }

  @Override
  public Optional<QualifiedName> lookup(String shortenedType) {
    if (scope == null) {
      return delegate.lookup(shortenedType);
    }
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
    result = delegate.lookup(shortenedType).orNull();
    if (result != null) {
      return Optional.of(result);
    }
    return handler.lookup(shortenedType);
  }

  @Override
  public void appendShortened(Appendable a, TypeElement type) throws IOException {
    appendShortened(a, QualifiedName.of(type));
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
    return new ScopeAwareTypeShortener(delegate, newScope, handler);
  }
}
