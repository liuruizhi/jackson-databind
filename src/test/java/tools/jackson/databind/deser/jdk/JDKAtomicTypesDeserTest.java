package tools.jackson.databind.deser.jdk;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.*;

import tools.jackson.core.JsonParser;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.*;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.deser.std.StdScalarDeserializer;

import static org.junit.jupiter.api.Assertions.*;

import static tools.jackson.databind.testutil.DatabindTestUtil.a2q;
import static tools.jackson.databind.testutil.DatabindTestUtil.jsonMapperBuilder;
import static tools.jackson.databind.testutil.DatabindTestUtil.newJsonMapper;

public class JDKAtomicTypesDeserTest
{
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
    @JsonSubTypes({ @JsonSubTypes.Type(Impl.class) })
    static abstract class Base { }

    @JsonTypeName("I")
    static class Impl extends Base {
        public int value;

        public Impl() { }
        public Impl(int v) { value = v; }
    }

    static class RefWrapper
    {
        public AtomicReference<Base> w;

        public RefWrapper() { }
        public RefWrapper(Base b) {
            w = new AtomicReference<Base>(b);
        }
        public RefWrapper(int i) {
            w = new AtomicReference<Base>(new Impl(i));
        }
    }

    static class SimpleWrapper {
        public AtomicReference<Object> value;

        public SimpleWrapper(Object o) { value = new AtomicReference<Object>(o); }
    }

    static class RefiningWrapper {
        @JsonDeserialize(contentAs=BigDecimal.class)
        public AtomicReference<Serializable> value;
    }

    // Additional tests for improvements with [databind#932]

    static class UnwrappingRefParent {
        @JsonUnwrapped(prefix = "XX.")
        public AtomicReference<Child> child = new AtomicReference<Child>(new Child());
    }

    static class Child {
        public String name = "Bob";
    }

    static class Parent {
        private Child child = new Child();

        @JsonUnwrapped
        public Child getChild() {
             return child;
        }
    }

    static class WrappedString {
        String value;

        public WrappedString(String s) { value = s; }
    }

    static class AtomicRefReadWrapper {
        @JsonDeserialize(contentAs=WrappedString.class)
        public AtomicReference<Object> value;
    }

    static class LowerCasingDeserializer extends StdScalarDeserializer<String>
    {
        public LowerCasingDeserializer() { super(String.class); }

        @Override
        public String deserialize(JsonParser p, DeserializationContext ctxt) {
            return p.getString().toLowerCase();
        }
    }

    static class LCStringWrapper {
        @JsonDeserialize(contentUsing=LowerCasingDeserializer.class)
        public AtomicReference<String> value;

        public LCStringWrapper() { }
    }

    @JsonPropertyOrder({ "a", "b" })
    static class Issue1256Bean {
        @JsonSerialize(as=AtomicReference.class)
        public Object a = new AtomicReference<Object>();
        public AtomicReference<Object> b = new AtomicReference<Object>();
    }

    // [databind#2303]
    static class MyBean2303 {
        public AtomicReference<AtomicReference<Integer>> refRef;
    }

    // [modules-java8#214]
    static class ListWrapper {
        @JsonMerge
        public AtomicReference<List<String>> list = new AtomicReference<>();
    }

    static class AtomicRefBean {
        protected AtomicReference<String> _atomic;

        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public AtomicRefBean(@JsonProperty("atomic") AtomicReference<String> ref) {
            _atomic = ref;
        }
    }

    static class AtomicRefBeanWithEmpty {
        protected AtomicReference<String> _atomic;

        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public AtomicRefBeanWithEmpty(@JsonProperty("atomic")
            @JsonSetter(nulls = Nulls.AS_EMPTY)
            AtomicReference<String> ref) {
            _atomic = ref;
        }
    }

    static class AtomicRefWithNodeBean {
        protected AtomicReference<JsonNode> _atomicNode;

        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public AtomicRefWithNodeBean(@JsonProperty("atomic") AtomicReference<JsonNode> ref) {
            _atomicNode = ref;
        }
    }

    /*
    /**********************************************************************
    /* Test methods
    /**********************************************************************
     */

    private final ObjectMapper MAPPER = newJsonMapper();

    @Test
    public void testAtomicBoolean() throws Exception
    {
        AtomicBoolean b = MAPPER.readValue("true", AtomicBoolean.class);
        assertTrue(b.get());
    }

    @Test
    public void testAtomicInt() throws Exception
    {
        AtomicInteger value = MAPPER.readValue("13", AtomicInteger.class);
        assertEquals(13, value.get());
    }

    @Test
    public void testAtomicLong() throws Exception
    {
        AtomicLong value = MAPPER.readValue("12345678901", AtomicLong.class);
        assertEquals(12345678901L, value.get());
    }

    @Test
    public void testAtomicReference() throws Exception
    {
        AtomicReference<long[]> value = MAPPER.readValue("[1,2]",
                new TypeReference<AtomicReference<long[]>>() { });
        Object ob = value.get();
        assertNotNull(ob);
        assertEquals(long[].class, ob.getClass());
        long[] longs = (long[]) ob;
        assertNotNull(longs);
        assertEquals(2, longs.length);
        assertEquals(1, longs[0]);
        assertEquals(2, longs[1]);
    }

    // for [databind#811]
    @Test
    public void testAbsentExclusion() throws Exception
    {
        ObjectMapper mapper = jsonMapperBuilder()
                .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_ABSENT))
                .build();
        assertEquals(a2q("{'value':true}"),
                mapper.writeValueAsString(new SimpleWrapper(Boolean.TRUE)));
        assertEquals(a2q("{}"),
                mapper.writeValueAsString(new SimpleWrapper(null)));
    }

    @Test
    public void testSerPropInclusionAlways() throws Exception
    {
        ObjectMapper mapper = jsonMapperBuilder()
                .changeDefaultPropertyInclusion(
                        i -> JsonInclude.Value.construct(JsonInclude.Include.NON_ABSENT, JsonInclude.Include.ALWAYS))
                .build();
        assertEquals(a2q("{'value':true}"),
                mapper.writeValueAsString(new SimpleWrapper(Boolean.TRUE)));
    }

    @Test
    public void testSerPropInclusionNonNull() throws Exception
    {
        ObjectMapper mapper = jsonMapperBuilder()
                .changeDefaultPropertyInclusion(
                        i -> JsonInclude.Value.construct(JsonInclude.Include.NON_ABSENT, JsonInclude.Include.NON_NULL))
                .build();
        assertEquals(a2q("{'value':true}"),
                mapper.writeValueAsString(new SimpleWrapper(Boolean.TRUE)));
    }

    @Test
    public void testSerPropInclusionNonAbsent() throws Exception
    {
        ObjectMapper mapper = jsonMapperBuilder()
                .changeDefaultPropertyInclusion(
                        i -> JsonInclude.Value.construct(JsonInclude.Include.NON_ABSENT, JsonInclude.Include.NON_ABSENT))
                .build();
        assertEquals(a2q("{'value':true}"),
                mapper.writeValueAsString(new SimpleWrapper(Boolean.TRUE)));
    }

    @Test
    public void testSerPropInclusionNonEmpty() throws Exception
    {
        ObjectMapper mapper = jsonMapperBuilder()
                .changeDefaultPropertyInclusion(
                        i -> JsonInclude.Value.construct(JsonInclude.Include.NON_ABSENT, JsonInclude.Include.NON_EMPTY))
                .build();
        assertEquals(a2q("{'value':true}"),
                mapper.writeValueAsString(new SimpleWrapper(Boolean.TRUE)));
    }

    // [databind#340]
    @Test
    public void testPolymorphicAtomicReference() throws Exception
    {
        RefWrapper input = new RefWrapper(13);
        String json = MAPPER.writeValueAsString(input);

        RefWrapper result = MAPPER.readValue(json, RefWrapper.class);
        assertNotNull(result.w);
        Object ob = result.w.get();
        assertEquals(Impl.class, ob.getClass());
        assertEquals(13, ((Impl) ob).value);
    }

    // [databind#740]
    @Test
    public void testFilteringOfAtomicReference() throws Exception
    {
        SimpleWrapper input = new SimpleWrapper(null);
        ObjectMapper mapper = MAPPER;

        // by default, include as null
        assertEquals(a2q("{'value':null}"), mapper.writeValueAsString(input));

        // ditto with "no nulls"
        mapper = jsonMapperBuilder()
                .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
                .build();
        assertEquals(a2q("{'value':null}"), mapper.writeValueAsString(input));

        // but not with "non empty"
        mapper = jsonMapperBuilder()
                .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_EMPTY))
                .build();
        assertEquals("{}", mapper.writeValueAsString(input));
    }

    @Test
    public void testTypeRefinement() throws Exception
    {
        RefiningWrapper input = new RefiningWrapper();
        BigDecimal bd = new BigDecimal("0.25");
        input.value = new AtomicReference<Serializable>(bd);
        String json = MAPPER.writeValueAsString(input);

        // so far so good. But does it come back as expected?
        RefiningWrapper result = MAPPER.readValue(json, RefiningWrapper.class);
        assertNotNull(result.value);
        Object ob = result.value.get();
        assertEquals(BigDecimal.class, ob.getClass());
        assertEquals(bd, ob);
    }

    // [databind#882]: verify `@JsonDeserialize(contentAs=)` works with AtomicReference
    @Test
    public void testDeserializeWithContentAs() throws Exception
    {
        AtomicRefReadWrapper result = MAPPER.readValue(a2q("{'value':'abc'}"),
                AtomicRefReadWrapper.class);
         Object v = result.value.get();
         assertNotNull(v);
         assertEquals(WrappedString.class, v.getClass());
         assertEquals("abc", ((WrappedString)v).value);
    }

    // [databind#932]: support unwrapping too
    @Test
    public void testWithUnwrapping() throws Exception
    {
         String jsonExp = a2q("{'XX.name':'Bob'}");
         String jsonAct = MAPPER.writeValueAsString(new UnwrappingRefParent());
         assertEquals(jsonExp, jsonAct);
    }

    @Test
    public void testWithCustomDeserializer() throws Exception
    {
        LCStringWrapper w = MAPPER.readValue(a2q("{'value':'FoobaR'}"),
                LCStringWrapper.class);
        assertEquals("foobar", w.value.get());
    }

    @Test
    public void testEmpty1256() throws Exception
    {
        ObjectMapper mapper = jsonMapperBuilder()
                .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_ABSENT))
                .build();
        assertEquals("{}", mapper.writeValueAsString(new Issue1256Bean()));
    }

    // [databind#1307]
    @SuppressWarnings("unchecked")
    @Test
    public void testNullValueHandling() throws Exception
    {
        AtomicReference<Double> inputData = new AtomicReference<Double>();
        String json = MAPPER.writeValueAsString(inputData);
        AtomicReference<Double> readData = (AtomicReference<Double>) MAPPER.readValue(json, AtomicReference.class);
        assertNotNull(readData);
        assertNull(readData.get());
    }

    // [databind#2303]
    @Test
    public void testNullWithinNested() throws Exception
    {
        final ObjectReader r = MAPPER.readerFor(MyBean2303.class);
        MyBean2303 intRef = r.readValue(" {\"refRef\": 2 } ");
        assertNotNull(intRef.refRef);
        assertNotNull(intRef.refRef.get());
        assertEquals(intRef.refRef.get().get(), Integer.valueOf(2));

        MyBean2303 nullRef = r.readValue(" {\"refRef\": null } ");
        assertNotNull(nullRef.refRef);
        assertNotNull(nullRef.refRef.get());
        assertNull(nullRef.refRef.get().get());
    }

    // for [modules-java8#214]: ReferenceType of List, merge
    @Test
    public void testMergeToListViaRef() throws Exception
    {
        ListWrapper base = MAPPER.readValue(a2q("{'list':['a']}"),
                ListWrapper.class);
        assertNotNull(base.list);
        assertEquals(Arrays.asList("a"), base.list.get());

        ListWrapper merged = MAPPER.readerForUpdating(base)
                .readValue(a2q("{'list':['b']}"));
        assertSame(base, merged);
        assertEquals(Arrays.asList("a", "b"), base.list.get());
    }

    // Verify expected behavior of AtomicReference wrt nulls, absent
    // values.
    //
    // @since 2.14
    @Test
    public void testAbsentAtomicRefViaCreator() throws Exception
    {
        AtomicRefBean bean;

        // First: null should become empty, non-null reference
        bean = MAPPER.readValue(a2q("{'atomic':null}"), AtomicRefBean.class);
        assertNotNull(bean._atomic);
        assertNull(bean._atomic.get());

        // And then absent (missing), via Creator method, should become actual null
        bean = MAPPER.readValue("{}", AtomicRefBean.class);
        assertNull(bean._atomic);

        // Except that we can override handling to produce empty
        AtomicRefBeanWithEmpty bean2 = MAPPER.readValue("{}", AtomicRefBeanWithEmpty.class);
        assertNotNull(bean2._atomic);
        assertNull(bean2._atomic.get());
    }

    // @since 2.14
    @Test
    public void testAtomicRefWithNodeViaCreator() throws Exception
    {
        AtomicRefWithNodeBean bean;

        // Somewhat usual, `null` SHOULD become `NullNode`
        bean = MAPPER.readValue(a2q("{'atomic':null}"), AtomicRefWithNodeBean.class);
        assertNotNull(bean._atomicNode);
        assertNotNull(bean._atomicNode.get());
        JsonNode n = bean._atomicNode.get();
        assertTrue(n.isNull());

        // And then absent (missing), via Creator method, should become actual null
        bean = MAPPER.readValue("{}", AtomicRefWithNodeBean.class);
        assertNull(bean._atomicNode);
    }
}
