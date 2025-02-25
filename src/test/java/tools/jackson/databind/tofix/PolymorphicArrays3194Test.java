package tools.jackson.databind.tofix;

import org.junit.jupiter.api.Test;
import tools.jackson.core.testutil.failure.JacksonTestFailureExpected;

import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;
import tools.jackson.databind.testutil.DatabindTestUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;

// [databund#3194]: Discrepancy between Type Id inclusion on serialization vs
// expectation during deserialization causes mismatch and fails deserialization.
class PolymorphicArrays3194Test extends DatabindTestUtil {
    static final class SomeBean {
        public Object[][] value;
    }

    @JacksonTestFailureExpected
    @Test
    void twoDimensionalArrayMapping() throws Exception {
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubTypeIsArray()
                .allowIfSubType(Object.class)
                .build();

        ObjectMapper mapper = JsonMapper
                .builder()
                .activateDefaultTyping(typeValidator, DefaultTyping.NON_FINAL)
                .build();

        SomeBean instance = new SomeBean();
        instance.value = new String[][]{{"1.1", "1.2"}, {"2.1", "2.2"}};
        String json = mapper
//                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(instance);

        // Note: we'll see something like:
        //
//  {
//    "value" : [ "[[Ljava.lang.String;", [ [ "[Ljava.lang.String;", [ "1.1", "1.2" ] ], [ "[Ljava.lang.String;", [ "2.1", "2.2" ] ] ] ]
//  }

        // that is, type ids for both array levels.

// System.err.println("JSON:\n"+json);
        SomeBean result = mapper.readValue(json, SomeBean.class); // fails
        assertEquals(String[][].class, result.value.getClass());
        assertEquals(String[].class, result.value[0].getClass());
    }
}
