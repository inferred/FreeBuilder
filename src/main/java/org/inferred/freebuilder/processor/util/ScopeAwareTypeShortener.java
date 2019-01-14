package org.inferred.freebuilder.processor.util;

import org.inferred.freebuilder.processor.util.ScopeHandler.ScopeState;
import org.inferred.freebuilder.processor.util.ScopeHandler.Visibility;
import org.inferred.freebuilder.processor.util.TypeShortener.AbstractTypeShortener;

import java.io.IOException;
import java.util.Set;

import javax.lang.model.element.TypeElement;

class ScopeAwareTypeShortener extends AbstractTypeShortener {

  private final TypeShortener delegate;
  private final QualifiedName scope;
  private final ScopeHandler handler;

  ScopeAwareTypeShortener(TypeShortener delegate, ScopeHandler handler) {
    this(delegate, null, handler);
  }

  ScopeAwareTypeShortener(TypeShortener delegate, QualifiedName scope, ScopeHandler handler) {
    this.delegate = delegate;
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
  public void appendShortened(Appendable a, TypeElement type) throws IOException {
    appendShortened(a, QualifiedName.of(type));
  }

  @Override
  public TypeShortener inScope(QualifiedName type, Set<QualifiedName> supertypes) {
    handler.declareGeneratedType(Visibility.UNKNOWN, type, supertypes);
    return new ScopeAwareTypeShortener(delegate, type, handler);
  }
}
