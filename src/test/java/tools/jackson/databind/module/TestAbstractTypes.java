package tools.jackson.databind.module;

import java.util.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.core.Version;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.testutil.DatabindTestUtil;

import static org.junit.jupiter.api.Assertions.*;

public class TestAbstractTypes extends DatabindTestUtil
{
    static class MyString implements CharSequence
    {
        protected String value;

        public MyString(String s) { value = s; }

        @Override
        public char charAt(int index) {
            return value.charAt(index);
        }

        @Override
        public int length() {
            return value.length();
        }

        @Override
        public CharSequence subSequence(int arg0, int arg1) { return this; }
    }

    public interface Abstract {
        public int getValue();
    }

    public static class AbstractImpl implements Abstract {
        @Override
        public int getValue() { return 3; }
    }

    // [databind#2019]: mappings from multiple modules
    public interface Datatype1 {
        String getValue();
    }

    public interface Datatype2 {
        String getValue();
    }

    static class SimpleDatatype1 implements Datatype1 {

        private final String value;

        @JsonCreator
        public SimpleDatatype1(@JsonProperty("value") String value) {
            this.value = value;
        }

        @Override
        public String getValue() {
            return value;
        }
    }

    static class SimpleDatatype2 implements Datatype2 {
        private final String value;

        @JsonCreator
        public SimpleDatatype2(@JsonProperty("value") String value) {
            this.value = value;
        }

        @Override
        public String getValue() {
            return value;
        }
    }

    /*
    /**********************************************************************
    /* Test methods
    /**********************************************************************
     */

    @Test
    public void testCollectionDefaulting() throws Exception
    {
        SimpleModule mod = new SimpleModule("test", Version.unknownVersion());
        // let's ensure we get hierarchic mapping
        mod.addAbstractTypeMapping(Collection.class, List.class);
        mod.addAbstractTypeMapping(List.class, LinkedList.class);
        ObjectMapper mapper = jsonMapperBuilder()
                .addModule(mod)
                .build();
        Collection<?> result = mapper.readValue("[]", Collection.class);
        assertEquals(LinkedList.class, result.getClass());
    }

    @Test
    public void testMapDefaultingBasic() throws Exception
    {
        SimpleModule mod = new SimpleModule("test", Version.unknownVersion());
        // default is HashMap, so:
        mod.addAbstractTypeMapping(Map.class, TreeMap.class);
        ObjectMapper mapper = jsonMapperBuilder()
                .addModule(mod)
                .build();
        Map<?,?> result = mapper.readValue("{}", Map.class);
        assertEquals(TreeMap.class, result.getClass());
    }

    // [databind#700]
    @Test
    public void testDefaultingRecursive() throws Exception
    {
        SimpleModule mod = new SimpleModule("test", Version.unknownVersion());

        // defaults: LinkedHashMap, ArrayList
        mod.addAbstractTypeMapping(Map.class, TreeMap.class);
        mod.addAbstractTypeMapping(List.class, LinkedList.class);

        ObjectMapper mapper = jsonMapperBuilder()
                .addModule(mod)
                .build();
        Object result;

        result = mapper.readValue("[ {} ]", Object.class);
        assertEquals(LinkedList.class, result.getClass());
        Object v = ((List<?>) result).get(0);
        assertNotNull(v);
        assertEquals(TreeMap.class, v.getClass());

        result = mapper.readValue("{ \"x\": [ 3 ] }", Object.class);
        assertEquals(TreeMap.class, result.getClass());
        Map<?,?> map = (Map<?,?>) result;
        assertEquals(1, map.size());
        v = map.get("x");
        assertNotNull(v);
        assertEquals(LinkedList.class, v.getClass());
        assertEquals(1, ((List<?>) v).size());
    }

    @Test
    public void testInterfaceDefaulting() throws Exception
    {
        SimpleModule mod = new SimpleModule("test", Version.unknownVersion());
        // let's ensure we get hierarchic mapping
        mod.addAbstractTypeMapping(CharSequence.class, MyString.class);
        ObjectMapper mapper = jsonMapperBuilder()
                .addModule(mod)
                .build();
        Object result = mapper.readValue(q("abc"), CharSequence.class);
        assertEquals(MyString.class, result.getClass());
        assertEquals("abc", ((MyString) result).value);

        // and ditto for POJOs
        mod = new SimpleModule();
        mod.addAbstractTypeMapping(Abstract.class, AbstractImpl.class);
        mapper = jsonMapperBuilder()
                .addModule(mod)
                .build();
        Abstract a = mapper.readValue("{}", Abstract.class);
        assertNotNull(a);
    }

    // [databind#2019]: mappings from multiple modules
    @Test
    public void testAbstractMappingsFromTwoModules() throws Exception
    {
        SimpleModule module1 = new SimpleModule("module1");
        module1.addAbstractTypeMapping(Datatype1.class, SimpleDatatype1.class);

        SimpleModule module2 = new SimpleModule("module2");
        module2.addAbstractTypeMapping(Datatype2.class, SimpleDatatype2.class);

        ObjectMapper mapper = jsonMapperBuilder()
                .addModules(module1, module2)
                .build();

        final String JSON_EXAMPLE = "{\"value\": \"aaa\"}";
        Datatype1 value1 = mapper.readValue(JSON_EXAMPLE, Datatype1.class);
        assertNotNull(value1);

        Datatype2 value2 = mapper.readValue(JSON_EXAMPLE, Datatype2.class);
        assertNotNull(value2);
    }
}
