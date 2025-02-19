package tools.jackson.databind.jsontype.jdk;

import java.util.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.testutil.DatabindTestUtil;

import static org.junit.jupiter.api.Assertions.*;

public class TypedContainerSerTest
	extends DatabindTestUtil
{
    @JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "object-type")
    @JsonSubTypes( { @Type(value = Dog.class, name = "doggy"),
        @Type(value = Cat.class, name = "kitty") })
    static abstract class Animal {
	    public String name;

	    protected Animal(String n) {
	        name = n;
	    }
	}

	@JsonTypeName("doggie")
	static class Dog extends Animal {
		public int boneCount;

		public Dog() {
			super(null);
		}

		@JsonCreator
		public Dog(@JsonProperty("name") String name) {
			super(name);
		}

		public void setBoneCount(int i) {
			boneCount = i;
		}
	}

	@JsonTypeName("kitty")
	static class Cat extends Animal {
		public String furColor;

		public Cat() {
			super(null);
		}

		@JsonCreator
		public Cat(@JsonProperty("furColor") String c) {
			super(null);
			furColor = c;
		}

		public void setName(String n) {
			name = n;
		}
	}

	static class Container1 {
		Animal animal;

		public Animal getAnimal() {
			return animal;
		}

		public void setAnimal(Animal animal) {
			this.animal = animal;
		}
	}

	static class Container2<T extends Animal> {
		@JsonSerialize
		T animal;

		public T getAnimal() {
			return animal;
		}

		public void setAnimal(T animal) {
			this.animal = animal;
		}

	}

    @JsonTypeInfo(use=JsonTypeInfo.Id.CLASS, include=JsonTypeInfo.As.PROPERTY, property="@class")
    static class Issue508A { }
    static class Issue508B extends Issue508A { }

    private final static ObjectMapper mapper = new ObjectMapper();

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

	@Test
    public void testPolymorphicWithContainer() throws Exception
    {
		Dog dog = new Dog("medor");
		dog.setBoneCount(3);
		Container1 c1 = new Container1();
		c1.setAnimal(dog);
		String s1 = mapper.writeValueAsString(c1);
		assertTrue(s1.indexOf("\"object-type\":\"doggy\"") >= 0,
				"polymorphic type info is kept (1)");
		Container2<Animal> c2 = new Container2<Animal>();
		c2.setAnimal(dog);
		String s2 = mapper.writeValueAsString(c2);
		assertTrue(s2.indexOf("\"object-type\":\"doggy\"") >= 0,
				"polymorphic type info is kept (2)");
    }

	@Test
    public void testIssue329() throws Exception
    {
        ArrayList<Animal> animals = new ArrayList<Animal>();
        animals.add(new Dog("Spot"));
        JavaType rootType = mapper.getTypeFactory().constructParametricType(Iterator.class, Animal.class);
        String json = mapper.writerFor(rootType).writeValueAsString(animals.iterator());
        if (json.indexOf("\"object-type\":\"doggy\"") < 0) {
            fail("No polymorphic type retained, should be; JSON = '"+json+"'");
        }
    }

	@Test
    public void testIssue508() throws Exception
    {
        List<List<Issue508A>> l = new ArrayList<List<Issue508A>>();
        List<Issue508A> l2 = new ArrayList<Issue508A>();
        l2.add(new Issue508A());
        l.add(l2);
        TypeReference<List<List<Issue508A>>> typeRef = new TypeReference<List<List<Issue508A>>>() {};
        String json = mapper.writerFor(typeRef).writeValueAsString(l);

        List<?> output = mapper.readValue(json, typeRef);
        assertEquals(1, output.size());
        Object ob = output.get(0);
        assertTrue(ob instanceof List<?>);
        List<?> list2 = (List<?>) ob;
        assertEquals(1, list2.size());
        ob = list2.get(0);
        assertSame(Issue508A.class, ob.getClass());
    }
}
