package org.inferred.freebuilder;

import com.google.common.collect.BiMap;

@FreeBuilder
public interface BiMapPropertyType {

  BiMap<Integer, String> getNumbers();

  class Builder extends BiMapPropertyType_Builder {}
}
