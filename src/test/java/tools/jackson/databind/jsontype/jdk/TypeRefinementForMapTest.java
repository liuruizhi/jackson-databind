package tools.jackson.databind.jsontype.jdk;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.*;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.*;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.StdSerializer;
import tools.jackson.databind.testutil.DatabindTestUtil;
import tools.jackson.databind.testutil.NoCheckSubTypeValidator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TypeRefinementForMapTest extends DatabindTestUtil
{
    interface HasUniqueId<K> {
        K getId();
    }

    static class Item implements HasUniqueId<String>
    {
        public String id;
        public String property;

        @Override
        public String getId() { return id; }
    }

    static class Data
    {
        public String id;

        @JsonDeserialize(as = MyHashMap.class)
        public Map<String, Item> items;

        // Would work with straight arguments:
//        public MyHashMap<String, Item> items;
    }

    @SuppressWarnings("serial")
    static class MyHashMap<K, V extends HasUniqueId<K>>
        extends LinkedHashMap<K, V>
    {
        @JsonCreator(mode=JsonCreator.Mode.DELEGATING)
        public MyHashMap(V[] values) {
            for (int i = 0; i < values.length; i++) {
                V v = values[i];
                put(v.getId(), v);
            }
        }
    }

    // for [databind#1384]
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static final class TestClass {
        @JsonProperty("mapProperty")
        @JsonSerialize(keyUsing = CompoundKeySerializer.class)
        @JsonDeserialize(keyUsing = CompoundKeyDeserializer.class)
        final Map<CompoundKey, String> mapProperty;

        @JsonCreator
        private TestClass(@JsonProperty("mapProperty") Map<CompoundKey, String> mapProperty) {
            this.mapProperty = mapProperty;
        }
    }

    static final class CompoundKey {
        private String part0;
        private String part1;

        public CompoundKey(String part0, String part1) {
            this.part0 = part0;
            this.part1 = part1;
        }

        public String getPart0() { return part0; }
        public String getPart1() { return part1; }
    }

    static class CompoundKeyDeserializer extends KeyDeserializer {
        @Override
        public Object deserializeKey(String s, DeserializationContext deserializationContext) {
            String[] parts = s.split("\\|");
            return new CompoundKey(parts[0], parts[1]);
        }
    }

    static class CompoundKeySerializer extends StdSerializer<CompoundKey> {
        public CompoundKeySerializer() { super(CompoundKey.class); }
        @Override
        public void serialize(CompoundKey compoundKey, JsonGenerator g, SerializationContext ctxt) {
            g.writeName(compoundKey.getPart0() + '|' + compoundKey.getPart1());
        }
    }

    /*
    /*******************************************************
    /* Test methods
    /*******************************************************
     */

    @Test
    public void testMapRefinement() throws Exception
    {
        String ID1 = "3a6383d4-8123-4c43-8b8d-7cedf3e59404";
        String ID2 = "81c3d978-90c4-4b00-8da1-1c39ffcab02c";
        String json = a2q(
"{'id':'"+ID1+"','items':[{'id':'"+ID2+"','property':'value'}]}");

        ObjectMapper m = new ObjectMapper();
        Data data = m.readValue(json, Data.class);

        assertEquals(ID1, data.id);
        assertNotNull(data.items);
        assertEquals(1, data.items.size());
        Item value = data.items.get(ID2);
        assertNotNull(value);
        assertEquals("value", value.property);
    }

    // for [databind#1384]
    @Test
    public void testMapKeyRefinement1384() throws Exception
    {
        final String TEST_INSTANCE_SERIALIZED =
                "{\"mapProperty\":[\"java.util.HashMap\",{\"Compound|Key\":\"Value\"}]}";
        ObjectMapper mapper = jsonMapperBuilder()
                .activateDefaultTyping(NoCheckSubTypeValidator.instance,
                        DefaultTyping.NON_FINAL)
                .build();
        TestClass testInstance = mapper.readValue(TEST_INSTANCE_SERIALIZED, TestClass.class);
        assertEquals(1, testInstance.mapProperty.size());
        Object key = testInstance.mapProperty.keySet().iterator().next();
        assertEquals(CompoundKey.class, key.getClass());
        String testInstanceSerialized = mapper.writeValueAsString(testInstance);
        assertEquals(TEST_INSTANCE_SERIALIZED, testInstanceSerialized);
    }
}
