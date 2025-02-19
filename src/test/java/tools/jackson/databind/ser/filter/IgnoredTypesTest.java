package tools.jackson.databind.ser.filter;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.*;

import tools.jackson.databind.*;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.testutil.DatabindTestUtil;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for type-based ignoral, both via annotations (<code>JsonIgnoreType</code>)
 * and "config overrides" (2.8 and above).
 */
public class IgnoredTypesTest extends DatabindTestUtil
{
    @JsonIgnoreType
    class IgnoredType { // note: non-static, can't be deserialized
        public IgnoredType(IgnoredType src) { }
    }

    @JsonIgnoreType(false)
    static class NonIgnoredType
    {
        public int value = 13;
        public IgnoredType ignored;
    }

    // // And test for mix-in annotations

    @JsonIgnoreType
    static class Person {
        public String name;
        public Person() { }
        public Person(String name) {
            this.name = name;
        }
    }

    static class PersonWrapper {
        public int value = 1;
        public Person person = new Person("Foo");
    }

    @JsonIgnoreType
    static abstract class PersonMixin {
    }

    static class Wrapper {
        public int value = 3;
        public Wrapped wrapped = new Wrapped(7);
    }

    static class Wrapped {
        public int x;

        // make default ctor fail
        public Wrapped() { throw new RuntimeException("Should not be called"); }
        public Wrapped(int x0) { x = x0; }
    }

    // [databind#2893]
    @JsonIgnoreType
    interface IgnoreMe { }

    static class ChildOfIgnorable implements IgnoreMe {
        public int value = 42;
    }

    static class ContainsIgnorable {
        public ChildOfIgnorable ign = new ChildOfIgnorable();

        public int x = 13;
    }

    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    private final ObjectMapper MAPPER = newJsonMapper();

    @Test
    public void testIgnoredType() throws Exception
    {
        // First: should be ok in general, even though couldn't build deserializer (due to non-static inner class):
        NonIgnoredType bean = MAPPER.readValue("{\"value\":13}", NonIgnoredType.class);
        assertNotNull(bean);
        assertEquals(13, bean.value);

        // And also ok to see something with that value; will just get ignored
        bean = MAPPER.readValue("{ \"ignored\":[1,2,{}], \"value\":9 }", NonIgnoredType.class);
        assertNotNull(bean);
        assertEquals(9, bean.value);
    }

    @Test
    public void testSingleWithMixins() throws Exception {
        SimpleModule module = new SimpleModule();
        module.setMixInAnnotation(Person.class, PersonMixin.class);
        ObjectMapper mapper = jsonMapperBuilder()
                .addModule(module)
                .build();
        PersonWrapper input = new PersonWrapper();
        String json = mapper.writeValueAsString(input);
        assertEquals("{\"value\":1}", json);
    }

    @Test
    public void testListWithMixins() throws Exception {
        SimpleModule module = new SimpleModule();
        module.setMixInAnnotation(Person.class, PersonMixin.class);
        ObjectMapper mapper = jsonMapperBuilder()
                .addModule(module)
                .build();
        List<Person> persons = new ArrayList<Person>();
        persons.add(new Person("Bob"));
        String json = mapper.writeValueAsString(persons);
        assertEquals("[{\"name\":\"Bob\"}]", json);
    }

    @Test
    public void testIgnoreUsingConfigOverride() throws Exception
    {
        final ObjectMapper mapper = jsonMapperBuilder()
                .withConfigOverride(Wrapped.class,
                        o -> o.setIsIgnoredType(true))
                .build();

        // serialize , first
        String json = mapper.writeValueAsString(new Wrapper());
        assertEquals(a2q("{'value':3}"), json);

        // then deserialize
        Wrapper result = mapper.readValue(a2q("{'value':5,'wrapped':false}"),
                Wrapper.class);
        assertEquals(5, result.value);
    }

    // [databind#2893]
    @Test
    public void testIgnoreTypeViaInterface() throws Exception
    {
        assertEquals(a2q("{'x':13}"), MAPPER.writeValueAsString(new ContainsIgnorable()));
    }
}
