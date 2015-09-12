package org.inferred.freebuilder.processor.util;

/**
 * An object representing a source code excerpt, e.g. a type.
 */
public interface Excerpt {
  void addTo(SourceBuilder source);
}
