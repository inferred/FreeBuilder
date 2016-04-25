package org.inferred.freebuilder;

import com.google.common.collect.Multiset;

@FreeBuilder
public interface MultisetPropertyType {
  Multiset<String> getNames();

  class Builder extends MultisetPropertyType_Builder {}
}
