package org.inferred.freebuilder;

import java.util.Map;

@FreeBuilder
public interface MapPropertyType {
  Map<Integer, String> getNumbers();

  class Builder extends MapPropertyType_Builder {}
}
