package org.inferred.freebuilder.processor.source;

import java.util.Optional;

interface TypeUsage {

  int start();
  int end();
  QualifiedName type();
  Optional<QualifiedName> scope();

  class Builder extends TypeUsage_Builder { }
}
