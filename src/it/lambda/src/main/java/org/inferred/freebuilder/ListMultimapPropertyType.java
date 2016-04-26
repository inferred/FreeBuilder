package org.inferred.freebuilder;

import com.google.common.collect.ListMultimap;

@FreeBuilder
public interface ListMultimapPropertyType {
  ListMultimap<Integer, String> getNumbers();

  class Builder extends ListMultimapPropertyType_Builder {}
}
