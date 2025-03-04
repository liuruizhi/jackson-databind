package tools.jackson.databind.tofix;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.core.testutil.failure.JacksonTestFailureExpected;

import com.fasterxml.jackson.annotation.*;

import tools.jackson.core.StreamWriteFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.testutil.DatabindTestUtil;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

// Test case for https://github.com/FasterXML/jackson-databind/issues/1298
class ObjectIdWithUnwrapping1298Test extends DatabindTestUtil {
    static Long nextId = 1L;

    public static final class ListOfParents {
        public List<Parent> parents = new ArrayList<>();

        public void addParent(Parent parent) {
            parents.add(parent);
        }
    }

    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id", scope = Parent.class)
    public static final class Parent {
        public Long id;

        @JsonUnwrapped
        public Child child;

        public Parent() {
            this.id = nextId++;
        }
    }

    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id", scope = Child.class)
    public static final class Child {
        public Long id;

        public final String name;

        public Child(@JsonProperty("name") String name) {
            this.name = name;
            this.id = ObjectIdWithUnwrapping1298Test.nextId++;
        }
    }

    @JacksonTestFailureExpected
    @Test
    void objectIdWithRepeatedChild() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper(JsonFactory.builder()
                .disable(StreamWriteFeature.AUTO_CLOSE_CONTENT).build());
        // to keep output faithful to original, prevent auto-closing...

        // Equivalent to Spring _embedded for Bean w/ List property
        ListOfParents parents = new ListOfParents();

        //Bean with Relationship
        Parent parent1 = new Parent();
        Child child1 = new Child("Child1");
        parent1.child = child1;
        parents.addParent(parent1);

        // serialize parent1 and parent2
        String json = mapper
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(parents);
        assertNotNull(json);
//        System.out.println("This works: " + json);

        // Add parent3 to create ObjectId reference
        // Bean w/ repeated relationship from parent1, should generate ObjectId
        Parent parent3 = new Parent();
        parent3.child = child1;
        parents.addParent(parent3);
        StringWriter sw = new StringWriter();

        try {
            mapper
//                .writerWithDefaultPrettyPrinter()
                    .writeValue(sw, parents);
        } catch (Exception e) {
            fail("Failed with " + e.getClass().getName() + ", output so far: " + sw);
        }
    }
}
