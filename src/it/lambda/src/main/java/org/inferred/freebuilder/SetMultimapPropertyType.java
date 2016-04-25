package org.inferred.freebuilder;

import com.google.common.collect.SetMultimap;

@FreeBuilder
public interface SetMultimapPropertyType {
  SetMultimap<Integer, String> getNumbers();

  class Builder extends SetMultimapPropertyType_Builder {}
}
