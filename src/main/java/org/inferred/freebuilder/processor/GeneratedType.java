package org.inferred.freebuilder.processor;

import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.QualifiedName;
import org.inferred.freebuilder.processor.util.ValueType;

abstract class GeneratedType extends ValueType implements Excerpt {
  public abstract QualifiedName getName();
}
