package tools.jackson.databind.ext.xml;

import javax.xml.datatype.*;
import javax.xml.namespace.QName;
import org.junit.jupiter.api.Test;

import tools.jackson.core.json.JsonWriteFeature;
import tools.jackson.databind.*;
import tools.jackson.databind.testutil.DatabindTestUtil;
import tools.jackson.databind.testutil.NoCheckSubTypeValidator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Core XML types (javax.xml) are considered "external" (or more precisely "optional")
 * since some Java(-like) platforms do not include them: specifically, Google AppEngine
 * and Android seem to skimp on their inclusion. As such, they are dynamically loaded
 * only as needed, and need bit special handling.
 */
public class MiscJavaXMLTypesReadWriteTest
    extends DatabindTestUtil
{
    /*
    /**********************************************************************
    /* Serializer tests
    /**********************************************************************
     */

    private final ObjectMapper MAPPER = newJsonMapper();
    private final ObjectMapper POLY_MAPPER = jsonMapperBuilder()
            .activateDefaultTyping(NoCheckSubTypeValidator.instance,
                    DefaultTyping.NON_FINAL)
            .build();

    @Test
    public void testQNameSer() throws Exception
    {
        QName qn = new QName("http://abc", "tag", "prefix");
        assertEquals(q(qn.toString()),
                MAPPER.writer()
                    .without(JsonWriteFeature.ESCAPE_FORWARD_SLASHES)
                    .writeValueAsString(qn));
    }

    @Test
    public void testDurationSer() throws Exception
    {
        DatatypeFactory dtf = DatatypeFactory.newInstance();
        // arbitrary value
        Duration dur = dtf.newDurationDayTime(false, 15, 19, 58, 1);
        assertEquals(q(dur.toString()), MAPPER.writeValueAsString(dur));
    }

    @Test
    public void testXMLGregorianCalendarSerAndDeser() throws Exception
    {
        DatatypeFactory dtf = DatatypeFactory.newInstance();
        XMLGregorianCalendar cal = dtf.newXMLGregorianCalendar
            (1974, 10, 10, 18, 15, 17, 123, 0);

        long timestamp = cal.toGregorianCalendar().getTimeInMillis();
        String numStr = String.valueOf(timestamp);
        assertEquals(numStr, MAPPER.writeValueAsString(cal));

        // [JACKSON-403] Needs to come back ok as well:
        XMLGregorianCalendar calOut = MAPPER.readValue(numStr, XMLGregorianCalendar.class);
        assertNotNull(calOut);
        assertEquals(timestamp, calOut.toGregorianCalendar().getTimeInMillis());

        ObjectMapper mapper = new ObjectMapper();
        // and then textual variant
        // this is ALMOST same as default for XMLGregorianCalendar... just need to unify Z/+0000
        String exp = cal.toXMLFormat();
        String act = mapper.writer()
                .without(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .writeValueAsString(cal);
        act = act.substring(1, act.length() - 1); // remove quotes
        exp = removeZ(exp);
        act = removeZ(act);
        assertEquals(exp, act);
    }

    private String removeZ(String dateStr) {
        if (dateStr.endsWith("Z")) {
            return dateStr.substring(0, dateStr.length()-1);
        }
        if (dateStr.endsWith("+00:00")) {
            return dateStr.substring(0, dateStr.length()-6);
        }
        return dateStr;
    }

    /*
    /**********************************************************************
    /* Deserializer tests
    /**********************************************************************
     */

    @Test
    public void testQNameDeser() throws Exception
    {
        QName qn = new QName("http://abc", "tag", "prefix");
        String qstr = qn.toString();
        assertEquals(qn, MAPPER.readValue(q(qstr), QName.class),
            "Should deserialize to equal QName (exp serialization: '"+qstr+"')");

        // [databind#4450]
        qn = MAPPER.readValue(q(""), QName.class);
        assertNotNull(qn);
        assertEquals("", qn.getLocalPart());
    }

    @Test
    public void testXMLGregorianCalendarDeser() throws Exception
    {
        DatatypeFactory dtf = DatatypeFactory.newInstance();
        XMLGregorianCalendar cal = dtf.newXMLGregorianCalendar
            (1974, 10, 10, 18, 15, 17, 123, 0);
        String exp = cal.toXMLFormat();
        assertEquals(cal, MAPPER.readValue(q(exp), XMLGregorianCalendar.class),
            "Should deserialize to equal XMLGregorianCalendar ('"+exp+"')");
    }

    @Test
    public void testDurationDeser() throws Exception
    {
        DatatypeFactory dtf = DatatypeFactory.newInstance();
        // arbitrary value, like... say, 27d5h15m59s
        Duration dur = dtf.newDurationDayTime(true, 27, 5, 15, 59);
        String exp = dur.toString();
        assertEquals(dur, MAPPER.readValue(q(exp), Duration.class),
            "Should deserialize to equal Duration ('"+exp+"')");
    }

    /*
    /**********************************************************************
    /* Polymorphic handling tests
    /**********************************************************************
     */

    @Test
    public void testPolymorphicXMLGregorianCalendar() throws Exception
    {
        XMLGregorianCalendar cal = DatatypeFactory.newInstance().newXMLGregorianCalendar
                (1974, 10, 10, 18, 15, 17, 123, 0);
        String json = POLY_MAPPER.writeValueAsString(cal);
        Object result = POLY_MAPPER.readValue(json, Object.class);
        if (!(result instanceof XMLGregorianCalendar)) {
            fail("Expected a `XMLGregorianCalendar`, got: "+result.getClass());
        }
        assertEquals(cal, result);
    }
}
