package org.inferred.freebuilder;

@FreeBuilder
public interface DefaultedPropertiesType {
  String getFirstName();

  String getSurname();

  class Builder extends DefaultedPropertiesType_Builder {
    public Builder() {
      setFirstName("Joe");
      setSurname("Bloggs");
    }
  }
}
