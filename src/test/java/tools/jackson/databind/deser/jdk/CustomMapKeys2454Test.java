package tools.jackson.databind.deser.jdk;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.*;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static tools.jackson.databind.testutil.DatabindTestUtil.a2q;
import static tools.jackson.databind.testutil.DatabindTestUtil.newJsonMapper;

public class CustomMapKeys2454Test
{
    @JsonDeserialize(keyUsing = Key2454Deserializer.class)
    @JsonSerialize(keyUsing = Key2454Serializer.class)
    static class Key2454 {
        String id;

        public Key2454(String id, boolean bogus) {
            this.id = id;
        }
    }

    static class Key2454Deserializer extends KeyDeserializer {
        @Override
        public Object deserializeKey(String key, DeserializationContext ctxt) {
            return new Key2454(key, false);
        }
    }

    static class Key2454Serializer extends ValueSerializer<Key2454> {
        @Override
        public void serialize(Key2454 value, JsonGenerator gen,
                SerializationContext serializers) {
            gen.writeName("id="+value.id);
        }
    }

    private final ObjectMapper MAPPER = newJsonMapper();

    @Test
    public void testCustomSerializer() throws Exception
    {
        assertEquals(a2q("{'id=a':'b'}"),
                MAPPER.writeValueAsString(Collections.singletonMap(new Key2454("a", true), "b")));
    }

    @Test
    public void testCustomDeserializer() throws Exception
    {
        Map<Key2454, String> result = MAPPER.readValue(a2q("{'a':'b'}"),
                new TypeReference<Map<Key2454, String>>() { });
        assertEquals(1, result.size());
        Key2454 key = result.keySet().iterator().next();
        assertEquals("a", key.id);
    }
}
