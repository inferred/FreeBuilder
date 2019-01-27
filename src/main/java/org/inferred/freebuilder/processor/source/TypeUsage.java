package org.inferred.freebuilder.processor.source;

import org.inferred.freebuilder.FreeBuilder;

import java.util.Optional;

@FreeBuilder
interface TypeUsage {

  int start();
  int end();
  QualifiedName type();
  Optional<QualifiedName> scope();

  class Builder extends TypeUsage_Builder { }
}
