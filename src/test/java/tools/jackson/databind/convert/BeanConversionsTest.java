package tools.jackson.databind.convert;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.*;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.util.StdConverter;

import static org.junit.jupiter.api.Assertions.*;

import static tools.jackson.databind.testutil.DatabindTestUtil.*;

/**
 * Tests for various conversions, especially ones using
 * {@link ObjectMapper#convertValue(Object, Class)}.
 */
public class BeanConversionsTest
{
    static class PointZ {
        public int x, y;

        public int z = -13;

        public PointZ() { }
        public PointZ(int a, int b, int c)
        {
            x = a;
            y = b;
            z = c;
        }
    }

    static class PointStrings {
        public final String x, y;

        public PointStrings(String x, String y) {
            this.x = x;
            this.y = y;
        }
    }

    public static class BooleanBean {
        public boolean boolProp;
    }

    static class WrapperBean {
        public BooleanBean x;
    }

    static class ObjectWrapper
    {
        private Object data;

        public ObjectWrapper() { }
        public ObjectWrapper(Object o) { data = o; }

        public Object getData() { return data; }
        public void setData(Object data) { this.data = data; }
    }

    static class Leaf {
        public int value;

        public Leaf() { }
        public Leaf(int v) { value = v; }
    }

    // [databind#288]

    @JsonSerialize(converter = ConvertingBeanConverter.class)
    static class ConvertingBean {
       public int x, y;
       public ConvertingBean(int v1, int v2) {
          x = v1;
          y = v2;
       }
    }

    @JsonPropertyOrder({ "a", "b" })
    public static class DummyBean {
       public final int a, b;
       public DummyBean(int v1, int v2) {
          a = v1 * 2;
          b = v2 * 2;
       }
    }

    static class ConvertingBeanConverter extends StdConverter<ConvertingBean, DummyBean>
    {
       @Override
       public DummyBean convert(ConvertingBean cb) {
          return new DummyBean(cb.x, cb.y);
       }
    }

    @JsonDeserialize(using = NullBeanDeserializer.class)
    static class NullBean {
        public static final NullBean NULL_INSTANCE = new NullBean();
    }

    static class NullBeanDeserializer extends ValueDeserializer<NullBean> {
        @Override
        public NullBean getNullValue(final DeserializationContext context) {
            return NullBean.NULL_INSTANCE;
        }

        @Override
        public NullBean deserialize(final JsonParser parser, final DeserializationContext context) {
            throw new UnsupportedOperationException();
        }
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    private final ObjectMapper MAPPER = newJsonMapper();

    @Test
    public void testBeanConvert()
    {
        // should have no problems convert between compatible beans...
        PointStrings input = new PointStrings("37", "-9");
        PointZ point = MAPPER.convertValue(input, PointZ.class);
        assertEquals(37, point.x);
        assertEquals(-9, point.y);
        // z not included in input, will be whatever default constructor provides
        assertEquals(-13, point.z);
    }

    // For [JACKSON-371]; verify that we know property that caused issue...
    // (note: not optimal place for test, but will have to do for now)
    @Test
    public void testErrorReporting() throws Exception
    {
        //String json = "{\"boolProp\":\"oops\"}";
        // First: unknown property
        try {
            MAPPER.readValue("{\"unknownProp\":true}", BooleanBean.class);
        } catch (UnrecognizedPropertyException e) {
            verifyException(e, "unknownProp");
        }

        // then bad conversion
        try {
            MAPPER.readValue("{\"boolProp\":\"foobar\"}", BooleanBean.class);
        } catch (InvalidFormatException e) {
            verifyException(e, "Cannot deserialize value of type `boolean` from String");
        }
    }

    @Test
    public void testIssue458() throws Exception
    {
        ObjectWrapper a = new ObjectWrapper("foo");
        ObjectWrapper b = new ObjectWrapper(a);
        ObjectWrapper b2 = MAPPER.convertValue(b, ObjectWrapper.class);
        ObjectWrapper a2 = MAPPER.convertValue(b2.getData(), ObjectWrapper.class);
        assertEquals("foo", a2.getData());
    }

    // should work regardless of wrapping...
    @Test
    public void testWrapping() throws Exception
    {
        ObjectMapper wrappingMapper = jsonMapperBuilder()
                .enable(DeserializationFeature.UNWRAP_ROOT_VALUE)
                .enable(SerializationFeature.WRAP_ROOT_VALUE)
                .build();

        // conversion is ok, even if it's bogus one
        _convertAndVerifyPoint(wrappingMapper);

        // also: ok to have mismatched settings, since as per [JACKSON-710], should
        // not actually use wrapping internally in these cases
        wrappingMapper = jsonMapperBuilder()
            .enable(DeserializationFeature.UNWRAP_ROOT_VALUE)
            .disable(SerializationFeature.WRAP_ROOT_VALUE)
            .build();
        _convertAndVerifyPoint(wrappingMapper);

        wrappingMapper = jsonMapperBuilder()
                .disable(DeserializationFeature.UNWRAP_ROOT_VALUE)
                .enable(SerializationFeature.WRAP_ROOT_VALUE)
                .build();
        _convertAndVerifyPoint(wrappingMapper);
    }

    // [Issue-11]: simple cast, for POJOs etc
    @Test
    public void testConvertUsingCast() throws Exception
    {
        String str = new String("foo");
        CharSequence seq = str;
        String result = MAPPER.convertValue(seq, String.class);
        // should just cast...
        assertSame(str, result);
    }

    private void _convertAndVerifyPoint(ObjectMapper m)
    {
        final PointZ input = new PointZ(1, 2, 3);
        PointZ output = m.convertValue(input, PointZ.class);
        assertEquals(1, output.x);
        assertEquals(2, output.y);
        assertEquals(3, output.z);
    }

    /**
     * Need to test "shortcuts" introduced by [databind#11] -- but
     * removed with [databind#2220]
     */
    @Test
    public void testIssue11() throws Exception
    {
        // then some other no-op conversions
        StringBuilder SB = new StringBuilder("test");
        CharSequence seq = MAPPER.convertValue(SB, CharSequence.class);
        assertNotSame(SB, seq);

        // and then something that should NOT use short-cut
        Leaf l = new Leaf(13);
        Map<?,?> m = MAPPER.convertValue(l, Map.class);
        assertNotNull(m);
        assertEquals(1, m.size());
        assertEquals(Integer.valueOf(13), m.get("value"));

        // and reverse too
        Leaf l2 = MAPPER.convertValue(m, Leaf.class);
        assertEquals(13, l2.value);

        // also; ok to use "untyped" (Object):
        Object ob = MAPPER.convertValue(l, Object.class);
        assertNotNull(ob);
        assertEquals(LinkedHashMap.class, ob.getClass());

        // And one more: this time with a minor twist
        final Object plaino = new Object();
        assertEquals("{}", MAPPER.writer()
                .writeValueAsString(plaino));
        // should now work, via serialization/deserialization:
        m = MAPPER.convertValue(plaino, Map.class);
        assertNotNull(m);
        assertEquals(0, m.size());
    }

    @Test
    public void testConversionIssue288() throws Exception
    {
        String json = MAPPER.writeValueAsString(new ConvertingBean(1, 2));
        // must be  {"a":2,"b":4}
        assertEquals("{\"a\":2,\"b\":4}", json);
    }

    // Test null conversions from [databind#1433]
    @Test
    public void testConversionIssue1433() throws Exception
    {
        assertNull(MAPPER.convertValue(null, Object.class));
        assertNull(MAPPER.convertValue(null, PointZ.class));

        assertSame(NullBean.NULL_INSTANCE,
                MAPPER.convertValue(null, NullBean.class));
    }
}
