package tools.jackson.databind.deser;

import java.lang.annotation.*;
import java.util.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.core.*;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.*;
import tools.jackson.databind.annotation.*;
import tools.jackson.databind.deser.std.*;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.util.AccessPattern;
import tools.jackson.databind.util.NameTransformer;
import tools.jackson.databind.util.StdConverter;

import static org.junit.jupiter.api.Assertions.*;

import static tools.jackson.databind.testutil.DatabindTestUtil.*;

/**
 * Test to check that customizations work as expected.
 */
@SuppressWarnings("serial")
public class CustomDeserializersTest
{
    static class ObjectWrapper {
        final Object object;
        protected ObjectWrapper(final Object object) {
            this.object = object;
        }
        public Object getObject() { return object; }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        static ObjectWrapper jsonValue(final Object object) {
            return new ObjectWrapper(object);
        }
    }
    
    static class DummyDeserializer<T>
        extends StdDeserializer<T>
    {
        final T value;

        public DummyDeserializer(T v, Class<T> cls) {
            super(cls);
            value = v;
        }

        @Override
        public T deserialize(JsonParser p, DeserializationContext ctxt)
        {
            // need to skip, if structured...
            p.skipChildren();
            return value;
        }
    }

    static class TestBeans {
        public List<TestBean> beans;
    }
    static class TestBean {
        public CustomBean c;
        public String d;
    }
    @JsonDeserialize(using=CustomBeanDeserializer.class)
    static class CustomBean {
        protected final int a, b;
        public CustomBean(int a, int b) {
            this.a = a;
            this.b = b;
        }
    }

    static class CustomBeanDeserializer extends ValueDeserializer<CustomBean>
    {
        @Override
        public CustomBean deserialize(JsonParser p, DeserializationContext ctxt)
        {
            int a = 0, b = 0;
            JsonToken t = p.currentToken();
            if (t == JsonToken.START_OBJECT) {
                t = p.nextToken();
            } else if (t != JsonToken.PROPERTY_NAME) {
                throw new Error();
            }
            while(t == JsonToken.PROPERTY_NAME) {
                final String fieldName = p.currentName();
                t = p.nextToken();
                if (t != JsonToken.VALUE_NUMBER_INT) {
                    throw new StreamReadException(p, "expecting number got "+ t);
                }
                if (fieldName.equals("a")) {
                    a = p.getIntValue();
                } else if (fieldName.equals("b")) {
                    b = p.getIntValue();
                } else {
                    throw new Error();
                }
                t = p.nextToken();
            }
            return new CustomBean(a, b);
        }
    }

    public static class Immutable {
        protected int x, y;

        public Immutable(int x0, int y0) {
            x = x0;
            y = y0;
        }
    }

    public static class CustomKey {
        private final int id;

        public CustomKey(int id) {this.id = id;}

        public int getId() { return id; }
    }

    public static class Model
    {
        protected final Map<CustomKey, String> map;

        @JsonCreator
        public Model(@JsonProperty("map") @JsonDeserialize(keyUsing = CustomKeyDeserializer.class) Map<CustomKey, String> map)
        {
            this.map = new HashMap<CustomKey, String>(map);
        }

        @JsonProperty
        @JsonSerialize(keyUsing = CustomKeySerializer.class)
        public Map<CustomKey, String> getMap() {
            return map;
        }
    }

    static class CustomKeySerializer extends ValueSerializer<CustomKey> {
        @Override
        public void serialize(CustomKey value, JsonGenerator g, SerializationContext provider) {
            g.writeName(String.valueOf(value.getId()));
        }
    }

    static class CustomKeyDeserializer extends KeyDeserializer {
        @Override
        public CustomKey deserializeKey(String key, DeserializationContext ctxt) {
            return new CustomKey(Integer.valueOf(key));
        }
    }

    // [databind#375]

    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @interface Negative { }

    static class Bean375Wrapper {
        @Negative
        public Bean375Outer value;
    }

    static class Bean375Outer {
        protected Bean375Inner inner;

        public Bean375Outer(Bean375Inner v) { inner = v; }
    }

    static class Bean375Inner {
        protected int x;

        public Bean375Inner(int x) { this.x = x; }
    }

    static class Bean375OuterDeserializer extends StdDeserializer<Bean375Outer>
    {
        protected BeanProperty prop;

        protected Bean375OuterDeserializer() { this(null); }
        protected Bean375OuterDeserializer(BeanProperty p) {
            super(Bean375Outer.class);
            prop = p;
        }

        @Override
        public Bean375Outer deserialize(JsonParser p, DeserializationContext ctxt)
        {
            Object ob = ctxt.readPropertyValue(p, prop, Bean375Inner.class);
            return new Bean375Outer((Bean375Inner) ob);
        }
        @Override
        public ValueDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property)
        {
            return new Bean375OuterDeserializer(property);
        }
    }

    static class Bean375InnerDeserializer extends StdDeserializer<Bean375Inner>
    {
        protected boolean negative;

        protected Bean375InnerDeserializer() { this(false); }
        protected Bean375InnerDeserializer(boolean n) {
            super(Bean375Inner.class);
            negative = n;
        }

        @Override
        public Bean375Inner deserialize(JsonParser p, DeserializationContext ctxt)
        {
            int x = p.getIntValue();
            if (negative) {
                x = -x;
            } else {
                x += x;
            }
            return new Bean375Inner(x);
        }

        @Override
        public ValueDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property)
        {
            if (property != null) {
                Negative n = property.getAnnotation(Negative.class);
                if (n != null) {
                    return new Bean375InnerDeserializer(true);
                }
            }
            return this;
        }
    }

    // for [databind#631]
    static class Issue631Bean
    {
        @JsonDeserialize(using=ParentClassDeserializer.class)
        public Object prop;
    }

    static class ParentClassDeserializer
        extends StdScalarDeserializer<Object>
    {
        protected ParentClassDeserializer() {
            super(Object.class);
        }

        @Override
        public Object deserialize(JsonParser p, DeserializationContext ctxt)
        {
            Object parent = p.currentValue();
            String desc = (parent == null) ? "NULL" : parent.getClass().getSimpleName();
            return "prop/"+ desc;
        }
    }

    static class UCStringDeserializer extends StdDeserializer<String> {
        public UCStringDeserializer() { super(String.class); }

        @Override
        public String deserialize(JsonParser p, DeserializationContext ctxt)
        {
            return p.getString().toUpperCase();
        }
    }

    static class DelegatingModuleImpl extends SimpleModule
    {
        public DelegatingModuleImpl() {
            super("test", Version.unknownVersion());
        }

        @Override
        public void setupModule(SetupContext context)
        {
            super.setupModule(context);
            context.addDeserializerModifier(new ValueDeserializerModifier() {
                @Override
                public ValueDeserializer<?> modifyDeserializer(DeserializationConfig config,
                        BeanDescription beanDesc, ValueDeserializer<?> deserializer) {
                    if (deserializer.handledType() == String.class) {
                        ValueDeserializer<?> d = new MyStringDeserializer(deserializer);
                        // just for test coverage purposes...
                        if (d.getDelegatee() != deserializer) {
                            throw new Error("Cannot access delegatee!");
                        }
                        return d;
                    }
                    return deserializer;
                }
            });
        }
    }

    static class MyStringDeserializer extends DelegatingDeserializer
    {
        public MyStringDeserializer(ValueDeserializer<?> newDel) {
            super(newDel);
        }

        @Override
        protected ValueDeserializer<?> newDelegatingInstance(ValueDeserializer<?> newDel) {
            return new MyStringDeserializer(newDel);
        }

        @Override
        public Object deserialize(JsonParser p, DeserializationContext ctxt)
        {
            Object ob = _delegatee.deserialize(p, ctxt);
            return "MY:"+ob;
        }
    }

    static class MyNodeDeserializer extends StdDeserializer<Object> {
        public MyNodeDeserializer() { super(Object.class); }

        @Override
        public Object deserialize(JsonParser p, DeserializationContext ctxt)
        {
            return ctxt.readTree(p);
        }
    }

    @JsonDeserialize(using = NamedPointDeserializer.class)
    static class NamedPoint
    {
        public Point point;
        public String name;

        public NamedPoint(String name, Point point) {
            this.point = point;
            this.name = name;
        }
    }

    static class NamedPointDeserializer extends StdDeserializer<NamedPoint>
    {
        public NamedPointDeserializer() {
            super(NamedPoint.class);
        }

        @Override
        public NamedPoint deserialize(JsonParser p, DeserializationContext ctxt)
        {
            JsonNode tree = ctxt.readTree(p);
            String name = tree.path("name").asString(null);
            Point point = ctxt.readTreeAsValue(tree.get("point"), Point.class);
            return new NamedPoint(name, point);
        }
    }

    // [databind#3748]
    static class BaseDeserializer3748
        extends StdDeserializer<String>
    {
        public BaseDeserializer3748() { super(String.class); }

        @Override
        public String deserialize(JsonParser p, DeserializationContext ctxt) {
            return null;
        }

        @Override
        public Object getEmptyValue(DeserializationContext ctxt) {
            return "empty";
        }

        @Override
        public AccessPattern getEmptyAccessPattern() {
            return AccessPattern.ALWAYS_NULL;
        }

        @Override
        public Object getAbsentValue(DeserializationContext ctxt) {
            return "absent";
        }

        @Override
        public ValueDeserializer<String> unwrappingDeserializer(DeserializationContext ctxt,
                NameTransformer unwrapper) {
            return new BaseDeserializer3748();
        }
    }

    static class Delegating3748 extends DelegatingDeserializer
    {
        public Delegating3748() {
            this(new BaseDeserializer3748());
        }

        public Delegating3748(ValueDeserializer<?> del) {
            super(del);
        }

        @Override
        protected ValueDeserializer<?> newDelegatingInstance(ValueDeserializer<?> newDelegatee) {
            return new Delegating3748(newDelegatee);
        }
    }

    /*
    /**********************************************************************
    /* Test methods
    /**********************************************************************
     */

    private final ObjectMapper MAPPER = newJsonMapper();

    @Test
    public void testCustomBeanDeserializer() throws Exception
    {
        String json = "{\"beans\":[{\"c\":{\"a\":10,\"b\":20},\"d\":\"hello, tatu\"}]}";
        TestBeans beans = MAPPER.readValue(json, TestBeans.class);

        assertNotNull(beans);
        List<TestBean> results = beans.beans;
        assertNotNull(results);
        assertEquals(1, results.size());
        TestBean bean = results.get(0);
        assertEquals("hello, tatu", bean.d);
        CustomBean c = bean.c;
        assertNotNull(c);
        assertEquals(10, c.a);
        assertEquals(20, c.b);

        json = "{\"beans\":[{\"c\":{\"b\":3,\"a\":-4},\"d\":\"\"},"
            +"{\"d\":\"abc\", \"c\":{\"b\":15}}]}";
        beans = MAPPER.readValue(json, TestBeans.class);

        assertNotNull(beans);
        results = beans.beans;
        assertNotNull(results);
        assertEquals(2, results.size());

        bean = results.get(0);
        assertEquals("", bean.d);
        c = bean.c;
        assertNotNull(c);
        assertEquals(-4, c.a);
        assertEquals(3, c.b);

        bean = results.get(1);
        assertEquals("abc", bean.d);
        c = bean.c;
        assertNotNull(c);
        assertEquals(0, c.a);
        assertEquals(15, c.b);
    }

    // [Issue#87]: delegating deserializer
    @Test
    public void testDelegating() throws Exception
    {
        SimpleModule module = new SimpleModule("test", Version.unknownVersion());
        module.addDeserializer(Immutable.class,
            new StdConvertingDeserializer<Immutable>(
                new StdConverter<JsonNode, Immutable>() {
                    @Override
                    public Immutable convert(JsonNode value)
                    {
                        int x = value.path("x").asInt();
                        int y = value.path("y").asInt();
                        return new Immutable(x, y);
                    }
                }
                ));
        ObjectMapper mapper = jsonMapperBuilder()
                .addModule(module)
                .build();
        Immutable imm = mapper.readValue("{\"x\":3,\"y\":7}", Immutable.class);
        assertEquals(3, imm.x);
        assertEquals(7, imm.y);
    }

    // [databind#623]
    @Test
    public void testJsonNodeDelegating() throws Exception
    {
        SimpleModule module = new SimpleModule("test", Version.unknownVersion());
        module.addDeserializer(Immutable.class,
            new StdNodeBasedDeserializer<Immutable>(Immutable.class) {
                @Override
                public Immutable convert(JsonNode root, DeserializationContext ctxt) {
                    int x = root.path("x").asInt();
                    int y = root.path("y").asInt();
                    return new Immutable(x, y);
                }
        });
        ObjectMapper mapper = jsonMapperBuilder()
                .addModule(module)
                .build();
        Immutable imm = mapper.readValue("{\"x\":-10,\"y\":3}", Immutable.class);
        assertEquals(-10, imm.x);
        assertEquals(3, imm.y);
    }

    @Test
    public void testIssue882() throws Exception
    {
        Model original = new Model(Collections.singletonMap(new CustomKey(123), "test"));
        String json = MAPPER.writeValueAsString(original);
        Model deserialized = MAPPER.readValue(json, Model.class);
        assertNotNull(deserialized);
        assertNotNull(deserialized.map);
        assertEquals(1, deserialized.map.size());
    }

    // [#337]: convenience methods for custom deserializers to use
    @Test
    public void testContextReadValue() throws Exception
    {
        SimpleModule module = new SimpleModule("test", Version.unknownVersion());
        module.addDeserializer(Bean375Outer.class, new Bean375OuterDeserializer());
        module.addDeserializer(Bean375Inner.class, new Bean375InnerDeserializer());
        ObjectMapper mapper = jsonMapperBuilder()
                .addModule(module)
                .build();

        // First, without property; doubles up value:
        Bean375Outer outer = mapper.readValue("13", Bean375Outer.class);
        assertEquals(26, outer.inner.x);

        // then with property; should find annotation, turn negative
        Bean375Wrapper w = mapper.readValue("{\"value\":13}", Bean375Wrapper.class);
        assertNotNull(w.value);
        assertNotNull(w.value.inner);
        assertEquals(-13, w.value.inner.x);
    }

    // [#631]: "current value" access
    @Test
    public void testCurrentValueAccess() throws Exception
    {
        Issue631Bean bean = MAPPER.readValue(a2q("{'prop':'stuff'}"),
                Issue631Bean.class);
        assertNotNull(bean);
        assertEquals("prop/Issue631Bean", bean.prop);
    }

    @Test
    public void testCustomStringDeser() throws Exception
    {
        ObjectMapper mapper = jsonMapperBuilder()
                .addModule(new SimpleModule()
                        .addDeserializer(String.class, new UCStringDeserializer()))
                .build();
        assertEquals("FOO", mapper.readValue(q("foo"), String.class));
        StringWrapper sw = mapper.readValue("{\"str\":\"foo\"}", StringWrapper.class);
        assertNotNull(sw);
        assertEquals("FOO", sw.str);
    }

    @Test
    public void testDelegatingDeserializer() throws Exception
    {
       ObjectMapper mapper = jsonMapperBuilder()
                .addModule(new DelegatingModuleImpl())
                .build();
        String str = mapper.readValue(q("foo"), String.class);
       assertEquals("MY:foo", str);
    }

    // [databind#2392]
    @Test
    public void testModifyingCustomDeserializer() throws Exception
    {
        ObjectMapper mapper = jsonMapperBuilder()
                .addModule(new SimpleModule()
                        .setDeserializerModifier(new ValueDeserializerModifier() {
                            @Override
                            public ValueDeserializer<?> modifyDeserializer(DeserializationConfig config,
                                    BeanDescription beanDesc, ValueDeserializer<?> deserializer) {
                                if (deserializer instanceof DummyDeserializer<?>) {
                                    return new DummyDeserializer<String>("FOOBAR", String.class);
                                }
                                return deserializer;
                            }
                        })
                        .addDeserializer(String.class, new DummyDeserializer<String>("dummy", String.class))
                        ).build();
        String str = mapper.readValue(q("foo"), String.class);
        assertEquals("FOOBAR", str);
    }

    // [databind#2452]
    @Test
    public void testCustomDeserializerWithReadTree() throws Exception
    {
        ObjectMapper mapper = jsonMapperBuilder()
                .addModule(new SimpleModule()
                        .addDeserializer(Object.class, new MyNodeDeserializer())
                        )
                .build();
        ObjectWrapper w = mapper.readValue(a2q("[ 1, { 'a' : 3}, 123 ] "),
                ObjectWrapper.class);
        assertEquals(ArrayNode.class, w.getObject().getClass());
        JsonNode n = (JsonNode) w.getObject();
        assertEquals(3, n.size());
        assertEquals(123, n.get(2).intValue());
    }

    // [databind#3002]
    @Test
    public void testCustomDeserializerWithReadTreeAsValue() throws Exception
    {
        final String json = a2q("{'point':{'x':13, 'y':-4}, 'name':'Foozibald' }");
        NamedPoint result = MAPPER.readValue(json, NamedPoint.class);
        assertNotNull(result);
        assertEquals("Foozibald", result.name);
        assertEquals(new Point(13, -4), result.point);

        // and with JavaType variant too
        result = MAPPER.readValue(json, MAPPER.constructType(NamedPoint.class));
        assertNotNull(result);
        assertEquals("Foozibald", result.name);
        assertEquals(new Point(13, -4), result.point);

        // also, try some edge conditions
        result = MAPPER.readValue(a2q("{'name':4}"), NamedPoint.class);
        assertNotNull(result);
        assertEquals("4", result.name);
        assertNull(result.point);
    }

    // [databind#3748]
    @Test
    public void testBasicDelegatingDeser() throws Exception
    {
        Delegating3748 deser = new Delegating3748();
        assertEquals("absent", deser.getAbsentValue(null));
        assertEquals("empty", deser.getEmptyValue(null));
        assertEquals(AccessPattern.ALWAYS_NULL, deser.getEmptyAccessPattern());
        ValueDeserializer<?> unwrapping = deser.unwrappingDeserializer(null, null);
        assertNotNull(unwrapping);
        assertNotSame(deser, unwrapping);
    }
}
