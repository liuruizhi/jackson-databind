package tools.jackson.databind.contextual;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.*;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.cfg.ContextAttributes;
import tools.jackson.databind.ser.std.StdScalarSerializer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static tools.jackson.databind.testutil.DatabindTestUtil.a2q;
import static tools.jackson.databind.testutil.DatabindTestUtil.jsonMapperBuilder;
import static tools.jackson.databind.testutil.DatabindTestUtil.newJsonMapper;

public class ContextAttributeWithSerTest
{
    final static String KEY = "foobar";

    static class PrefixStringSerializer extends StdScalarSerializer<String>
    {
        protected PrefixStringSerializer() {
            super(String.class);
        }

        @Override
        public void serialize(String value, JsonGenerator jgen,
                SerializationContext provider)
        {
            Integer I = (Integer) provider.getAttribute(KEY);
            if (I == null) {
                I = Integer.valueOf(0);
            }
            int i = I.intValue();
            provider.setAttribute(KEY, Integer.valueOf(i + 1));
            jgen.writeString("" +i+":"+value);
        }
    }

    static class TestPOJO
    {
        @JsonSerialize(using=PrefixStringSerializer.class)
        public String value;

        public TestPOJO(String str) { value = str; }
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    final ObjectMapper MAPPER = newJsonMapper();

    @Test
    public void testSimplePerCall() throws Exception
    {
        final String EXP = a2q("[{'value':'0:a'},{'value':'1:b'}]");
        ObjectWriter w = MAPPER.writer();
        final TestPOJO[] INPUT = new TestPOJO[] {
                new TestPOJO("a"), new TestPOJO("b") };
        assertEquals(EXP, w.writeValueAsString(INPUT));

        // also: ensure that we don't retain per-call state accidentally:
        assertEquals(EXP, w.writeValueAsString(INPUT));
    }

    @Test
    public void testSimpleDefaults() throws Exception
    {
        final String EXP = a2q("{'value':'3:xyz'}");
        final TestPOJO INPUT = new TestPOJO("xyz");
        String json = MAPPER.writer().withAttribute(KEY, Integer.valueOf(3))
                .writeValueAsString(INPUT);
        assertEquals(EXP, json);

        String json2 = MAPPER.writer().withAttribute(KEY, Integer.valueOf(3))
                .writeValueAsString(INPUT);
        assertEquals(EXP, json2);
    }

    @Test
    public void testHierarchic() throws Exception
    {
        final TestPOJO[] INPUT = new TestPOJO[] { new TestPOJO("a"), new TestPOJO("b") };
        final String EXP = a2q("[{'value':'2:a'},{'value':'3:b'}]");
        ObjectWriter w = MAPPER.writer().withAttribute(KEY, Integer.valueOf(2));
        assertEquals(EXP, w.writeValueAsString(INPUT));

        // and verify state clearing:
        assertEquals(EXP, w.writeValueAsString(INPUT));
    }

    // [databind#3001]
    @Test
    public void testDefaultsViaMapper() throws Exception
    {
        final TestPOJO[] INPUT = new TestPOJO[] { new TestPOJO("a"), new TestPOJO("b") };
        ContextAttributes attrs = ContextAttributes.getEmpty()
                .withSharedAttribute(KEY, Integer.valueOf(72));
        ObjectMapper mapper = jsonMapperBuilder()
                .defaultAttributes(attrs)
                .build();
        final String EXP1 = a2q("[{'value':'72:a'},{'value':'73:b'}]");
        assertEquals(EXP1, mapper.writeValueAsString(INPUT));

        // value should be "reset" as well
        assertEquals(EXP1, mapper.writeValueAsString(INPUT));

        // and should be overridable on per-call basis too
        assertEquals(a2q("[{'value':'13:a'},{'value':'14:b'}]"),
                mapper.writer()
                    .withAttribute(KEY, Integer.valueOf(13))
                    .writeValueAsString(INPUT));
    }
}
