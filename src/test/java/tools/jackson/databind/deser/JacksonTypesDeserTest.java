package tools.jackson.databind.deser;

import java.util.*;

import org.junit.jupiter.api.Test;

import tools.jackson.core.*;
import tools.jackson.core.io.ContentReference;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.TypeFactory;
import tools.jackson.databind.util.TokenBuffer;

import static org.junit.jupiter.api.Assertions.*;

import static tools.jackson.databind.testutil.DatabindTestUtil.*;

/**
 * Unit tests for those Jackson types we want to ensure can be deserialized.
 */
public class JacksonTypesDeserTest
{
    private final ObjectMapper MAPPER = sharedMapper();

    @Test
    public void testTokenStreamLocation() throws Exception
    {
        // note: source reference is untyped, only String guaranteed to work
        TokenStreamLocation loc = new TokenStreamLocation(ContentReference.rawReference("whatever"),
                -1, -1, 100, 13);
        // Let's use serializer here; goal is round-tripping
        String ser = MAPPER.writerWithDefaultPrettyPrinter()
                .writeValueAsString(loc);
        TokenStreamLocation result = MAPPER.readValue(ser, TokenStreamLocation.class);
        assertNotNull(result);
        // 14-Mar-2021, tatu: Should NOT count on this being retained actually,
        //   after 2.13 (will retain for now)...
//        assertEquals(loc.getSourceRef(), result.getSourceRef());
        assertEquals(loc.getByteOffset(), result.getByteOffset());
        assertEquals(loc.getCharOffset(), result.getCharOffset());
        assertEquals(loc.getColumnNr(), result.getColumnNr());
        assertEquals(loc.getLineNr(), result.getLineNr());
    }

    // doesn't really belong here but...
    @Test
    public void testTokenStreamLocationProps()
    {
        TokenStreamLocation loc = new TokenStreamLocation(null,  -1, -1, 100, 13);
        assertTrue(loc.equals(loc));
        assertFalse(loc.equals(null));
        final Object value = "abx";
        assertFalse(loc.equals(value));

        // should we check it's not 0?
        loc.hashCode();
    }

    @Test
    public void testJavaType() throws Exception
    {
        TypeFactory tf = defaultTypeFactory();
        // first simple type:
        String json = MAPPER.writeValueAsString(tf.constructType(String.class));
        assertEquals(q(java.lang.String.class.getName()), json);
        // and back
        JavaType t = MAPPER.readValue(json, JavaType.class);
        assertNotNull(t);
        assertEquals(String.class, t.getRawClass());
    }

    /**
     * Verify that {@link TokenBuffer} can be properly deserialized
     * automatically, using the "standard" JSON sample document
     */
    @Test
    public void testTokenBufferWithSample() throws Exception
    {
        // First, try standard sample doc:
        try (TokenBuffer result = MAPPER.readValue(SAMPLE_DOC_JSON_SPEC, TokenBuffer.class)) {
            verifyJsonSpecSampleDoc(result.asParser(ObjectReadContext.empty()), true);
        }
    }

    @SuppressWarnings("resource")
    @Test
    public void testTokenBufferWithSequence() throws Exception
    {
        final ObjectMapper mapper = jsonMapperBuilder()
                .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
        
        // and then sequence of other things
        JsonParser p = mapper.createParser("[ 32, [ 1 ], \"abc\", { \"a\" : true } ]");
        assertToken(JsonToken.START_ARRAY, p.nextToken());

        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        TokenBuffer buf = mapper.readValue(p, TokenBuffer.class);

        // check manually...
        JsonParser bufParser = buf.asParser(ObjectReadContext.empty());
        assertToken(JsonToken.VALUE_NUMBER_INT, bufParser.nextToken());
        assertEquals(32, bufParser.getIntValue());
        assertNull(bufParser.nextToken());

        // then bind to another
        buf = mapper.readValue(p, TokenBuffer.class);
        bufParser = buf.asParser(ObjectReadContext.empty());
        assertToken(JsonToken.START_ARRAY, bufParser.nextToken());
        assertToken(JsonToken.VALUE_NUMBER_INT, bufParser.nextToken());
        assertEquals(1, bufParser.getIntValue());
        assertToken(JsonToken.END_ARRAY, bufParser.nextToken());
        assertNull(bufParser.nextToken());

        // third one, with automatic binding
        buf = mapper.readValue(p, TokenBuffer.class);
        String str = mapper.readValue(buf.asParser(ObjectReadContext.empty()), String.class);
        assertEquals("abc", str);

        // and ditto for last one
        buf = mapper.readValue(p, TokenBuffer.class);
        Map<?,?> map = mapper.readValue(buf.asParser(ObjectReadContext.empty()), Map.class);
        assertEquals(1, map.size());
        assertEquals(Boolean.TRUE, map.get("a"));

        assertEquals(JsonToken.END_ARRAY, p.nextToken());
        assertNull(p.nextToken());
    }

    // 10k does it, 5k not, but use bit higher values just in case
    private final static int RECURSION_2398 = 25000;

    public void testJavaTypeDeser() throws Exception
    {
        TypeFactory tf = defaultTypeFactory();
        // first simple type:
        String json = MAPPER.writeValueAsString(tf.constructType(String.class));
        assertEquals(q(java.lang.String.class.getName()), json);
        // and back
        JavaType t = MAPPER.readValue(json, JavaType.class);
        assertNotNull(t);
        assertEquals(String.class, t.getRawClass());
    }

    // [databind#2398]
    @Test
    public void testDeeplyNestedArrays() throws Exception
    {
        JsonFactory jsonFactory = JsonFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(Integer.MAX_VALUE).build())
                .build();
        try (JsonParser p = JsonMapper.builder(jsonFactory).build().createParser(
                _createNested(RECURSION_2398 * 2, "[", " 123 ", "]"))) {
            p.nextToken();
            TokenBuffer b = TokenBuffer.forGeneration();
            b.copyCurrentStructure(p);
            b.close();
        }
    }

    @Test
    public void testDeeplyNestedObjects() throws Exception
    {
        JsonFactory jsonFactory = JsonFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(Integer.MAX_VALUE).build())
                .build();
        try (JsonParser p = JsonMapper.builder(jsonFactory).build().createParser(
                _createNested(RECURSION_2398, "{\"a\":", "42", "}"))) {
            p.nextToken();
            TokenBuffer b = TokenBuffer.forGeneration();
            b.copyCurrentStructure(p);
            b.close();
        }
    }

    private String _createNested(int nesting, String open, String middle, String close)
    {
        StringBuilder sb = new StringBuilder(2 * nesting);
        for (int i = 0; i < nesting; ++i) {
            sb.append(open);
        }
        sb.append(middle);
        for (int i = 0; i < nesting; ++i) {
            sb.append(close);
        }
        return sb.toString();
    }
}
