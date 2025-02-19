package tools.jackson.databind.ser;

import java.io.*;
import java.util.*;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.*;
import tools.jackson.databind.testutil.DatabindTestUtil;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for checking handling of some of {@link MapperFeature}s
 * and {@link SerializationFeature}s for serialization.
 */
public class SerializationFeaturesTest
    extends DatabindTestUtil
{
    static class CloseableBean implements AutoCloseable
    {
        public int a = 3;

        protected boolean wasClosed = false;

        @Override
        public void close() throws IOException {
            wasClosed = true;
        }
    }

    private static class StringListBean {
        @SuppressWarnings("unused")
        public Collection<String> values;

        public StringListBean(Collection<String> v) { values = v; }
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    private final ObjectMapper MAPPER = newJsonMapper();

    @SuppressWarnings("resource")
    @Test
    public void testCloseCloseable() throws IOException
    {
        // default should be disabled:
        CloseableBean bean = new CloseableBean();
        MAPPER.writeValueAsString(bean);
        assertFalse(bean.wasClosed);

        // via writer as well
        bean = new CloseableBean();
        MAPPER.writer()
            .writeValueAsString(bean);
        assertFalse(bean.wasClosed);
        
        // but can enable it:
        ObjectMapper mapper2 = jsonMapperBuilder()
                .enable(SerializationFeature.CLOSE_CLOSEABLE)
                .build();
        bean = new CloseableBean();
        mapper2.writeValueAsString(bean);
        assertTrue(bean.wasClosed);

        // and same via writer
        bean = new CloseableBean();
        mapper2.writer()
            .writeValueAsString(bean);
        assertTrue(bean.wasClosed);

        // also: let's ensure that ObjectWriter won't interfere with it
        bean = new CloseableBean();
        MAPPER.writerFor(CloseableBean.class)
            .with(SerializationFeature.CLOSE_CLOSEABLE)
            .writeValueAsString(bean);
        assertTrue(bean.wasClosed);
    }

    @Test
    public void testCharArrays() throws IOException
    {
        char[] chars = new char[] { 'a','b','c' };
        ObjectMapper m = new ObjectMapper();
        // default: serialize as Strings
        assertEquals(q("abc"), m.writeValueAsString(chars));

        // new feature: serialize as JSON array:
        assertEquals("[\"a\",\"b\",\"c\"]",
                m.writer()
                .with(SerializationFeature.WRITE_CHAR_ARRAYS_AS_JSON_ARRAYS)
                .writeValueAsString(chars));
    }

    @Test
    public void testFlushingAutomatic() throws IOException
    {
        ObjectMapper mapper = new ObjectMapper();
        assertTrue(mapper.serializationConfig().isEnabled(SerializationFeature.FLUSH_AFTER_WRITE_VALUE));
        // default is to flush after writeValue()
        StringWriter sw = new StringWriter();
        mapper.writeValue(sw, Integer.valueOf(13));
        assertEquals("13", sw.toString());

        // ditto with ObjectWriter
        sw = new StringWriter();
        ObjectWriter ow = mapper.writer();
        ow.writeValue(sw, Integer.valueOf(99));
        assertEquals("99", sw.toString());
    }

    @Test
    public void testFlushingNotAutomatic() throws IOException
    {
        // but should not occur if configured otherwise
        ObjectMapper mapper = jsonMapperBuilder()
                .configure(SerializationFeature.FLUSH_AFTER_WRITE_VALUE, false)
                .build();
        StringWriter sw = new StringWriter();
        JsonGenerator g = mapper.createGenerator(sw);

        mapper.writeValue(g, Integer.valueOf(13));
        // no flushing now:
        assertEquals("", sw.toString());
        // except when actually flushing
        g.flush();
        assertEquals("13", sw.toString());
        g.close();
        // Also, same should happen with ObjectWriter
        sw = new StringWriter();
        g = mapper.createGenerator(sw);
        ObjectWriter ow = mapper.writer();
        ow.writeValue(g, Integer.valueOf(99));
        assertEquals("", sw.toString());
        // except when actually flushing
        g.flush();
        assertEquals("99", sw.toString());
        g.close();
    }

    @Test
    public void testSingleElementCollections() throws IOException
    {
        final ObjectWriter writer = objectWriter().with(SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED);

        // Lists:
        ArrayList<String> strs = new ArrayList<String>();
        strs.add("xyz");
        assertEquals(q("xyz"), writer.writeValueAsString(strs));
        ArrayList<Integer> ints = new ArrayList<Integer>();
        ints.add(13);
        assertEquals("13", writer.writeValueAsString(ints));

        // other Collections, like Sets:
        HashSet<Long> longs = new HashSet<Long>();
        longs.add(42L);
        assertEquals("42", writer.writeValueAsString(longs));
        // [databind#180]
        final String EXP_STRINGS = "{\"values\":\"foo\"}";
        assertEquals(EXP_STRINGS, writer.writeValueAsString(new StringListBean(Collections.singletonList("foo"))));

        final Set<String> SET = new HashSet<String>();
        SET.add("foo");
        assertEquals(EXP_STRINGS, writer.writeValueAsString(new StringListBean(SET)));

        // arrays:
        assertEquals("true", writer.writeValueAsString(new boolean[] { true }));
        assertEquals("[true,false]", writer.writeValueAsString(new boolean[] { true, false }));
        assertEquals("true", writer.writeValueAsString(new Boolean[] { Boolean.TRUE }));

        assertEquals("3", writer.writeValueAsString(new short[] { 3 }));
        assertEquals("[3,2]", writer.writeValueAsString(new short[] { 3, 2 }));

        assertEquals("3", writer.writeValueAsString(new int[] { 3 }));
        assertEquals("[3,2]", writer.writeValueAsString(new int[] { 3, 2 }));

        assertEquals("1", writer.writeValueAsString(new long[] { 1L }));
        assertEquals("[-1,4]", writer.writeValueAsString(new long[] { -1L, 4L }));

        assertEquals("0.5", writer.writeValueAsString(new double[] { 0.5 }));
        assertEquals("[0.5,2.5]", writer.writeValueAsString(new double[] { 0.5, 2.5 }));

        assertEquals("0.5", writer.writeValueAsString(new float[] { 0.5f }));
        assertEquals("[0.5,2.5]", writer.writeValueAsString(new float[] { 0.5f, 2.5f }));

        assertEquals(q("foo"), writer.writeValueAsString(new String[] { "foo" }));
    }
}
