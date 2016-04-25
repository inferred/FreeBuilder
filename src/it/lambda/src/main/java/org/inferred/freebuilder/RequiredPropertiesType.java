package org.inferred.freebuilder;

@FreeBuilder
public interface RequiredPropertiesType {
  String getFirstName();
  String getSurname();

  class Builder extends RequiredPropertiesType_Builder {}
}
