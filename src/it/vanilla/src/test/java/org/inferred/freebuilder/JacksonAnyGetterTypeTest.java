package org.inferred.freebuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class JacksonAnyGetterTypeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testDeserializeJsonWithAnyGetter() throws IOException {
        JacksonAnyGetterType parsed = objectMapper.readValue(
                "{\"simple_property\": \"simpleValue\", \"other_property\": 3}",
                JacksonAnyGetterType.class);

        assertEquals("simpleValue", parsed.getSimpleProperty());
        assertNotNull(parsed.getUnknownProperties());
        assertEquals(1, parsed.getUnknownProperties().size());
        assertEquals(new IntNode(3), parsed.getUnknownProperties().get("other_property"));
    }

    @Test
    public void testSerializeJsonWithAnyGetter() throws JsonProcessingException {
        JacksonAnyGetterType getterType = new JacksonAnyGetterType.Builder()
                .setSimpleProperty("checkValue")
                .putUnknownProperties("propertyOne", new TextNode("abc"))
                .putUnknownProperties("propertyTwo", new IntNode(2)).build();

        String json = objectMapper.writeValueAsString(getterType);
        assertTrue("should contain simple_property",
                json.contains("\"simple_property\":\"checkValue\""));
        assertTrue("should contain propertyOne", json.contains("\"propertyOne\":\"abc\""));
        assertTrue("should contain propertyTwo", json.contains("\"propertyTwo\":2"));
    }

}
