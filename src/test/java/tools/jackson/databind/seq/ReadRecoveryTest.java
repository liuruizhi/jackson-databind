package tools.jackson.databind.seq;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.*;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.testutil.DatabindTestUtil;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests to verify aspects of error recover for reading using
 * iterator.
 */
public class ReadRecoveryTest extends DatabindTestUtil
{
    static class Bean {
        public int a, b;

        @Override public String toString() { return "{Bean, a="+a+", b="+b+"}"; }
    }

    /*
    /**********************************************************************
    /* Unit tests; root-level value sequences via Mapper
    /**********************************************************************
     */

    private final ObjectMapper MAPPER = newJsonMapper();

    @Test
    public void testRootBeans() throws Exception
    {
        final String JSON = a2q("{'a':3} {'x':5}");
        MappingIterator<Bean> it = MAPPER.readerFor(Bean.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValues(JSON);
        // First one should be fine
        assertTrue(it.hasNextValue());
        Bean bean = it.nextValue();
        assertEquals(3, bean.a);
        // but second one not
        try {
            bean = it.nextValue();
            fail("Should not have succeeded");
        } catch (UnrecognizedPropertyException e) {
            verifyException(e, "Unrecognized property \"x\"");
        }
        // 21-May-2015, tatu: With [databind#734], recovery, we now know there's no more data!
        assertFalse(it.hasNextValue());

        it.close();
    }

    // for [databind#734]
    // Simple test for verifying that basic recover works for a case of
    // unknown structured value
    @Test
    public void testSimpleRootRecovery() throws Exception
    {
        final String JSON = a2q("{'a':3}{'a':27,'foo':[1,2],'b':{'x':3}}  {'a':1,'b':2} ");

        MappingIterator<Bean> it = MAPPER.readerFor(Bean.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValues(JSON);
        Bean bean = it.nextValue();

        assertNotNull(bean);
        assertEquals(3, bean.a);

        // second one problematic
        try {
            it.nextValue();
        } catch (UnrecognizedPropertyException e) {
            verifyException(e, "Unrecognized property \"foo\"");
        }

        // but should recover nicely
        bean = it.nextValue();
        assertNotNull(bean);
        assertEquals(1, bean.a);
        assertEquals(2, bean.b);

        assertFalse(it.hasNextValue());

        it.close();
    }

    // Similar to "raw" root-level Object sequence, but in array
    @Test
    public void testSimpleArrayRecovery() throws Exception
    {
        final String JSON = a2q("[{'a':3},{'a':27,'foo':[1,2],'b':{'x':3}}  ,{'a':1,'b':2}  ]");

        MappingIterator<Bean> it = MAPPER.readerFor(Bean.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValues(JSON);
        Bean bean = it.nextValue();

        assertNotNull(bean);
        assertEquals(3, bean.a);

        // second one problematic
        try {
            it.nextValue();
        } catch (UnrecognizedPropertyException e) {
            verifyException(e, "Unrecognized property \"foo\"");
        }

        // but should recover nicely
        bean = it.nextValue();
        assertNotNull(bean);
        assertEquals(1, bean.a);
        assertEquals(2, bean.b);

        assertFalse(it.hasNextValue());

        it.close();
    }
}
