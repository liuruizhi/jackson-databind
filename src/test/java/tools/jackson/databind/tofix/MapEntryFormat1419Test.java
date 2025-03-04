package tools.jackson.databind.tofix;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.core.testutil.failure.JacksonTestFailureExpected;

import com.fasterxml.jackson.annotation.JsonFormat;

import tools.jackson.databind.*;
import tools.jackson.databind.testutil.DatabindTestUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;

// for [databind#1419]
class MapEntryFormat1419Test extends DatabindTestUtil {
    static class BeanWithMapEntryAsObject {
        @JsonFormat(shape = JsonFormat.Shape.OBJECT)
        public Map.Entry<String, String> entry;

        protected BeanWithMapEntryAsObject() {
        }

        public BeanWithMapEntryAsObject(String key, String value) {
            Map<String, String> map = new HashMap<>();
            map.put(key, value);
            entry = map.entrySet().iterator().next();
        }
    }

    private final ObjectMapper MAPPER = newJsonMapper();

    @JacksonTestFailureExpected
    @Test
    void wrappedAsObjectRoundtrip() throws Exception {
        BeanWithMapEntryAsObject input = new BeanWithMapEntryAsObject("foo", "bar");
        String json = MAPPER.writeValueAsString(input);
        assertEquals(a2q("{'entry':{'key':'foo','value':'bar'}}"), json);
        BeanWithMapEntryAsObject result = MAPPER.readValue(json, BeanWithMapEntryAsObject.class);
        assertEquals("foo", result.entry.getKey());
        assertEquals("bar", result.entry.getValue());
    }
}
