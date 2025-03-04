package tools.jackson.databind.deser.creators;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.core.Version;
import tools.jackson.databind.*;
import tools.jackson.databind.annotation.JsonValueInstantiator;
import tools.jackson.databind.deser.*;
import tools.jackson.databind.exc.InvalidDefinitionException;
import tools.jackson.databind.introspect.AnnotatedWithParams;
import tools.jackson.databind.module.SimpleModule;

import static org.junit.jupiter.api.Assertions.*;

import static tools.jackson.databind.testutil.DatabindTestUtil.*;

/**
 * Test custom value instantiators.
 */
@SuppressWarnings("serial")
public class ValueInstantiatorTest
{
    static class MyBean
    {
        String _secret;

        // 20-Sep-2017, tatu: Must NOT be public for 3.x because we do auto-detect
        //   public ctors....
        protected MyBean(String s, boolean bogus) {
            _secret = s;
        }
    }

    static class MysteryBean
    {
        Object value;

        public MysteryBean(Object v) { value = v; }
    }

    static class CreatorBean
    {
        String _secret;

        public String value;

        protected CreatorBean(String s) {
            _secret = s;
        }
    }

    static abstract class InstantiatorBase extends ValueInstantiator.Base
    {
        public InstantiatorBase() {
            super(Object.class);
        }

        @Override
        public String getValueTypeDesc() {
            return "UNKNOWN";
        }

        @Override
        public boolean canCreateUsingDelegate() { return false; }
    }

    static abstract class PolymorphicBeanBase { }

    static class PolymorphicBean extends PolymorphicBeanBase
    {
        public String name;
    }

    static class MyList extends ArrayList<Object>
    {
        public MyList(boolean b) { super(); }
    }

    static class MyMap extends HashMap<String,Object>
    {
        public MyMap(boolean b) { super(); }
        public MyMap(String name) {
            super();
            put(name, name);
        }
    }

    static class MyBeanInstantiator extends InstantiatorBase
    {
        @Override
        public String getValueTypeDesc() {
            return MyBean.class.getName();
        }

        @Override
        public boolean canCreateUsingDefault() { return true; }

        @Override
        public MyBean createUsingDefault(DeserializationContext ctxt) {
            return new MyBean("secret!", true);
        }
    }

    /**
     * Something more ambitious: semi-automated approach to polymorphic
     * deserialization, using ValueInstantiator; from Object to any
     * type...
     */
    static class PolymorphicBeanInstantiator extends InstantiatorBase
    {
        @Override
        public String getValueTypeDesc() {
            return Object.class.getName();
        }

        @Override
        public boolean canCreateFromObjectWith() { return true; }

        @Override
        public CreatorProperty[] getFromObjectArguments(DeserializationConfig config) {
            return  new CreatorProperty[] {
                    CreatorProperty.construct(new PropertyName("type"), config.constructType(Class.class), null,
                            null, null, null, 0, null,
                            PropertyMetadata.STD_REQUIRED)
            };
        }

        @Override
        public Object createFromObjectWith(DeserializationContext ctxt, Object[] args) {
            try {
                Class<?> cls = (Class<?>) args[0];
                return cls.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    static class CreatorMapInstantiator extends InstantiatorBase
    {
        @Override
        public String getValueTypeDesc() {
            return MyMap.class.getName();
        }

        @Override
        public boolean canCreateFromObjectWith() { return true; }

        @Override
        public CreatorProperty[] getFromObjectArguments(DeserializationConfig config) {
            return  new CreatorProperty[] {
                    CreatorProperty.construct(new PropertyName("name"), config.constructType(String.class), null,
                            null, null, null, 0, null,
                            PropertyMetadata.STD_REQUIRED)
            };
        }

        @Override
        public Object createFromObjectWith(DeserializationContext ctxt, Object[] args) {
            return new MyMap((String) args[0]);
        }
    }

    static class MyDelegateBeanInstantiator extends ValueInstantiator.Base
    {
        public MyDelegateBeanInstantiator() { super(Object.class); }

        @Override
        public String getValueTypeDesc() { return "xxx"; }

        @Override
        public boolean canCreateUsingDelegate() { return true; }

        @Override
        public JavaType getDelegateType(DeserializationConfig config) {
            return config.constructType(Object.class);
        }

        @Override
        public Object createUsingDelegate(DeserializationContext ctxt, Object delegate) {
            return new MyBean(""+delegate, true);
        }
    }

    static class MyListInstantiator extends InstantiatorBase
    {
        @Override
        public String getValueTypeDesc() {
            return MyList.class.getName();
        }

        @Override
        public boolean canCreateUsingDefault() { return true; }

        @Override
        public MyList createUsingDefault(DeserializationContext ctxt) {
            return new MyList(true);
        }
    }

    static class MyDelegateListInstantiator extends ValueInstantiator.Base
    {
        public MyDelegateListInstantiator() { super(Object.class); }

        @Override
        public String getValueTypeDesc() { return "xxx"; }

        @Override
        public boolean canCreateUsingDelegate() { return true; }

        @Override
        public JavaType getDelegateType(DeserializationConfig config) {
            return config.constructType(Object.class);
        }

        @Override
        public Object createUsingDelegate(DeserializationContext ctxt, Object delegate) {
            MyList list = new MyList(true);
            list.add(delegate);
            return list;
        }
    }

    static class MyMapInstantiator extends InstantiatorBase
    {
        @Override
        public String getValueTypeDesc() {
            return MyMap.class.getName();
        }

        @Override
        public boolean canCreateUsingDefault() { return true; }

        @Override
        public MyMap createUsingDefault(DeserializationContext ctxt) {
            return new MyMap(true);
        }
    }

    static class MyDelegateMapInstantiator extends ValueInstantiator.Base
    {
        public MyDelegateMapInstantiator() { super(Object.class); }

        @Override
        public String getValueTypeDesc() { return "xxx"; }

        @Override
        public boolean canCreateUsingDelegate() { return true; }

        @Override
        public JavaType getDelegateType(DeserializationConfig config) {
            return defaultTypeFactory().constructType(Object.class);
        }

        @Override
        public Object createUsingDelegate(DeserializationContext ctxt, Object delegate) {
            MyMap map = new MyMap(true);
            map.put("value", delegate);
            return map;
        }
    }

    @JsonValueInstantiator(AnnotatedBeanInstantiator.class)
    static class AnnotatedBean {
        protected final String a;
        protected final int b;

        public AnnotatedBean(String a, int b) {
            this.a = a;
            this.b = b;
        }
    }

    static class AnnotatedBeanInstantiator extends InstantiatorBase
    {
        @Override
        public String getValueTypeDesc() {
            return AnnotatedBean.class.getName();
        }

        @Override
        public boolean canCreateUsingDefault() { return true; }

        @Override
        public AnnotatedBean createUsingDefault(DeserializationContext ctxt) {
            return new AnnotatedBean("foo", 3);
        }
    }

    static class MyModule extends SimpleModule
    {
        public MyModule(Class<?> cls, ValueInstantiator inst)
        {
            super("Test", Version.unknownVersion());
            this.addValueInstantiator(cls, inst);
        }
    }

    @JsonValueInstantiator(AnnotatedBeanDelegatingInstantiator.class)
    static class AnnotatedBeanDelegating {
        protected final Object value;

        public AnnotatedBeanDelegating(Object v, boolean bogus) {
            value = v;
        }
    }

    static class AnnotatedBeanDelegatingInstantiator extends InstantiatorBase
    {
        @Override
        public String getValueTypeDesc() {
            return AnnotatedBeanDelegating.class.getName();
        }

        @Override
        public boolean canCreateUsingDelegate() { return true; }

        @Override
        public JavaType getDelegateType(DeserializationConfig config) {
            return config.constructType(Map.class);
        }

        @Override
        public AnnotatedWithParams getDelegateCreator() {
            return null;
        }

        @Override
        public Object createUsingDelegate(DeserializationContext ctxt, Object delegate) {
            return new AnnotatedBeanDelegating(delegate, false);
        }
    }

    /*
    /**********************************************************
    /* Unit tests for default creators
    /**********************************************************
     */

    private final ObjectMapper MAPPER = sharedMapper();

    @Test
    public void testCustomBeanInstantiator() throws Exception
    {
        ObjectMapper mapper = jsonMapperBuilder()
                .addModule(new MyModule(MyBean.class, new MyBeanInstantiator()))
                .build();
        MyBean bean = mapper.readValue("{}", MyBean.class);
        assertNotNull(bean);
        assertEquals("secret!", bean._secret);
    }

    @Test
    public void testCustomListInstantiator() throws Exception
    {
        ObjectMapper mapper = jsonMapperBuilder()
                .addModule(new MyModule(MyList.class, new MyListInstantiator()))
                .build();
        MyList result = mapper.readValue("[]", MyList.class);
        assertNotNull(result);
        assertEquals(MyList.class, result.getClass());
        assertEquals(0, result.size());
    }

    @Test
    public void testCustomMapInstantiator() throws Exception
    {
        ObjectMapper mapper = jsonMapperBuilder()
                .addModule(new MyModule(MyMap.class, new MyMapInstantiator()))
                .build();
        MyMap result = mapper.readValue("{ \"a\":\"b\" }", MyMap.class);
        assertNotNull(result);
        assertEquals(MyMap.class, result.getClass());
        assertEquals(1, result.size());
    }

    /*
    /**********************************************************
    /* Unit tests for delegate creators
    /**********************************************************
     */

    @Test
    public void testDelegateBeanInstantiator() throws Exception
    {
        ObjectMapper mapper = jsonMapperBuilder()
                .addModule(new MyModule(MyBean.class, new MyDelegateBeanInstantiator()))
                .build();
        MyBean bean = mapper.readValue("123", MyBean.class);
        assertNotNull(bean);
        assertEquals("123", bean._secret);
    }

    @Test
    public void testDelegateListInstantiator() throws Exception
    {
        ObjectMapper mapper = jsonMapperBuilder()
                .addModule(new MyModule(MyList.class, new MyDelegateListInstantiator()))
                .build();
        MyList result = mapper.readValue("123", MyList.class);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(Integer.valueOf(123), result.get(0));
    }

    @Test
    public void testDelegateMapInstantiator() throws Exception
    {
        ObjectMapper mapper = jsonMapperBuilder()
                .addModule(new MyModule(MyMap.class, new MyDelegateMapInstantiator()))
                .build();
        MyMap result = mapper.readValue("123", MyMap.class);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(Integer.valueOf(123), result.values().iterator().next());
    }

    @Test
    public void testCustomDelegateInstantiator() throws Exception
    {
        AnnotatedBeanDelegating value = MAPPER.readValue("{\"a\":3}", AnnotatedBeanDelegating.class);
        assertNotNull(value);
        Object ob = value.value;
        assertNotNull(ob);
        assertTrue(ob instanceof Map);
    }

    /*
    /**********************************************************
    /* Unit tests for property-based creators
    /**********************************************************
     */

    @Test
    public void testPropertyBasedBeanInstantiator() throws Exception
    {
        ObjectMapper mapper = jsonMapperBuilder()
                .addModule(new MyModule(CreatorBean.class,
                new InstantiatorBase() {
                    @Override
                    public boolean canCreateFromObjectWith() { return true; }

                    @Override
                    public CreatorProperty[] getFromObjectArguments(DeserializationConfig config) {
                        return  new CreatorProperty[] {
                                CreatorProperty.construct(new PropertyName("secret"), config.constructType(String.class), null,
                                        null, null, null, 0, null,
                                        PropertyMetadata.STD_REQUIRED)
                        };
                    }

                    @Override
                    public Object createFromObjectWith(DeserializationContext ctxt, Object[] args) {
                        return new CreatorBean((String) args[0]);
                    }
                }))
                .build();
        CreatorBean bean = mapper.readValue("{\"secret\":123,\"value\":37}", CreatorBean.class);
        assertNotNull(bean);
        assertEquals("123", bean._secret);
    }

    @Test
    public void testPropertyBasedMapInstantiator() throws Exception
    {
        ObjectMapper mapper = jsonMapperBuilder()
                .addModule(new MyModule(MyMap.class, new CreatorMapInstantiator()))
                .build();
        MyMap result = mapper.readValue("{\"name\":\"bob\", \"x\":\"y\"}", MyMap.class);
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("bob", result.get("bob"));
        assertEquals("y", result.get("x"));
    }

    /*
    /**********************************************************
    /* Unit tests for scalar-delegates
    /**********************************************************
     */

    @Test
    public void testBeanFromString() throws Exception
    {
        ObjectMapper mapper = jsonMapperBuilder()
                .addModule(new MyModule(MysteryBean.class,
                new InstantiatorBase() {
                    @Override
                    public boolean canCreateFromString() { return true; }

                    @Override
                    public Object createFromString(DeserializationContext ctxt, String value) {
                        return new MysteryBean(value);
                    }
                }))
                .build();
        MysteryBean result = mapper.readValue(q("abc"), MysteryBean.class);
        assertNotNull(result);
        assertEquals("abc", result.value);
    }

    @Test
    public void testBeanFromInt() throws Exception
    {
        ObjectMapper mapper = jsonMapperBuilder()
                .addModule(new MyModule(MysteryBean.class,
                new InstantiatorBase() {
                    @Override
                    public boolean canCreateFromInt() { return true; }

                    @Override
                    public Object createFromInt(DeserializationContext ctxt, int value) {
                        return new MysteryBean(value+1);
                    }
                }))
                .build();
        MysteryBean result = mapper.readValue("37", MysteryBean.class);
        assertNotNull(result);
        assertEquals(Integer.valueOf(38), result.value);
    }

    @Test
    public void testBeanFromLong() throws Exception
    {
        ObjectMapper mapper = jsonMapperBuilder()
                .addModule(new MyModule(MysteryBean.class,
                new InstantiatorBase() {
                    @Override
                    public boolean canCreateFromLong() { return true; }

                    @Override
                    public Object createFromLong(DeserializationContext ctxt, long value) {
                        return new MysteryBean(value+1L);
                    }
                }))
                .build();
        MysteryBean result = mapper.readValue("9876543210", MysteryBean.class);
        assertNotNull(result);
        assertEquals(Long.valueOf(9876543211L), result.value);
    }

    @Test
    public void testBeanFromDouble() throws Exception
    {
        ObjectMapper mapper = jsonMapperBuilder()
                .addModule(new MyModule(MysteryBean.class,
                new InstantiatorBase() {
                    @Override
                    public boolean canCreateFromDouble() { return true; }

                    @Override
                    public Object createFromDouble(DeserializationContext ctxt, double value) {
                        return new MysteryBean(2.0 * value);
                    }
                }))
                .build();
        MysteryBean result = mapper.readValue("0.25", MysteryBean.class);
        assertNotNull(result);
        assertEquals(Double.valueOf(0.5), result.value);
    }

    @Test
    public void testBeanFromBoolean() throws Exception
    {
        ObjectMapper mapper = jsonMapperBuilder()
                .addModule(new MyModule(MysteryBean.class,
                new InstantiatorBase() {
                    @Override
                    public boolean canCreateFromBoolean() { return true; }

                    @Override
                    public Object createFromBoolean(DeserializationContext ctxt, boolean value) {
                        return new MysteryBean(Boolean.valueOf(value));
                    }
                }))
                .build();
        MysteryBean result = mapper.readValue("true", MysteryBean.class);
        assertNotNull(result);
        assertEquals(Boolean.TRUE, result.value);
    }

    /*
    /**********************************************************
    /* Other tests
    /**********************************************************
     */

    /**
     * Beyond basic features, it should be possible to even implement
     * polymorphic handling...
     */
    @Test
    public void testPolymorphicCreatorBean() throws Exception
    {
        ObjectMapper mapper = jsonMapperBuilder()
                .addModule(new MyModule(PolymorphicBeanBase.class, new PolymorphicBeanInstantiator()))
                .build();
        String JSON = "{\"type\":"+q(PolymorphicBean.class.getName())+",\"name\":\"Axel\"}";
        PolymorphicBeanBase result = mapper.readValue(JSON, PolymorphicBeanBase.class);
        assertNotNull(result);
        assertSame(PolymorphicBean.class, result.getClass());
        assertEquals("Axel", ((PolymorphicBean) result).name);
    }

    @Test
    public void testEmptyBean() throws Exception
    {
        AnnotatedBean bean = MAPPER.readValue("{}", AnnotatedBean.class);
        assertNotNull(bean);
        assertEquals("foo", bean.a);
        assertEquals(3, bean.b);
    }

    @Test
    public void testErrorMessageForMissingCtor() throws Exception
    {
        // first fail, check message from JSON Object (no default ctor)
        try {
            MAPPER.readValue("{ }", MyBean.class);
            fail("Should not succeed");
        } catch (InvalidDefinitionException e) {
            verifyException(e, "Cannot construct instance of");
            verifyException(e, "no Creators");
        }
    }

    @Test
    public void testErrorMessageForMissingStringCtor() throws Exception
    {
        // then from JSON String
        try {
            MAPPER.readValue("\"foo\"", MyBean.class);
            fail("Should not succeed");
        } catch (InvalidDefinitionException e) {
            verifyException(e, "Cannot construct instance of");
            verifyException(e, "no String-argument constructor/factory");
        }
    }
}
