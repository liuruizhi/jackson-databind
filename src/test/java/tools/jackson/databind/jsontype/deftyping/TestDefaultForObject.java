package tools.jackson.databind.jsontype.deftyping;

import java.util.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import tools.jackson.core.*;
import tools.jackson.databind.*;
import tools.jackson.databind.exc.InvalidDefinitionException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;
import tools.jackson.databind.testutil.DatabindTestUtil;
import tools.jackson.databind.testutil.NoCheckSubTypeValidator;
import tools.jackson.databind.util.TokenBuffer;

import static org.junit.jupiter.api.Assertions.*;

public class TestDefaultForObject
    extends DatabindTestUtil
{
    static abstract class AbstractBean { }

    static class StringBean extends AbstractBean { // ha, punny!
        public String name;

        public StringBean() { this(null); }
        protected StringBean(String n)  { name = n; }
    }

    final static class FinalStringBean extends StringBean {
        protected FinalStringBean() { this(null); }
        public FinalStringBean(String n)  { super(n); }
    }

    enum Choice { YES, NO; }

    /**
     * Another enum type, but this time forcing sub-classing
     */
    enum ComplexChoice {
        MAYBE(true), PROBABLY_NOT(false);

        private boolean state;

        private ComplexChoice(boolean b) { state = b; }

        @Override
        public String toString() { return String.valueOf(state); }
    }

    static class PolymorphicType {
        public String foo;
        public Object bar;

        public PolymorphicType() { }
        public PolymorphicType(String foo, int bar) {
            this.foo = foo;
            this.bar = bar;
        }
    }

    final static class BeanHolder
    {
        public AbstractBean bean;

        public BeanHolder() { }
        public BeanHolder(AbstractBean b) { bean = b; }
    }

    final static class ObjectHolder
    {
        public Object value;

        public ObjectHolder() { }
        public ObjectHolder(Object v) { value = v; }
    }

    static class DomainBean {
        public int weight;
    }

    static class DiscussBean extends DomainBean {
        public String subject;
    }

    static class DomainBeanWrapper {
        public String name;
        public Object myBean;
    }

    @SuppressWarnings("serial")
    static class BlockAllPTV extends PolymorphicTypeValidator.Base
    {
        @Override
        public Validity validateBaseType(DatabindContext ctxt, JavaType baseType) {
            return Validity.DENIED;
        }
    }

    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    private final ObjectMapper MAPPER = jsonMapperBuilder()
            .disable(DeserializationFeature.READ_ENUMS_USING_TO_STRING)
            .disable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING)
            .build();

    /**
     * Unit test that verifies that a bean is stored with type information,
     * when declared type is <code>Object.class</code> (since it is within
     * Object[]), and default type information is enabled.
     */
    @Test
    public void testBeanAsObject() throws Exception
    {
        ObjectMapper mapper = jsonMapperBuilder()
                .activateDefaultTyping(NoCheckSubTypeValidator.instance)
                .build();
        // note: need to wrap, to get declared as Object
        String str = mapper.writeValueAsString(new Object[] { new StringBean("abc") });

        _verifySerializationAsMap(str);

        // Ok: serialization seems to work as expected. Now deserialize:
        Object ob = mapper.readValue(str, Object[].class);
        assertNotNull(ob);
        Object[] result = (Object[]) ob;
        assertNotNull(result[0]);
        assertEquals(StringBean.class, result[0].getClass());
        assertEquals("abc", ((StringBean) result[0]).name);
    }

    // with 2.5, another test to check that "as-property" is valid option
    @Test
    public void testBeanAsObjectUsingAsProperty() throws Exception
    {
        ObjectMapper mapper = jsonMapperBuilder()
                .activateDefaultTypingAsProperty(NoCheckSubTypeValidator.instance,
                        DefaultTyping.NON_FINAL, ".hype")
                .build();
        // note: need to wrap, to get declared as Object
        String json = mapper.writeValueAsString(new StringBean("abc"));

        // Ok: serialization seems to work as expected. Now deserialize:
        Object result = mapper.readValue(json, Object.class);
        assertNotNull(result);
        assertEquals(StringBean.class, result.getClass());
        assertEquals("abc", ((StringBean) result).name);
    }

    // [databind#2840]: ensure "as-property" uses PTV passed
    @Test
    public void testAsPropertyWithPTV() throws Exception {
        ObjectMapper m = JsonMapper.builder()
                .activateDefaultTypingAsProperty(new BlockAllPTV(),
                        DefaultTyping.NON_FINAL,
                        "@classy")
                .build();
        String json = m.writeValueAsString(new StringBean("abc"));
        try {
            /*Object result =*/ m.readValue(json, Object.class);
            fail("Should not pass");
        } catch (InvalidDefinitionException e) {
            verifyException(e, "Configured `PolymorphicTypeValidator`");
            verifyException(e, "denied resolution of all subtypes of ");
        }
    }

    /**
     * Unit test that verifies that an abstract bean is stored with type information
     * if default type information is enabled for non-concrete types.
     */
    @Test
    public void testAbstractBean() throws Exception
    {
        // First, let's verify that we'd fail without enabling default type info
        ObjectMapper m = new ObjectMapper();
        AbstractBean[] input = new AbstractBean[] { new StringBean("xyz") };
        String serial = m.writeValueAsString(input);
        try {
            m.readValue(serial, AbstractBean[].class);
            fail("Should have failed");
        } catch (InvalidDefinitionException e) {
            verifyException(e, "cannot construct instance of");
        }
        // and then that we will succeed with default type info
        m = jsonMapperBuilder()
                .activateDefaultTyping(NoCheckSubTypeValidator.instance,
                        DefaultTyping.OBJECT_AND_NON_CONCRETE)
                .build();
        serial = m.writeValueAsString(input);
        AbstractBean[] beans = m.readValue(serial, AbstractBean[].class);
        assertEquals(1, beans.length);
        assertEquals(StringBean.class, beans[0].getClass());
        assertEquals("xyz", ((StringBean) beans[0]).name);
    }

    /**
     * Unit test to verify that type information is included for
     * all non-final types, if default typing suitably configured
     */
    @Test
    public void testNonFinalBean() throws Exception
    {
        // first: use "object or abstract" typing: should produce no type info:
        ObjectMapper m = jsonMapperBuilder()
                .activateDefaultTyping(NoCheckSubTypeValidator.instance,
                        DefaultTyping.OBJECT_AND_NON_CONCRETE)
                .build();
        StringBean bean = new StringBean("x");
        assertEquals("{\"name\":\"x\"}", m.writeValueAsString(bean));
        // then non-final, and voila:
        m = jsonMapperBuilder()
                .activateDefaultTyping(NoCheckSubTypeValidator.instance,
                        DefaultTyping.NON_FINAL)
                .build();
        assertEquals("[\""+StringBean.class.getName()+"\",{\"name\":\"x\"}]",
            m.writeValueAsString(bean));
    }

    @Test
    public void testNullValue() throws Exception
    {
        ObjectMapper m = jsonMapperBuilder()
                .activateDefaultTyping(NoCheckSubTypeValidator.instance,
                        DefaultTyping.NON_FINAL)
                .build();
        BeanHolder h = new BeanHolder();
        String json = m.writeValueAsString(h);
        assertNotNull(json);
        BeanHolder result = m.readValue(json, BeanHolder.class);
        assertNotNull(result);
        assertNull(result.bean);
    }

    @Test
    public void testEnumAsObject() throws Exception
    {
        // wrapping to be declared as object
        Object[] input = new Object[] { Choice.YES };
        Object[] input2 = new Object[] { ComplexChoice.MAYBE};
        // first, without type info:
        assertEquals("[\"YES\"]", MAPPER.writeValueAsString(input));
        assertEquals("[\"MAYBE\"]", MAPPER.writeValueAsString(input2));

        // and then with it
        ObjectMapper m = jsonMapperBuilder()
                .disable(DeserializationFeature.READ_ENUMS_USING_TO_STRING)
                .disable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING)
                .activateDefaultTyping(NoCheckSubTypeValidator.instance)
                .build();
        String json = m.writeValueAsString(input);
        assertEquals("[[\""+Choice.class.getName()+"\",\"YES\"]]", json);

        // which we should get back same way
        Object[] output = m.readValue(json, Object[].class);
        assertEquals(1, output.length);
        assertEquals(Choice.YES, output[0]);

        // ditto for more complicated enum
        json = m.writeValueAsString(input2);
        assertEquals("[[\""+ComplexChoice.class.getName()+"\",\"MAYBE\"]]", json);
        output = m.readValue(json, Object[].class);
        assertEquals(1, output.length);
        assertEquals(ComplexChoice.MAYBE, output[0]);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testEnumSet() throws Exception
    {
        EnumSet<Choice> set = EnumSet.of(Choice.NO);
        Object[] input = new Object[] { set };
        ObjectMapper m = jsonMapperBuilder()
                .activateDefaultTyping(NoCheckSubTypeValidator.instance)
                .build();
        String json = m.writeValueAsString(input);
        Object[] output = m.readValue(json, Object[].class);
        assertEquals(1, output.length);
        Object ob = output[0];
        assertTrue(ob instanceof EnumSet<?>);
        EnumSet<Choice> set2 = (EnumSet<Choice>) ob;
        assertEquals(1, set2.size());
        assertTrue(set2.contains(Choice.NO));
        assertFalse(set2.contains(Choice.YES));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testEnumMap() throws Exception
    {
        EnumMap<Choice,String> map = new EnumMap<Choice,String>(Choice.class);
        map.put(Choice.NO, "maybe");
        Object[] input = new Object[] { map };
        ObjectMapper m = jsonMapperBuilder()
                .activateDefaultTyping(NoCheckSubTypeValidator.instance)
                .build();
        String json = m.writeValueAsString(input);
        Object[] output = m.readValue(json, Object[].class);
        assertEquals(1, output.length);
        Object ob = output[0];
        assertTrue(ob instanceof EnumMap<?,?>);
        EnumMap<Choice,String> map2 = (EnumMap<Choice,String>) ob;
        assertEquals(1, map2.size());
        assertEquals("maybe", map2.get(Choice.NO));
        assertNull(map2.get(Choice.YES));
    }

    @Test
    public void testJackson311() throws Exception
    {
        ObjectMapper mapper = jsonMapperBuilder()
                .activateDefaultTyping(NoCheckSubTypeValidator.instance,
                        DefaultTyping.NON_FINAL)
                .build();
        String json = mapper.writeValueAsString(new PolymorphicType("hello", 2));
        PolymorphicType value = mapper.readValue(json, PolymorphicType.class);
        assertEquals("hello", value.foo);
        assertEquals(Integer.valueOf(2), value.bar);
    }

    // Also, let's ensure TokenBuffer gets properly handled
    @Test
    public void testTokenBuffer() throws Exception
    {
        ObjectMapper mapper = jsonMapperBuilder()
                .activateDefaultTyping(NoCheckSubTypeValidator.instance,
                        DefaultTyping.NON_FINAL)
                .build();
        // Ok, first test JSON Object containing buffer:
        TokenBuffer buf = TokenBuffer.forGeneration();
        buf.writeStartObject();
        buf.writeNumberProperty("num", 42);
        buf.writeEndObject();
        String json = mapper.writeValueAsString(new ObjectHolder(buf));
        ObjectHolder holder = mapper.readValue(json, ObjectHolder.class);
        assertNotNull(holder.value);
        assertSame(TokenBuffer.class, holder.value.getClass());
        JsonParser jp = ((TokenBuffer) holder.value).asParser(ObjectReadContext.empty());
        assertToken(JsonToken.START_OBJECT, jp.nextToken());
        assertToken(JsonToken.PROPERTY_NAME, jp.nextToken());
        assertToken(JsonToken.VALUE_NUMBER_INT, jp.nextToken());
        assertToken(JsonToken.END_OBJECT, jp.nextToken());
        assertNull(jp.nextToken());
        jp.close();
        buf.close();

        // then as an array:
        buf = TokenBuffer.forGeneration();
        buf.writeStartArray();
        buf.writeBoolean(true);
        buf.writeEndArray();
        json = mapper.writeValueAsString(new ObjectHolder(buf));
        holder = mapper.readValue(json, ObjectHolder.class);
        assertNotNull(holder.value);
        assertSame(TokenBuffer.class, holder.value.getClass());
        jp = ((TokenBuffer) holder.value).asParser(ObjectReadContext.empty());
        assertToken(JsonToken.START_ARRAY, jp.nextToken());
        assertToken(JsonToken.VALUE_TRUE, jp.nextToken());
        assertToken(JsonToken.END_ARRAY, jp.nextToken());
        assertNull(jp.nextToken());
        jp.close();
        buf.close();

        // and finally as scalar
        buf = TokenBuffer.forGeneration();
        buf.writeNumber(321);
        json = mapper.writeValueAsString(new ObjectHolder(buf));
        holder = mapper.readValue(json, ObjectHolder.class);
        assertNotNull(holder.value);
        assertSame(TokenBuffer.class, holder.value.getClass());
        jp = ((TokenBuffer) holder.value).asParser(ObjectReadContext.empty());
        assertToken(JsonToken.VALUE_NUMBER_INT, jp.nextToken());
        assertEquals(321, jp.getIntValue());
        assertNull(jp.nextToken());
        jp.close();
        buf.close();
    }

    @Test
    public void testIssue352() throws Exception
    {
        ObjectMapper mapper = jsonMapperBuilder()
                .activateDefaultTyping(NoCheckSubTypeValidator.instance,
                        DefaultTyping.OBJECT_AND_NON_CONCRETE, JsonTypeInfo.As.PROPERTY)
                .build();
        DiscussBean d1 = new DiscussBean();
        d1.subject = "mouse";
        d1.weight=88;
        DomainBeanWrapper wrapper = new DomainBeanWrapper();
        wrapper.name = "mickey";
        wrapper.myBean = d1;
        String json = mapper.writeValueAsString(wrapper);
        DomainBeanWrapper result = mapper.readValue(json, DomainBeanWrapper.class);
        assertNotNull(result);
        assertNotNull(wrapper.myBean);
        assertSame(DiscussBean.class, wrapper.myBean.getClass());
    }

    // Test to ensure we can also use "As.PROPERTY" inclusion and custom property name
    @Test
    public void testFeature432() throws Exception
    {
        ObjectMapper mapper = jsonMapperBuilder()
                .activateDefaultTypingAsProperty(NoCheckSubTypeValidator.instance,
                        DefaultTyping.OBJECT_AND_NON_CONCRETE, "*CLASS*")
                .build();
        String json = mapper.writeValueAsString(new BeanHolder(new StringBean("punny")));
        assertEquals("{\"bean\":{\"*CLASS*\":\"tools.jackson.databind.jsontype.deftyping.TestDefaultForObject$StringBean\",\"name\":\"punny\"}}", json);
    }

    @Test
    public void testNoGoWithExternalProperty() throws Exception
    {
        try {
            /*ObjectMapper mapper =*/ jsonMapperBuilder()
                    .activateDefaultTyping(NoCheckSubTypeValidator.instance,
                            DefaultTyping.JAVA_LANG_OBJECT, JsonTypeInfo.As.EXTERNAL_PROPERTY)
                    .build();
            fail("Should not have passed");
        } catch (IllegalArgumentException e) {
            verifyException(e, "Cannot use includeAs of EXTERNAL_PROPERTY");
        }
    }

    // [databind#2349]
    @Test
    public void testWithFinalClass_NonFinal() throws Exception
    {
        // First: type info NOT included
        ObjectMapper mapper = jsonMapperBuilder()
                .activateDefaultTyping(NoCheckSubTypeValidator.instance,
                        DefaultTyping.NON_FINAL)
                .build();
        assertEquals(a2q("{'name':'abc'}"),
                mapper.writeValueAsString(new FinalStringBean("abc")));

        // 23-Oct-2023, tatu: [databind#4160] Remove "EVERYTHING" option
        /*
        mapper = jsonMapperBuilder()
                .activateDefaultTyping(NoCheckSubTypeValidator.instance,
                        DefaultTyping.EVERYTHING)
                .build();
        assertEquals(a2q("['"+FinalStringBean.class.getName()+"',{'name':'abc'}]"),
                mapper.writeValueAsString(new FinalStringBean("abc")));
                */
    }

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */

    @SuppressWarnings("unchecked")
    private void _verifySerializationAsMap(String str) throws Exception
    {
        // First: validate that structure looks correct (as Map etc)
        // note: should look something like:
        // "[["org.codehaus.jackson.map.jsontype.TestDefaultForObject$StringBean",{"name":"abc"}]]")

        // note: must have default mapper, default typer NOT enabled (to get 'plain' map)
        ObjectMapper m = newJsonMapper();
        List<Object> list = m.readValue(str, List.class);
        assertEquals(1, list.size()); // no type for main List, just single entry
        Object entryOb = list.get(0);
        assertTrue(entryOb instanceof List<?>);
        // but then type wrapper for bean
        List<?> entryList = (List<?>)entryOb;
        assertEquals(2, entryList.size());
        assertEquals(StringBean.class.getName(), entryList.get(0));
        assertTrue(entryList.get(1) instanceof Map);
        Map<?,?> map = (Map<?,?>) entryList.get(1);
        assertEquals(1, map.size());
        assertEquals("abc", map.get("name"));
    }
}
