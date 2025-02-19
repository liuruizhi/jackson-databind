package tools.jackson.databind.records;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.ConstructorDetector;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.testutil.DatabindTestUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RecordImplicitSingleValueUsePropertiesBasedCreatorsTest extends DatabindTestUtil
{

    record RecordWithMultiValueCanonAndSingleValueAltConstructor(int id, String name) {

        public RecordWithMultiValueCanonAndSingleValueAltConstructor(int id) {
            this(id, "AltConstructor");
        }
    }

    record RecordWithSingleValueCanonAndMultiValueAltConstructor(String name) {

        public RecordWithSingleValueCanonAndMultiValueAltConstructor(String name, String email) {
            this("AltConstructor");
        }
    }

    private final ObjectMapper MAPPER = jsonMapperBuilder()
            .constructorDetector(ConstructorDetector.USE_PROPERTIES_BASED)
            .build();

    /*
    /**********************************************************************
    /* Test methods, multi-value canonical constructor + single-value alt constructor
    /**********************************************************************
     */

    @Test
    public void testDeserializeMultipleConstructors_UsingMultiValueCanonicalConstructor() throws Exception {
        RecordWithMultiValueCanonAndSingleValueAltConstructor value = MAPPER.readValue(
                a2q("{'id':123,'name':'Bob'}"),
                RecordWithMultiValueCanonAndSingleValueAltConstructor.class);

        assertEquals(new RecordWithMultiValueCanonAndSingleValueAltConstructor(123, "Bob"), value);
    }

    @Test
    public void testDeserializeMultipleConstructors_WillIgnoreSingleValueAltConstructor() throws Exception {
        RecordWithMultiValueCanonAndSingleValueAltConstructor value = MAPPER.readValue(
                a2q("{'id':123}"),
                RecordWithMultiValueCanonAndSingleValueAltConstructor.class);

        assertEquals(new RecordWithMultiValueCanonAndSingleValueAltConstructor(123, null), value);
    }

    /*
    /**********************************************************************
    /* Test methods, single-value canonical constructor + multi-value alt constructor
    /**********************************************************************
     */

    @Test
    public void testDeserializeMultipleConstructors_UsingSingleValueCanonicalConstructor() throws Exception {
        RecordWithSingleValueCanonAndMultiValueAltConstructor value = MAPPER.readValue(
                a2q("{'name':'Bob'}"),
                RecordWithSingleValueCanonAndMultiValueAltConstructor.class);

        assertEquals(new RecordWithSingleValueCanonAndMultiValueAltConstructor("Bob"), value);
    }

    @Test
    public void testDeserializeMultipleConstructors_WillIgnoreMultiValueAltConstructor() throws Exception {
        try {
            MAPPER.readValue(a2q("{'name':'Bob','email':'bob@email.com'}"), RecordWithSingleValueCanonAndMultiValueAltConstructor.class);
        } catch (UnrecognizedPropertyException e) {
            verifyException(e, "Unrecognized");
            verifyException(e, "\"email\"");
            verifyException(e, "RecordWithSingleValueCanonAndMultiValueAltConstructor");
        }
    }
}
