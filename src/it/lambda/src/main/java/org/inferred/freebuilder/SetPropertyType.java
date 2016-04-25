package org.inferred.freebuilder;

import java.util.Set;

@FreeBuilder
public interface SetPropertyType {
  Set<String> getNames();

  class Builder extends SetPropertyType_Builder {}
}
