package org.inferred.freebuilder;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.Map;

@FreeBuilder
@JsonDeserialize(builder = JacksonAnyGetterType.Builder.class)
public interface JacksonAnyGetterType {
  @JsonProperty("simple_property")
  String getSimpleProperty();

  @JsonAnyGetter
  Map<String, JsonNode> getUnknownProperties();

  class Builder extends JacksonAnyGetterType_Builder {}
}
