package tools.jackson.databind.ext.sql;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.*;
import tools.jackson.databind.testutil.DatabindTestUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SqlTimestampDeserializationTest
    extends DatabindTestUtil
{
    private final ObjectMapper MAPPER = newJsonMapper();

    // As for TestDateDeserialization except we don't need to test date conversion routines, so
    // just check we pick up timestamp class

    @Test
    public void testTimestampUtil() throws Exception
    {
        long now = 123456789L;
        java.sql.Timestamp value = new java.sql.Timestamp(now);

        // First from long
        assertEquals(value, MAPPER.readValue(""+now, java.sql.Timestamp.class));

        String dateStr = serializeTimestampAsString(value);
        java.sql.Timestamp result = MAPPER.readValue("\""+dateStr+"\"", java.sql.Timestamp.class);

        assertEquals(value.getTime(), result.getTime(),
            "Date: expect "+value+" ("+value.getTime()+"), got "+result+" ("+result.getTime()+")");
    }

    @Test
    public void testTimestampUtilSingleElementArray() throws Exception
    {
        final ObjectReader r = MAPPER.readerFor(java.sql.Timestamp.class)
                .with(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS);

        long now = System.currentTimeMillis();
        java.sql.Timestamp value = new java.sql.Timestamp(now);

        // First from long
        assertEquals(value, r.readValue("["+now+"]"));

        String dateStr = serializeTimestampAsString(value);
        java.sql.Timestamp result = r.readValue("[\""+dateStr+"\"]");

        assertEquals(value.getTime(), result.getTime(),
            "Date: expect "+value+" ("+value.getTime()+"), got "+result+" ("+result.getTime()+")");
    }

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */

    private String serializeTimestampAsString(java.sql.Timestamp value)
    {
        /* Then from String. This is bit tricky, since JDK does not really
         * suggest a 'standard' format. So let's try using something...
         */
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        return df.format(value);
    }
}
