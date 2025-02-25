package tools.jackson.databind;

import java.util.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

import tools.jackson.core.*;
import tools.jackson.databind.annotation.*;
import tools.jackson.databind.cfg.HandlerInstantiator;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.introspect.Annotated;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.TypeIdResolver;
import tools.jackson.databind.jsontype.TypeResolverBuilder;
import tools.jackson.databind.jsontype.impl.TypeIdResolverBase;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static tools.jackson.databind.testutil.DatabindTestUtil.q;

public class HandlerInstantiationTest
{
    /*
    /**********************************************************************
    /* Helper classes, beans
    /**********************************************************************
     */

    @JsonDeserialize(using=MyBeanDeserializer.class)
    @JsonSerialize(using=MyBeanSerializer.class)
    static class MyBean
    {
        public String value;

        public MyBean() { this(null); }
        public MyBean(String s) { value = s; }
    }

    @SuppressWarnings("serial")
    @JsonDeserialize(keyUsing=MyKeyDeserializer.class)
    static class MyMap extends HashMap<String,String> { }

    @JsonTypeInfo(use=Id.CUSTOM, include=As.WRAPPER_ARRAY)
    @JsonTypeIdResolver(TestCustomIdResolver.class)
    static class TypeIdBean {
        public int x;

        public TypeIdBean() { }
        public TypeIdBean(int x) { this.x = x; }
    }

    static class TypeIdBeanWrapper {
        public TypeIdBean bean;

        public TypeIdBeanWrapper() { this(null); }
        public TypeIdBeanWrapper(TypeIdBean b) { bean = b; }
    }

    /*
    /**********************************************************************
    /* Helper classes, serializers/deserializers/resolvers
    /**********************************************************************
     */

    static class MyBeanDeserializer extends ValueDeserializer<MyBean>
    {
        public String _prefix = "";

        public MyBeanDeserializer(String p) {
            _prefix  = p;
        }

        @Override
        public MyBean deserialize(JsonParser jp, DeserializationContext ctxt)
        {
            return new MyBean(_prefix+jp.getString());
        }
    }

    static class MyKeyDeserializer extends KeyDeserializer
    {
        public MyKeyDeserializer() { }

        @Override
        public Object deserializeKey(String key, DeserializationContext ctxt)
        {
            return "KEY";
        }
    }

    static class MyBeanSerializer extends ValueSerializer<MyBean>
    {
        public String _prefix = "";

        public MyBeanSerializer(String p) {
            _prefix  = p;
        }

        @Override
        public void serialize(MyBean value, JsonGenerator jgen, SerializationContext provider)
        {
            jgen.writeString(_prefix + value.value);
        }
    }

    // copied from "TestCustomTypeIdResolver"
    static class TestCustomIdResolver extends TypeIdResolverBase
    {
        private static final long serialVersionUID = 1L;

        static List<JavaType> initTypes;

        final String _id;

        public TestCustomIdResolver(String idForBean) {
            _id = idForBean;
        }

        @Override
        public Id getMechanism() {
            return Id.CUSTOM;
        }

        @Override
        public String idFromValue(DatabindContext ctxt, Object value)
        {
            if (value.getClass() == TypeIdBean.class) {
                return _id;
            }
            return "unknown";
        }

        @Override
        public String idFromValueAndType(DatabindContext ctxt, Object value, Class<?> type) {
            return idFromValue(ctxt, value);
        }

        @Override
        public void init(JavaType baseType) {
            if (initTypes != null) {
                initTypes.add(baseType);
            }
        }

        @Override
        public JavaType typeFromId(DatabindContext context, String id)
        {
            if (id.equals(_id)) {
                return context.constructType(TypeIdBean.class);
            }
            return null;
        }
        @Override
        public String idFromBaseType(DatabindContext ctxt) {
            return "xxx";
        }
    }

    /*
    /**********************************************************************
    /* Helper classes, handler instantiator
    /**********************************************************************
     */

    static class MyInstantiator extends HandlerInstantiator
    {
        private final String _prefix;

        public MyInstantiator(String p) {
            _prefix = p;
        }

        @Override
        public ValueDeserializer<?> deserializerInstance(DeserializationConfig config,
                Annotated annotated,
                Class<?> deserClass)
        {
            if (deserClass == MyBeanDeserializer.class) {
                return new MyBeanDeserializer(_prefix);
            }
            return null;
        }

        @Override
        public KeyDeserializer keyDeserializerInstance(DeserializationConfig config,
                Annotated annotated, Class<?> keyDeserClass)
        {
            if (keyDeserClass == MyKeyDeserializer.class) {
                return new MyKeyDeserializer();
            }
            return null;

        }

        @Override
        public ValueSerializer<?> serializerInstance(SerializationConfig config,
                Annotated annotated, Class<?> serClass)
        {
            if (serClass == MyBeanSerializer.class) {
                return new MyBeanSerializer(_prefix);
            }
            return null;
        }

        @Override
        public TypeIdResolver typeIdResolverInstance(MapperConfig<?> config,
                Annotated annotated, Class<?> resolverClass)
        {
            if (resolverClass == TestCustomIdResolver.class) {
                return new TestCustomIdResolver("!!!");
            }
            return null;
        }

        @Override
        public TypeResolverBuilder<?> typeResolverBuilderInstance(MapperConfig<?> config, Annotated annotated,
                Class<?> builderClass)
        {
            return null;
        }
    }

    /*
    /**********************************************************************
    /* Unit tests
    /**********************************************************************
     */

    @Test
    public void testDeserializer() throws Exception
    {
        JsonMapper mapper = JsonMapper.builder()
                .handlerInstantiator(new MyInstantiator("abc:"))
                .build();
        MyBean result = mapper.readValue(q("123"), MyBean.class);
        assertEquals("abc:123", result.value);
    }

    @Test
    public void testKeyDeserializer() throws Exception
    {
        JsonMapper mapper = JsonMapper.builder()
                .handlerInstantiator(new MyInstantiator("abc:"))
                .build();
        MyMap map = mapper.readValue("{\"a\":\"b\"}", MyMap.class);
        // easiest to test by just serializing...
        assertEquals("{\"KEY\":\"b\"}", mapper.writeValueAsString(map));
    }

    @Test
    public void testSerializer() throws Exception
    {
        JsonMapper mapper = JsonMapper.builder()
                .handlerInstantiator(new MyInstantiator("xyz:"))
                .build();
        assertEquals(q("xyz:456"), mapper.writeValueAsString(new MyBean("456")));
    }

    @Test
    public void testTypeIdResolver() throws Exception
    {
        JsonMapper mapper = JsonMapper.builder()
                .handlerInstantiator(new MyInstantiator("foobar"))
                .build();
        String json = mapper.writeValueAsString(new TypeIdBeanWrapper(new TypeIdBean(123)));
        // should now use our custom id scheme:
        assertEquals("{\"bean\":[\"!!!\",{\"x\":123}]}", json);
        // and bring it back too:
        TypeIdBeanWrapper result = mapper.readValue(json, TypeIdBeanWrapper.class);
        TypeIdBean bean = result.bean;
        assertEquals(123, bean.x);
    }

}
