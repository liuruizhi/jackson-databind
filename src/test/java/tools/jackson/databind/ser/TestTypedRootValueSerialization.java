package tools.jackson.databind.ser;

import java.util.*;

import org.junit.jupiter.api.Test;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.testutil.DatabindTestUtil;

import static org.junit.jupiter.api.Assertions.*;

public class TestTypedRootValueSerialization extends DatabindTestUtil
{
    static interface Issue822Interface {
        public int getA();
    }

    // If this annotation is added, things will work:
    //@tools.jackson.databind.annotation.JsonSerialize(as=Issue822Interface.class)
    // but it should not be necessary when root type is passed
    static class Issue822Impl implements Issue822Interface {
        @Override
        public int getA() { return 3; }
        public int getB() { return 9; }
    }

    // First ensure that basic interface-override works:
    @Test
    public void testTypedSerialization() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        String singleJson = mapper.writerFor(Issue822Interface.class).writeValueAsString(new Issue822Impl());
        // start with specific value case:
        assertEquals("{\"a\":3}", singleJson);
    }

    // [JACKSON-822]: ensure that type can be coerced
    @Test
    public void testTypedArrays() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
// Work-around when real solution not yet implemented:
//        mapper.enable(MapperFeature.USE_STATIC_TYPING);
        assertEquals("[{\"a\":3}]", mapper.writerFor(Issue822Interface[].class).writeValueAsString(
                new Issue822Interface[] { new Issue822Impl() }));
    }

    // [JACKSON-822]: ensure that type can be coerced
    @Test
    public void testTypedLists() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
     // Work-around when real solution not yet implemented:
//        mapper.enable(MapperFeature.USE_STATIC_TYPING);

        List<Issue822Interface> list = new ArrayList<Issue822Interface>();
        list.add(new Issue822Impl());
        String listJson = mapper.writerFor(new TypeReference<List<Issue822Interface>>(){})
                .writeValueAsString(list);
        assertEquals("[{\"a\":3}]", listJson);
    }

    @Test
    public void testTypedMaps() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        Map<String,Issue822Interface> map = new HashMap<String,Issue822Interface>();
        map.put("a", new Issue822Impl());
        String listJson = mapper.writerFor(new TypeReference<Map<String,Issue822Interface>>(){})
                .writeValueAsString(map);
        assertEquals("{\"a\":{\"a\":3}}", listJson);
    }
}
