package org.inferred.freebuilder.processor.util;

import java.util.Optional;

interface TypeUsage {

  int start();
  int end();
  QualifiedName type();
  Optional<QualifiedName> scope();

  class Builder extends TypeUsage_Builder { }
}
