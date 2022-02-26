package org.inferred.freebuilder.processor.source;

import java.util.Optional;
import org.inferred.freebuilder.FreeBuilder;

@FreeBuilder
interface TypeUsage {

  int start();

  int end();

  QualifiedName type();

  Optional<QualifiedName> scope();

  class Builder extends TypeUsage_Builder {}
}
