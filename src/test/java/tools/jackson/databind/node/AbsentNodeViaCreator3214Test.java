package tools.jackson.databind.node;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.*;
import tools.jackson.databind.testutil.DatabindTestUtil;

import static org.junit.jupiter.api.Assertions.fail;

public class AbsentNodeViaCreator3214Test extends DatabindTestUtil
{
    static class Pojo3214
    {
        JsonNode fromCtor = StringNode.valueOf("x");
        JsonNode fromSetter = StringNode.valueOf("x");

        @JsonCreator
        public Pojo3214(@JsonProperty("node") JsonNode n) {
            this.fromCtor = n;
        }

        public void setNodeFromSetter(JsonNode nodeFromSetter) {
            this.fromSetter = nodeFromSetter;
        }
    }

    private final ObjectMapper MAPPER = newJsonMapper();

    // [databind#3214]
    @Test
    public void testNullFromMissingNodeParameter() throws Exception
    {
        Pojo3214 p = MAPPER.readValue("{}", Pojo3214.class);
        if (p.fromCtor != null) {
            fail("Expected null to be passed, got instance of "+p.fromCtor.getClass());
        }
    }
}
