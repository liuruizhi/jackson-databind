package tools.jackson.databind.ser;

import org.junit.jupiter.api.Test;

import tools.jackson.core.*;
import tools.jackson.databind.*;
import tools.jackson.databind.testutil.DatabindTestUtil;

import static org.junit.jupiter.api.Assertions.*;

public class TestArraySerialization
    extends DatabindTestUtil
{
    private final ObjectMapper MAPPER = sharedMapper();

    @Test
    public void testLongStringArray() throws Exception
    {
        final int SIZE = 40000;

        StringBuilder sb = new StringBuilder(SIZE*2);
        for (int i = 0; i < SIZE; ++i) {
            sb.append((char) i);
        }
        String str = sb.toString();
        byte[] data = MAPPER.writeValueAsBytes(new String[] { "abc", str, null, str });
        JsonParser p = MAPPER.createParser(data);
        assertToken(JsonToken.START_ARRAY, p.nextToken());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals("abc", p.getString());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        String actual = p.getString();
        assertEquals(str.length(), actual.length());
        assertEquals(str, actual);
        assertToken(JsonToken.VALUE_NULL, p.nextToken());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals(str, p.getString());
        assertToken(JsonToken.END_ARRAY, p.nextToken());
        assertNull(p.nextToken());
        p.close();
    }

    @Test
    public void testIntArray() throws Exception
    {
        String json = MAPPER.writeValueAsString(new int[] { 1, 2, 3, -7 });
        assertEquals("[1,2,3,-7]", json);
    }

    @Test
    public void testBigIntArray() throws Exception
    {
        final int SIZE = 99999;
        int[] ints = new int[SIZE];
        for (int i = 0; i < ints.length; ++i) {
            ints[i] = i;
        }

        // Let's try couple of times, to ensure that state is handled
        // correctly by ObjectMapper (wrt buffer recycling used
        // with 'writeAsBytes()')
        for (int round = 0; round < 3; ++round) {
            byte[] data = MAPPER.writeValueAsBytes(ints);
            JsonParser p = MAPPER.createParser(data);
            assertToken(JsonToken.START_ARRAY, p.nextToken());
            for (int i = 0; i < SIZE; ++i) {
                assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
                assertEquals(i, p.getIntValue());
            }
            assertToken(JsonToken.END_ARRAY, p.nextToken());
            p.close();
        }
    }

    @Test
    public void testLongArray() throws Exception
    {
        String json = MAPPER.writeValueAsString(new long[] { Long.MIN_VALUE, 0, Long.MAX_VALUE });
        assertEquals("["+Long.MIN_VALUE+",0,"+Long.MAX_VALUE+"]", json);
    }

    @Test
    public void testStringArray() throws Exception
    {
        assertEquals("[\"a\",\"\\\"foo\\\"\",null]",
                MAPPER.writeValueAsString(new String[] { "a", "\"foo\"", null }));
        assertEquals("[]",
                MAPPER.writeValueAsString(new String[] { }));
    }

    @Test
    public void testDoubleArray() throws Exception
    {
        String json = MAPPER.writeValueAsString(new double[] { 1.01, 2.0, -7, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY });
        assertEquals("[1.01,2.0,-7.0,\"NaN\",\"-Infinity\",\"Infinity\"]", json);
    }

    @Test
    public void testFloatArray() throws Exception
    {
        String json = MAPPER.writeValueAsString(new float[] { 1.01f, 2.0f, -7f, Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY });
        assertEquals("[1.01,2.0,-7.0,\"NaN\",\"-Infinity\",\"Infinity\"]", json);
    }
}
