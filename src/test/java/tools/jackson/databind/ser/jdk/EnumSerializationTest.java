package tools.jackson.databind.ser.jdk;

import java.util.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.*;

import tools.jackson.core.*;
import tools.jackson.databind.*;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.jsontype.TypeSerializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;
import tools.jackson.databind.ser.std.ToStringSerializer;
import tools.jackson.databind.testutil.DatabindTestUtil;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for verifying serialization of simple basic non-structured
 * types; primitives (and/or their wrappers), Strings.
 */
public class EnumSerializationTest
    extends DatabindTestUtil
{
    /**
     * Test enumeration for verifying Enum serialization functionality.
     */
    protected enum TestEnum {
        A, B, C;
        private TestEnum() { }

        @Override public String toString() { return name().toLowerCase(); }
    }

    /**
     * Alternative version that forces use of "toString-serializer".
     */
    @JsonSerialize(using=ToStringSerializer.class)
    protected enum AnnotatedTestEnum {
        A2, B2, C2;
        private AnnotatedTestEnum() { }

        @Override public String toString() { return name().toLowerCase(); }
    }

    protected enum EnumWithJsonValue {
        A("foo"), B("bar");
        private final String name;
        private EnumWithJsonValue(String n) {
            name = n;
        }

        @Override
        public String toString() { return name; }

        @JsonValue
        public String external() { return "value:"+name; }
    }

    protected static interface ToStringMixin {
        @Override
        @JsonValue public String toString();
    }

    protected static enum SerializableEnum implements JacksonSerializable
    {
        A, B, C;

        private SerializableEnum() { }

        @Override
        public void serializeWithType(JsonGenerator g, SerializationContext provider, TypeSerializer typeSer)
        {
            serialize(g, provider);
        }

        @Override
        public void serialize(JsonGenerator g, SerializationContext provider)
        {
            g.writeString("foo");
        }
    }

    protected static enum LowerCaseEnum {
        A, B, C;
        private LowerCaseEnum() { }
        @Override
        public String toString() { return name().toLowerCase(); }
    }

    static class MapBean {
        public Map<TestEnum,Integer> map = new HashMap<TestEnum,Integer>();

        public void add(TestEnum key, int value) {
            map.put(key, Integer.valueOf(value));
        }
    }

    static enum NOT_OK {
        V1("v1");
        protected String key;
        // any runtime-persistent annotation is fine
        NOT_OK(@JsonProperty String key) { this.key = key; }
    }

    static enum OK {
        V1("v1");
        protected String key;
        OK(String key) { this.key = key; }
    }

    @SuppressWarnings("rawtypes")
    static class LowerCasingEnumSerializer extends StdSerializer<Enum>
    {
        public LowerCasingEnumSerializer() { super(Enum.class); }
        @Override
        public void serialize(Enum value, JsonGenerator g,
                SerializationContext provider) {
            g.writeString(value.name().toLowerCase());
        }
    }

    protected static enum LC749Enum {
        A, B, C;
        private LC749Enum() { }
        @Override
        public String toString() { return name().toLowerCase(); }
    }

    // for [databind#1322]
    protected enum EnumWithJsonProperty {
        @JsonProperty("aleph")
        A;
    }

    // [databind#2871]: add `@JsonKey`
    protected enum EnumWithJsonKey {
        A("a"), B("b");
        private final String name;
        private EnumWithJsonKey(String n) {
            name = n;
        }

        @Override
        public String toString() { return name; }

        @JsonKey
        public String externalKey() { return "key:"+name; }

        @JsonValue
        public String externalValue() { return "value:"+name; }
    }

    /*
    /**********************************************************************
    /* Test methods
    /**********************************************************************
     */

    private final ObjectMapper MAPPER = jsonMapperBuilder()
            .disable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING).build();

    @Test
    public void testSimple() throws Exception
    {
        assertEquals("\"B\"", MAPPER.writeValueAsString(TestEnum.B));
    }

    @Test
    public void testEnumSet() throws Exception
    {
        final EnumSet<TestEnum> value = EnumSet.of(TestEnum.B);
        assertEquals("[\"B\"]", MAPPER.writeValueAsString(value));
    }

    /**
     * Whereas regular Enum serializer uses enum names, some users
     * prefer calling toString() instead. So let's verify that
     * this can be done using annotation for enum class.
     */
    @Test
    public void testEnumUsingToString() throws Exception
    {
        assertEquals("\"c2\"", MAPPER.writeValueAsString(AnnotatedTestEnum.C2));
    }

    @Test
    public void testSubclassedEnums() throws Exception
    {
        assertEquals("\"B\"", MAPPER.writeValueAsString(EnumWithSubClass.B));
    }

    @Test
    public void testEnumsWithJsonValue() throws Exception {
        assertEquals("\"value:bar\"", MAPPER.writeValueAsString(EnumWithJsonValue.B));
    }

    @Test
    public void testEnumsWithJsonValueUsingMixin() throws Exception
    {
        // can't share, as new mix-ins are added
        ObjectMapper mapper = jsonMapperBuilder()
                .addMixIn(TestEnum.class, ToStringMixin.class)
                .build();
        assertEquals("\"b\"", mapper.writeValueAsString(TestEnum.B));
    }

    // [databind#601]
    @Test
    public void testEnumsWithJsonValueInMap() throws Exception
    {
        EnumMap<EnumWithJsonValue,String> input = new EnumMap<EnumWithJsonValue,String>(EnumWithJsonValue.class);
        input.put(EnumWithJsonValue.B, "x");
        // 24-Sep-2015, tatu: SHOULD actually use annotated method, as per:
        assertEquals("{\"value:bar\":\"x\"}", MAPPER.writeValueAsString(input));
    }

    /**
     * Test for ensuring that @JsonSerializable is used with Enum types as well
     * as with any other types.
     */
    @Test
    public void testSerializableEnum() throws Exception
    {
        assertEquals("\"foo\"", MAPPER.writeValueAsString(SerializableEnum.A));
    }

    @Test
    public void testToStringEnum() throws Exception
    {
        ObjectMapper m = jsonMapperBuilder()
                .configure(SerializationFeature.WRITE_ENUMS_USING_TO_STRING, true)
                .build();
        assertEquals("\"b\"", m.writeValueAsString(LowerCaseEnum.B));

        // [databind#749] but should also be able to dynamically disable
        assertEquals("\"B\"",
                m.writer().without(SerializationFeature.WRITE_ENUMS_USING_TO_STRING)
                    .writeValueAsString(LowerCaseEnum.B));
    }

    @Test
    public void testToStringEnumWithEnumMap() throws Exception
    {
        ObjectMapper m = jsonMapperBuilder()
                .enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING)
                .build();
        EnumMap<LowerCaseEnum,String> enums = new EnumMap<LowerCaseEnum,String>(LowerCaseEnum.class);
        enums.put(LowerCaseEnum.C, "value");
        assertEquals("{\"c\":\"value\"}", m.writeValueAsString(enums));
    }

    @Test
    public void testAsIndex() throws Exception
    {
        // By default, serialize using name
        ObjectMapper m = jsonMapperBuilder()
                .disable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING)
                .build();
        assertFalse(m.isEnabled(SerializationFeature.WRITE_ENUMS_USING_INDEX));
        assertEquals(q("B"), m.writeValueAsString(TestEnum.B));

        // but we can change (dynamically, too!) it to be number-based
        m = jsonMapperBuilder()
                .enable(SerializationFeature.WRITE_ENUMS_USING_INDEX)
                .build();
        assertEquals("1", m.writeValueAsString(TestEnum.B));
    }

    @Test
    public void testAnnotationsOnEnumCtor() throws Exception
    {
        assertEquals(q("V1"), MAPPER.writeValueAsString(OK.V1));
        assertEquals(q("V1"), MAPPER.writeValueAsString(NOT_OK.V1));
        assertEquals(q("V2"), MAPPER.writeValueAsString(NOT_OK2.V2));
    }

    // [databind#227]
    @Test
    public void testGenericEnumSerializer() throws Exception
    {
        // By default, serialize using name
        SimpleModule module = new SimpleModule("foobar");
        module.addSerializer(Enum.class, new LowerCasingEnumSerializer());
        ObjectMapper mapper = jsonMapperBuilder()
                .addModule(module)
                .build();
        assertEquals(q("b"), mapper.writeValueAsString(TestEnum.B));
    }

    // [databind#749]

    @Test
    public void testEnumMapSerDefault() throws Exception {
        final ObjectMapper mapper = jsonMapperBuilder()
                .disable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING)
                .build();
        EnumMap<LC749Enum, String> m = new EnumMap<LC749Enum, String>(LC749Enum.class);
        m.put(LC749Enum.A, "value");
        assertEquals("{\"A\":\"value\"}", mapper.writeValueAsString(m));
    }

    @Test
    public void testEnumMapSerDisableToString() throws Exception {
        final ObjectMapper mapper = newJsonMapper();
        ObjectWriter w = mapper.writer().without(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
        EnumMap<LC749Enum, String> m = new EnumMap<LC749Enum, String>(LC749Enum.class);
        m.put(LC749Enum.A, "value");
        assertEquals("{\"A\":\"value\"}", w.writeValueAsString(m));
    }

    @Test
    public void testEnumMapSerEnableToString() throws Exception {
        final ObjectMapper mapper = newJsonMapper();
        ObjectWriter w = mapper.writer().with(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
        EnumMap<LC749Enum, String> m = new EnumMap<LC749Enum, String>(LC749Enum.class);
        m.put(LC749Enum.A, "value");
        assertEquals("{\"a\":\"value\"}", w.writeValueAsString(m));
    }

    // [databind#1322]
    @Test
    public void testEnumsWithJsonProperty() throws Exception {
        assertEquals(q("aleph"), MAPPER.writeValueAsString(EnumWithJsonProperty.A));
    }

    @Test
    public void testEnumsWithJsonPropertyEnableToString() throws Exception {
        String result = MAPPER.writerFor(EnumWithJsonProperty.class)
                .with(SerializationFeature.WRITE_ENUMS_USING_TO_STRING)
                .writeValueAsString(EnumWithJsonProperty.A);
        assertEquals(q("aleph"), result);
    }

    // [databind#1535]
    @Test
    public void testEnumKeysWithJsonProperty() throws Exception {
        Map<EnumWithJsonProperty,Integer> input = new HashMap<EnumWithJsonProperty,Integer>();
        input.put(EnumWithJsonProperty.A, 13);
        assertEquals(a2q("{'aleph':13}"), MAPPER.writeValueAsString(input));
    }

    // [databind#1322]
    @Test
    public void testEnumsWithJsonPropertyInSet() throws Exception
    {
        assertEquals("[\"aleph\"]",
                MAPPER.writeValueAsString(EnumSet.of(EnumWithJsonProperty.A)));
    }

    // [databind#1322]
    @Test
    public void testEnumsWithJsonPropertyAsKey() throws Exception
    {
        EnumMap<EnumWithJsonProperty,String> input = new EnumMap<EnumWithJsonProperty,String>(EnumWithJsonProperty.class);
        input.put(EnumWithJsonProperty.A, "b");
        assertEquals("{\"aleph\":\"b\"}", MAPPER.writeValueAsString(input));
    }

    // [databind#2871]
    @Test
    public void testEnumWithJsonKey() throws Exception
    {
        // First with EnumMap
        EnumMap<EnumWithJsonKey, EnumWithJsonKey> input1 = new EnumMap<>(EnumWithJsonKey.class);
        input1.put(EnumWithJsonKey.A, EnumWithJsonKey.B);
        assertEquals(a2q("{'key:a':'value:b'}"), MAPPER.writeValueAsString(input1));

        // Then regular Map with Enums
        Map<EnumWithJsonKey, EnumWithJsonKey> input2
            = Collections.singletonMap(EnumWithJsonKey.A, EnumWithJsonKey.B);
        assertEquals(a2q("{'key:a':'value:b'}"), MAPPER.writeValueAsString(input2));
    }

    // [databind#3053]
    @Test
    public void testEnumFeature_WRITE_ENUMS_TO_LOWERCASE_isDisabledByDefault() {
        ObjectReader READER = MAPPER.reader();
        assertFalse(READER.isEnabled(EnumFeature.WRITE_ENUMS_TO_LOWERCASE));
        assertFalse(READER.without(EnumFeature.WRITE_ENUMS_TO_LOWERCASE)
            .isEnabled(EnumFeature.WRITE_ENUMS_TO_LOWERCASE));
    }

    @Test
    public void testEnumFeature_WRITE_ENUMS_TO_LOWERCASE() throws Exception {
        ObjectMapper m = jsonMapperBuilder()
            .configure(EnumFeature.WRITE_ENUMS_TO_LOWERCASE, true)
            .build();
        assertEquals(q("b"), m.writeValueAsString(TestEnum.B));
        // NOTE: cannot be dynamically changed
    }
}

// [JACKSON-757], non-inner enum
enum NOT_OK2 {
    V2("v2");
    protected String key;
    // any runtime-persistent annotation is fine
    NOT_OK2(@JsonProperty String key) { this.key = key; }
}
