package tools.jackson.databind.deser.merge;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonMerge;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.*;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.*;

import static tools.jackson.databind.testutil.DatabindTestUtil.*;

public class NodeMergeTest
{
    final static ObjectMapper MAPPER = jsonMapperBuilder()
            // 26-Oct-2016, tatu: Make sure we'll report merge problems by default
            .disable(MapperFeature.IGNORE_MERGE_FOR_UNMERGEABLE)
            // 15-Feb-2021, tatu: slightly related to [databind#3056],
            //   ensure that dup detection will not trip handling here
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .build()
    ;

    static class ObjectNodeWrapper {
        @JsonMerge
        public ObjectNode props = MAPPER.createObjectNode();
        {
            props.put("default", "enabled");
        }
    }

    static class ArrayNodeWrapper {
        @JsonMerge
        public ArrayNode list = MAPPER.createArrayNode();
        {
            list.add(123);
        }
    }

    /*
    /********************************************************
    /* Test methods
    /********************************************************
     */

    @Test
    public void testObjectNodeUpdateValue() throws Exception
    {
        ObjectNode base = MAPPER.createObjectNode();
        base.put("first", "foo");
        assertSame(base,
                MAPPER.readerForUpdating(base)
                .readValue(a2q("{'second':'bar', 'third':5, 'fourth':true}")));
        assertEquals(4, base.size());
        assertEquals("bar", base.path("second").asString());
        assertEquals("foo", base.path("first").asString());
        assertEquals(5, base.path("third").asInt());
        assertTrue(base.path("fourth").asBoolean());
    }

    @Test
    public void testObjectNodeMerge() throws Exception
    {
        ObjectNodeWrapper w = MAPPER.readValue(a2q("{'props':{'stuff':'xyz'}}"),
                ObjectNodeWrapper.class);
        assertEquals(2, w.props.size());
        assertEquals("enabled", w.props.path("default").asString());
        assertEquals("xyz", w.props.path("stuff").asString());
    }

    @Test
    public void testObjectDeepUpdate() throws Exception
    {
        ObjectNode base = MAPPER.createObjectNode();
        ObjectNode props = base.putObject("props");
        props.put("base", 123);
        props.put("value", 456);
        ArrayNode a = props.putArray("array");
        a.add(true);
        base.putNull("misc");
        assertSame(base,
                MAPPER.readerForUpdating(base)
                .readValue(a2q(
"{'props':{'value':true, 'extra':25.5, 'array' : [ 3 ]}}")));
        assertEquals(2, base.size());
        ObjectNode resultProps = (ObjectNode) base.get("props");
        assertEquals(4, resultProps.size());

        assertEquals(123, resultProps.path("base").asInt());
        assertTrue(resultProps.path("value").asBoolean());
        assertEquals(25.5, resultProps.path("extra").asDouble());
        JsonNode n = resultProps.get("array");
        assertEquals(ArrayNode.class, n.getClass());
        assertEquals(2, n.size());
        assertEquals(3, n.get(1).asInt());
    }

    @Test
    public void testArrayNodeUpdateValue() throws Exception
    {
        ArrayNode base = MAPPER.createArrayNode();
        base.add("first");
        assertSame(base,
                MAPPER.readerForUpdating(base)
                .readValue(a2q("['second',false,null]")));
        assertEquals(4, base.size());
        assertEquals("first", base.path(0).asString());
        assertEquals("second", base.path(1).asString());
        assertFalse(base.path(2).asBoolean());
        assertTrue(base.path(3).isNull());
    }

    @Test
    public void testArrayNodeMerge() throws Exception
    {
        ArrayNodeWrapper w = MAPPER.readValue(a2q("{'list':[456,true,{},  [], 'foo']}"),
                ArrayNodeWrapper.class);
        assertEquals(6, w.list.size());
        assertEquals(123, w.list.get(0).asInt());
        assertEquals(456, w.list.get(1).asInt());
        assertTrue(w.list.get(2).asBoolean());
        JsonNode n = w.list.get(3);
        assertTrue(n.isObject());
        assertEquals(0, n.size());
        n = w.list.get(4);
        assertTrue(n.isArray());
        assertEquals(0, n.size());
        assertEquals("foo", w.list.get(5).asString());
    }

    // [databind#3056]
    @Test
    public void testUpdateObjectNodeWithNull() throws Exception
    {
        JsonNode src = MAPPER.readTree(a2q("{'test':{}}"));
        JsonNode update = MAPPER.readTree(a2q("{'test':null}"));

        ObjectNode result = MAPPER.readerForUpdating(src)
            .readValue(update);

        assertEquals(a2q("{'test':null}"), result.toString());
    }

    @Test
    public void testUpdateObjectNodeWithNumber() throws Exception
    {
        JsonNode src = MAPPER.readTree(a2q("{'test':{}}"));
        JsonNode update = MAPPER.readTree(a2q("{'test':123}"));

        ObjectNode result = MAPPER.readerForUpdating(src)
            .readValue(update);

        assertEquals(a2q("{'test':123}"), result.toString());
    }

    @Test
    public void testUpdateArrayWithNull() throws Exception
    {
        JsonNode src = MAPPER.readTree(a2q("{'test':[]}"));
        JsonNode update = MAPPER.readTree(a2q("{'test':null}"));

        ObjectNode result = MAPPER.readerForUpdating(src)
            .readValue(update);

        assertEquals(a2q("{'test':null}"), result.toString());
    }

    @Test
    public void testUpdateArrayWithString() throws Exception
    {
        JsonNode src = MAPPER.readTree(a2q("{'test':[]}"));
        JsonNode update = MAPPER.readTree(a2q("{'test':'NA'}"));

        ObjectNode result = MAPPER.readerForUpdating(src)
            .readValue(update);

        assertEquals(a2q("{'test':'NA'}"), result.toString());
    }

    // [databind#3122]: "readTree()" fails where "readValue()" doesn't:
    @Test
    public void testObjectDeepMerge3122() throws Exception
    {
        final String jsonToMerge = a2q("{'root':{'b':'bbb','foo':'goodbye'}}");

        JsonNode node1 = MAPPER.readTree(a2q("{'root':{'a':'aaa','foo':'hello'}}"));
        assertEquals(2, node1.path("root").size());

        JsonNode node2 = MAPPER.readerForUpdating(node1)
                .readValue(jsonToMerge);
        assertSame(node1, node2);
        assertEquals(3, node1.path("root").size());

        node1 = MAPPER.readTree(a2q("{'root':{'a':'aaa','foo':'hello'}}"));
        JsonNode node3 = MAPPER.readerForUpdating(node1)
                .readTree(jsonToMerge);
        assertSame(node1, node3);
        assertEquals(3, node1.path("root").size());

        node1 = MAPPER.readTree(a2q("{'root':{'a':'aaa','foo':'hello'}}"));
        JsonNode node4 = MAPPER.readerForUpdating(node1)
                .readTree(utf8Bytes(jsonToMerge));
        assertSame(node1, node4);
        assertEquals(3, node1.path("root").size());

        // And finally variant passing `JsonParser`
        try (JsonParser p = MAPPER.createParser(jsonToMerge)) {
            JsonNode node5 = MAPPER.readerForUpdating(node1)
                    .readTree(p);
            assertSame(node1, node5);
            assertEquals(3, node1.path("root").size());
        }
    }
}
