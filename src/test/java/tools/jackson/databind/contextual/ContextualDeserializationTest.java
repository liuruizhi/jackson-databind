package tools.jackson.databind.contextual;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.*;

import tools.jackson.core.JsonParser;
import tools.jackson.core.Version;
import tools.jackson.databind.*;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.deser.std.StdScalarDeserializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import static tools.jackson.databind.testutil.DatabindTestUtil.a2q;
import static tools.jackson.databind.testutil.DatabindTestUtil.jsonMapperBuilder;

/**
 * Test cases to verify that it is possible to define deserializers
 * that can use contextual information (like field/method
 * annotations) for configuration.
 */
public class ContextualDeserializationTest
{
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @JacksonAnnotation
    public @interface Name {
        public String value();
    }

    static class StringValue {
        protected String value;

        public StringValue(String v) { value = v; }
    }

    static class ContextualBean
    {
        @Name("NameA")
        public StringValue a;
        @Name("NameB")
        public StringValue b;
    }

    static class ContextualCtorBean
    {
        protected String a, b;

        @JsonCreator
        public ContextualCtorBean(
                @Name("CtorA") @JsonProperty("a") StringValue a,
                @Name("CtorB") @JsonProperty("b") StringValue b)
        {
            this.a = a.value;
            this.b = b.value;
        }
    }

    @Name("Class")
    static class ContextualClassBean
    {
        public StringValue a;

        @Name("NameB")
        public StringValue b;
    }

    static class ContextualArrayBean
    {
        @Name("array")
        public StringValue[] beans;
    }

    static class ContextualListBean
    {
        @Name("list")
        public List<StringValue> beans;
    }

    static class ContextualMapBean
    {
        @Name("map")
        public Map<String, StringValue> beans;
    }

    static class MyContextualDeserializer
        extends ValueDeserializer<StringValue>
    {
        protected final String _fieldName;

        public MyContextualDeserializer() { this(""); }
        public MyContextualDeserializer(String fieldName) {
            _fieldName = fieldName;
        }

        @Override
        public StringValue deserialize(JsonParser jp, DeserializationContext ctxt)
        {
            return new StringValue(""+_fieldName+"="+jp.getString());
        }

        @Override
        public ValueDeserializer<?> createContextual(DeserializationContext ctxt,
                BeanProperty property)
        {
            String name = (property == null) ? "NULL" : property.getName();
            return new MyContextualDeserializer(name);
        }
    }

    /**
     * Alternative that uses annotation for choosing name to use
     */
    static class AnnotatedContextualDeserializer
        extends ValueDeserializer<StringValue>
    {
        protected final String _fieldName;

        public AnnotatedContextualDeserializer() { this(""); }
        public AnnotatedContextualDeserializer(String fieldName) {
            _fieldName = fieldName;
        }

        @Override
        public StringValue deserialize(JsonParser jp, DeserializationContext ctxt)
        {
            return new StringValue(""+_fieldName+"="+jp.getString());
        }

        @Override
        public ValueDeserializer<?> createContextual(DeserializationContext ctxt,
                BeanProperty property)
        {
            Name ann = property.getAnnotation(Name.class);
            if (ann == null) {
                ann = property.getContextAnnotation(Name.class);
            }
            String propertyName = (ann == null) ?  "UNKNOWN" : ann.value();
            return new MyContextualDeserializer(propertyName);
        }
    }

    static class GenericStringDeserializer
        extends StdScalarDeserializer<Object>
    {
        final String _value;

        public GenericStringDeserializer() { this("N/A"); }
        protected GenericStringDeserializer(String value) {
            super(String.class);
            _value = value;
        }

        @Override
        public ValueDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
            return new GenericStringDeserializer(String.valueOf(ctxt.getContextualType().getRawClass().getSimpleName()));
        }

        @Override
        public Object deserialize(JsonParser p, DeserializationContext ctxt) {
            return _value;
        }
    }

    static class GenericBean {
        @JsonDeserialize(contentUsing=GenericStringDeserializer.class)
        public Map<Integer, String> stuff;
    }

    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    private final ObjectMapper ANNOTATED_CTXT_MAPPER = JsonMapper.builder()
            .addModule(new SimpleModule("test", Version.unknownVersion())
                    .addDeserializer(StringValue.class, new AnnotatedContextualDeserializer()
            ))
            .build();

    @Test
    public void testSimple() throws Exception
    {
        SimpleModule module = new SimpleModule("test", Version.unknownVersion());
        module.addDeserializer(StringValue.class, new MyContextualDeserializer());
        ObjectMapper mapper = jsonMapperBuilder()
                .addModule(module)
                .build();
        ContextualBean bean = mapper.readValue("{\"a\":\"1\",\"b\":\"2\"}", ContextualBean.class);
        assertEquals("a=1", bean.a.value);
        assertEquals("b=2", bean.b.value);

        // try again, to ensure caching etc works
        bean = mapper.readValue("{\"a\":\"3\",\"b\":\"4\"}", ContextualBean.class);
        assertEquals("a=3", bean.a.value);
        assertEquals("b=4", bean.b.value);
    }

    @Test
    public void testSimpleWithAnnotations() throws Exception
    {
        ObjectMapper mapper = _mapperWithAnnotatedContextual();
        ContextualBean bean = mapper.readValue("{\"a\":\"1\",\"b\":\"2\"}", ContextualBean.class);
        assertEquals("NameA=1", bean.a.value);
        assertEquals("NameB=2", bean.b.value);

        // try again, to ensure caching etc works
        bean = mapper.readValue("{\"a\":\"x\",\"b\":\"y\"}", ContextualBean.class);
        assertEquals("NameA=x", bean.a.value);
        assertEquals("NameB=y", bean.b.value);
    }

    @Test
    public void testSimpleWithClassAnnotations() throws Exception
    {
        ObjectMapper mapper = _mapperWithAnnotatedContextual();
        ContextualClassBean bean = mapper.readValue("{\"a\":\"1\",\"b\":\"2\"}", ContextualClassBean.class);
        assertEquals("Class=1", bean.a.value);
        assertEquals("NameB=2", bean.b.value);
        // and again
        bean = mapper.readValue("{\"a\":\"123\",\"b\":\"345\"}", ContextualClassBean.class);
        assertEquals("Class=123", bean.a.value);
        assertEquals("NameB=345", bean.b.value);
    }

    @Test
    public void testAnnotatedCtor() throws Exception
    {
        ObjectMapper mapper = _mapperWithAnnotatedContextual();
        ContextualCtorBean bean = mapper.readValue("{\"a\":\"foo\",\"b\":\"bar\"}", ContextualCtorBean.class);
        assertEquals("CtorA=foo", bean.a);
        assertEquals("CtorB=bar", bean.b);

        bean = mapper.readValue("{\"a\":\"1\",\"b\":\"0\"}", ContextualCtorBean.class);
        assertEquals("CtorA=1", bean.a);
        assertEquals("CtorB=0", bean.b);
    }

    @Test
    public void testAnnotatedArray() throws Exception
    {
        ObjectMapper mapper = _mapperWithAnnotatedContextual();
        ContextualArrayBean bean = mapper.readValue("{\"beans\":[\"x\"]}", ContextualArrayBean.class);
        assertEquals(1, bean.beans.length);
        assertEquals("array=x", bean.beans[0].value);

        bean = mapper.readValue("{\"beans\":[\"a\",\"b\"]}", ContextualArrayBean.class);
        assertEquals(2, bean.beans.length);
        assertEquals("array=a", bean.beans[0].value);
        assertEquals("array=b", bean.beans[1].value);
    }

    @Test
    public void testAnnotatedList() throws Exception
    {
        ObjectMapper mapper = _mapperWithAnnotatedContextual();
        ContextualListBean bean = mapper.readValue("{\"beans\":[\"x\"]}", ContextualListBean.class);
        assertEquals(1, bean.beans.size());
        assertEquals("list=x", bean.beans.get(0).value);

        bean = mapper.readValue("{\"beans\":[\"x\",\"y\",\"z\"]}", ContextualListBean.class);
        assertEquals(3, bean.beans.size());
        assertEquals("list=x", bean.beans.get(0).value);
        assertEquals("list=y", bean.beans.get(1).value);
        assertEquals("list=z", bean.beans.get(2).value);
    }

    @Test
    public void testAnnotatedMap() throws Exception
    {
        ObjectMapper mapper = _mapperWithAnnotatedContextual();
        ContextualMapBean bean = mapper.readValue("{\"beans\":{\"a\":\"b\"}}", ContextualMapBean.class);
        assertEquals(1, bean.beans.size());
        Map.Entry<String,StringValue> entry = bean.beans.entrySet().iterator().next();
        assertEquals("a", entry.getKey());
        assertEquals("map=b", entry.getValue().value);

        bean = mapper.readValue("{\"beans\":{\"x\":\"y\",\"1\":\"2\"}}", ContextualMapBean.class);
        assertEquals(2, bean.beans.size());
        Iterator<Map.Entry<String,StringValue>> it = bean.beans.entrySet().iterator();
        entry = it.next();
        assertEquals("x", entry.getKey());
        assertEquals("map=y", entry.getValue().value);
        entry = it.next();
        assertEquals("1", entry.getKey());
        assertEquals("map=2", entry.getValue().value);
    }

    // for [databind#165]
    @Test
    public void testContextualType() throws Exception {
        GenericBean bean = new ObjectMapper().readValue(a2q("{'stuff':{'1':'b'}}"),
                GenericBean.class);
        assertNotNull(bean.stuff);
        assertEquals(1, bean.stuff.size());
        assertEquals("String", bean.stuff.get(Integer.valueOf(1)));
    }

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */

    private ObjectMapper _mapperWithAnnotatedContextual() {
        return ANNOTATED_CTXT_MAPPER;
    }
}
