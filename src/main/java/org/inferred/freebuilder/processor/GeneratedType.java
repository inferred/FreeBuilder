package org.inferred.freebuilder.processor;

import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.QualifiedName;

import java.util.Set;

abstract class GeneratedType extends Excerpt {
  public abstract QualifiedName getName();
  public abstract Set<QualifiedName> getVisibleNestedTypes();
}
