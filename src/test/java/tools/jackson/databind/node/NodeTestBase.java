package tools.jackson.databind.node;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.testutil.DatabindTestUtil;

import static org.junit.jupiter.api.Assertions.*;

abstract class NodeTestBase extends DatabindTestUtil
{
    protected void assertNodeNumbersForNonNumeric(JsonNode n)
    {
        assertFalse(n.isNumber());
        assertFalse(n.canConvertToInt());
        assertFalse(n.canConvertToLong());
        assertFalse(n.canConvertToExactIntegral());

        assertEquals(0, n.asInt());
        assertEquals(-42, n.asInt(-42));
        assertEquals(0, n.asLong());
        assertEquals(12345678901L, n.asLong(12345678901L));
        assertEquals(0.0, n.asDouble());
        assertEquals(-19.25, n.asDouble(-19.25));
    }

    // Test to check conversions, coercions
    protected void assertNodeNumbers(JsonNode n, int expInt, double expDouble)
    {
        assertEquals(expInt, n.asInt());
        assertEquals(expInt, n.asInt(-42));
        assertEquals((long) expInt, n.asLong());
        assertEquals((long) expInt, n.asLong(19L));
        assertEquals(expDouble, n.asDouble());
        assertEquals(expDouble, n.asDouble(-19.25));

        assertTrue(n.isEmpty());
    }

    // Testing for non-ContainerNode (ValueNode) stream method implementations
    //
    // @since 2.19
    protected void assertNonContainerStreamMethods(ValueNode n)
    {
        assertEquals(0, n.valueStream().count());
        assertEquals(0, n.propertyStream().count());

        // And then empty forEachEntry()
        n.forEachEntry((k, v) -> { throw new UnsupportedOperationException(); });
    }
}
