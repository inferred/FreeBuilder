package org.inferred.freebuilder;

import java.util.List;

@FreeBuilder
public interface ListPropertyType {
  List<String> getNames();

  class Builder extends ListPropertyType_Builder {}
}
