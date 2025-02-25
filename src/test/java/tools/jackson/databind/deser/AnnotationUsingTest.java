package tools.jackson.databind.deser;

import java.util.*;

import org.junit.jupiter.api.Test;

import tools.jackson.core.*;
import tools.jackson.databind.*;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.deser.std.StdDeserializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit test suite that tests "usingXxx" properties of
 * {@link JsonDeserialize} annotation.
 */
@SuppressWarnings("serial")
public class AnnotationUsingTest
{
    /*
    /**********************************************************************
    /* Annotated Bean classes
    /**********************************************************************
     */

    /**
     * Class for testing {@link ValueDeserializer} annotation
     * for class itself.
     */
    @JsonDeserialize(using=ValueDeserializer.class)
    final static class ValueClass {
        int _a;

        /* we'll test it by not having default no-arg ctor, and leaving
         * out single-int-arg ctor (because deserializer would use that too)
         */
        public ValueClass(int a, int b) {
            _a = a;
        }
    }

    /**
     * Class for testing {@link ValueDeserializer} annotation
     * for a method
     */
    final static class MethodBean {
        int[] _ints;

        /* Note: could be made to work otherwise, except that
         * to trigger failure (in absence of annotation) Json
         * is of type VALUE_NUMBER_INT, not an Array: array would
         * work by default, but scalar not
         */
        @JsonDeserialize(using=IntsDeserializer.class)
        public void setInts(int[] i) {
            _ints = i;
        }
    }

    static class ArrayBean {
        @JsonDeserialize(contentUsing=ValueDeserializer.class)
        public Object[] values;
    }

    static class ListBean {
        @JsonDeserialize(contentUsing=ValueDeserializer.class)
        public List<Object> values;
    }

    static class MapBean {
        @JsonDeserialize(contentUsing=ValueDeserializer.class)
        public Map<String,Object> values;
    }

    static class MapKeyBean {
        @JsonDeserialize(keyUsing=MapKeyDeserializer.class)
        public Map<Object,Object> values;
    }

    @JsonDeserialize(keyUsing=MapKeyDeserializer.class, contentUsing=ValueDeserializer.class)
    static class MapKeyMap extends HashMap<Object,Object> { }

    /*
    /**********************************************************************
    /* Deserializers
    /**********************************************************************
     */

    static class ValueDeserializer extends StdDeserializer<ValueClass>
    {
        public ValueDeserializer() { super(ValueClass.class); }

        @Override
        public ValueClass deserialize(JsonParser p, DeserializationContext ctxt)
        {
            int i = p.getIntValue();
            return new ValueClass(i, i);
        }
    }

    private final static class IntsDeserializer extends StdDeserializer<int[]>
    {
        public IntsDeserializer() { super(int[].class); }

        @Override
        public int[] deserialize(JsonParser p, DeserializationContext ctxt)
        {
            return new int[] { p.getIntValue() };
        }
    }

    private final static class MapKeyDeserializer extends KeyDeserializer
    {
        @Override
        public Object deserializeKey(String key, DeserializationContext ctxt)
        {
            return new String[] { key };
        }
    }

    /*
    /**********************************************************************
    /* Tests: specifying deserializer of value itself
    /**********************************************************************
     */

    // Unit test to verify that {@link JsonDeserialize#using} annotation works
    // when applied to a class
    @Test
    public void testClassDeserializer() throws Exception
    {
        ObjectMapper m = new ObjectMapper();
        ValueClass result = m.readValue("  123  ", ValueClass.class);
        assertEquals(123, result._a);
    }

    // Unit test to verify that {@link JsonDeserialize#using} annotation works
    // when applied to a Method
    @Test
    public void testMethodDeserializer() throws Exception
    {
        ObjectMapper m = new ObjectMapper();
        // note: since it's part of method, must parse from Object struct
        MethodBean result = m.readValue(" { \"ints\" : 3 } ", MethodBean.class);
        assertNotNull(result);
        int[] ints = result._ints;
        assertNotNull(ints);
        assertEquals(1, ints.length);
        assertEquals(3, ints[0]);
    }

    /*
    /**********************************************************************
    /* Tests: specifying deserializer for keys and/or contents
    /**********************************************************************
     */

    @Test
    public void testArrayContentUsing() throws Exception
    {
        ObjectMapper m = new ObjectMapper();
        ArrayBean result = m.readValue(" { \"values\" : [ 1, 2, 3 ] } ", ArrayBean.class);
        assertNotNull(result);
        Object[] obs = result.values;
        assertNotNull(obs);
        assertEquals(3, obs.length);
        assertEquals(ValueClass.class, obs[0].getClass());
        assertEquals(1, ((ValueClass) obs[0])._a);
        assertEquals(ValueClass.class, obs[1].getClass());
        assertEquals(2, ((ValueClass) obs[1])._a);
        assertEquals(ValueClass.class, obs[2].getClass());
        assertEquals(3, ((ValueClass) obs[2])._a);
    }

    @Test
    public void testListContentUsing() throws Exception
    {
        ObjectMapper m = new ObjectMapper();
        ListBean result = m.readValue(" { \"values\" : [ 1, 2, 3 ] } ", ListBean.class);
        assertNotNull(result);
        List<Object> obs = result.values;
        assertNotNull(obs);
        assertEquals(3, obs.size());
        assertEquals(ValueClass.class, obs.get(0).getClass());
        assertEquals(1, ((ValueClass) obs.get(0))._a);
        assertEquals(ValueClass.class, obs.get(1).getClass());
        assertEquals(2, ((ValueClass) obs.get(1))._a);
        assertEquals(ValueClass.class, obs.get(2).getClass());
        assertEquals(3, ((ValueClass) obs.get(2))._a);
    }

    @Test
    public void testMapContentUsing() throws Exception
    {
        ObjectMapper m = new ObjectMapper();
        MapBean result = m.readValue(" { \"values\" : { \"a\": 1, \"b\":2 } } ", MapBean.class);
        assertNotNull(result);
        Map<String,Object> map = result.values;
        assertNotNull(map);
        assertEquals(2, map.size());
        assertEquals(ValueClass.class, map.get("a").getClass());
        assertEquals(1, ((ValueClass) map.get("a"))._a);
        assertEquals(ValueClass.class, map.get("b").getClass());
        assertEquals(2, ((ValueClass) map.get("b"))._a);
    }

    @Test
    public void testMapKeyUsing() throws Exception
    {
        ObjectMapper m = new ObjectMapper();
        MapKeyBean result = m.readValue(" { \"values\" : { \"a\": true } } ", MapKeyBean.class);
        assertNotNull(result);
        Map<Object,Object> map = result.values;
        assertNotNull(map);
        assertEquals(1, map.size());
        Map.Entry<Object,Object> en = map.entrySet().iterator().next();
        assertEquals(String[].class, en.getKey().getClass());
        assertEquals(Boolean.TRUE, en.getValue());
    }

    // @since 1.8
    @Test
    public void testRootValueWithCustomKey() throws Exception
    {
        ObjectMapper m = new ObjectMapper();
        MapKeyMap result = m.readValue(" { \"a\": 13 } ", MapKeyMap.class);
        assertNotNull(result);
        assertNotNull(result);
        assertEquals(1, result.size());
        Map.Entry<Object,Object> en = result.entrySet().iterator().next();
        assertEquals(ValueClass.class, en.getValue().getClass());
        assertEquals(13, ((ValueClass) en.getValue())._a);
        assertEquals(String[].class, en.getKey().getClass());
    }

}
