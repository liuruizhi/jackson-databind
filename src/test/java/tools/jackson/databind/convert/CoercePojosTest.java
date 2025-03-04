package tools.jackson.databind.convert;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.*;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.type.LogicalType;

import static org.junit.jupiter.api.Assertions.*;

import static tools.jackson.databind.testutil.DatabindTestUtil.*;

public class CoercePojosTest
{
    static class Bean {
        public String a;
    }

    static class BeanWithProp3676 {
        @JsonCreator
        public BeanWithProp3676(@JsonProperty("a") String a) {
            this.a = a;
        }

        public String a;
    }

    private final String JSON_EMPTY = q("");
    private final String JSON_BLANK = q("    ");

    private final ObjectMapper MAPPER = newJsonMapper();

    /*
    /********************************************************
    /* Test methods, from empty String
    /********************************************************
     */

    @Test
    public void testPOJOFromEmptyStringLegacy() throws Exception
    {
        // first, verify default settings which do not accept empty String:
        assertFalse(MAPPER.isEnabled(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT));

        // should be ok to enable dynamically
        _verifyFromEmptyPass(MAPPER.reader()
                .with(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT),
                JSON_EMPTY);

    }

    @Test
    public void testPOJOFromEmptyGlobalConfig() throws Exception
    {
        _testPOJOFromEmptyGlobalConfig(CoercionInputShape.EmptyString, JSON_EMPTY, null);
    }

    @Test
    public void testPOJOFromEmptyLogicalTypeConfig() throws Exception
    {
        _testPOJOFromEmptyLogicalTypeConfig(CoercionInputShape.EmptyString, JSON_EMPTY, null);
    }

    @Test
    public void testPOJOFromEmptyPhysicalTypeConfig() throws Exception
    {
        _testPOJOFromEmptyPhysicalTypeConfig(CoercionInputShape.EmptyString, JSON_EMPTY, null);
    }

    // [databind#3676] Alternative test for "Mode.PROPERTIES" variant where we
    //  have no "default" constructor
    @Test
    public void testPOJOFromEmptyWithProperties() throws Exception
    {
        // Then coerce as empty
        ObjectMapper mapper = jsonMapperBuilder()
                .withCoercionConfigDefaults(h -> {
                    h.setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsEmpty);
                })
            .build();
        BeanWithProp3676 b = mapper.readerFor(BeanWithProp3676.class)
                .readValue(JSON_EMPTY);
        assertNotNull(b);
        assertNull(b.a);
    }

    /*
    /********************************************************
    /* Test methods, from blank String
    /********************************************************
     */

    @Test
    public void testPOJOFromBlankGlobalConfig() throws Exception
    {
        _testPOJOFromEmptyGlobalConfig(CoercionInputShape.EmptyString, JSON_BLANK, Boolean.TRUE);
    }

    @Test
    public void testPOJOFromBlankLogicalTypeConfig() throws Exception
    {
        _testPOJOFromEmptyLogicalTypeConfig(CoercionInputShape.EmptyString, JSON_BLANK, Boolean.TRUE);
    }

    @Test
    public void testPOJOFromBlankPhysicalTypeConfig() throws Exception
    {
        _testPOJOFromEmptyPhysicalTypeConfig(CoercionInputShape.EmptyString, JSON_BLANK, Boolean.TRUE);
    }

    /*
    /********************************************************
    /* Second-level helper methods
    /********************************************************
     */

    private void _testPOJOFromEmptyGlobalConfig(final CoercionInputShape shape, final String json,
            Boolean allowEmpty)
        throws Exception
    {
        ObjectMapper mapper;

        // First, coerce to null
        mapper = jsonMapperBuilder()
                .withCoercionConfigDefaults(h -> {
                    h.setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsNull)
                        .setAcceptBlankAsEmpty(allowEmpty);
                })
                .build();
        assertNull(_verifyFromEmptyPass(mapper, JSON_EMPTY));

        // Then coerce as empty
        mapper = jsonMapperBuilder()
                .withCoercionConfigDefaults(h -> {
                    h.setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsEmpty)
                        .setAcceptBlankAsEmpty(allowEmpty);
                })
            .build();
        Bean b = _verifyFromEmptyPass(mapper, JSON_EMPTY);
        assertNotNull(b);

        // and finally, "try convert", which aliases to 'null'
        mapper = jsonMapperBuilder()
                .withCoercionConfigDefaults(h -> {
                    h.setCoercion(CoercionInputShape.EmptyString, CoercionAction.TryConvert)
                        .setAcceptBlankAsEmpty(allowEmpty);
                })
            .build();
        assertNull(_verifyFromEmptyPass(mapper, JSON_EMPTY));
    }

    private void _testPOJOFromEmptyLogicalTypeConfig(final CoercionInputShape shape, final String json,
            Boolean allowEmpty)
        throws Exception
    {
        ObjectMapper mapper;

        // First, coerce to null
        mapper = jsonMapperBuilder()
                .withCoercionConfig(LogicalType.POJO,
                        cfg -> cfg.setCoercion(shape, CoercionAction.AsNull)
                                .setAcceptBlankAsEmpty(allowEmpty))
                .build();
        assertNull(_verifyFromEmptyPass(mapper, json));

        // Then coerce as empty
        mapper = jsonMapperBuilder()
                .withCoercionConfig(LogicalType.POJO,
                        cfg -> cfg.setCoercion(shape, CoercionAction.AsEmpty)
                            .setAcceptBlankAsEmpty(allowEmpty))
                .build();

        Bean b = _verifyFromEmptyPass(mapper, json);
        assertNotNull(b);

        // But also make fail again with 2-level settings
        mapper = jsonMapperBuilder()
                .withCoercionConfigDefaults(h -> h.setCoercion(shape, CoercionAction.AsNull)
                        .setAcceptBlankAsEmpty(allowEmpty))
                .withCoercionConfig(LogicalType.POJO,
                        cfg -> cfg.setCoercion(shape, CoercionAction.Fail))
                .build();
        _verifyFromEmptyFail(mapper, json);
    }

    private void _testPOJOFromEmptyPhysicalTypeConfig(final CoercionInputShape shape, final String json,
            Boolean allowEmpty)
        throws Exception
    {
        ObjectMapper mapper;

        // First, coerce to null
        mapper = jsonMapperBuilder()
                .withCoercionConfig(Bean.class,
                        cfg -> cfg.setCoercion(shape, CoercionAction.AsNull)
                            .setAcceptBlankAsEmpty(allowEmpty))
                .build();
        assertNull(_verifyFromEmptyPass(mapper, json));

        // Then coerce as empty
        mapper = jsonMapperBuilder()
                .withCoercionConfig(Bean.class,
                        cfg -> cfg.setCoercion(shape, CoercionAction.AsEmpty)
                            .setAcceptBlankAsEmpty(allowEmpty))
                .build();

        Bean b = _verifyFromEmptyPass(mapper, json);
        assertNotNull(b);

        // But also make fail again with 2-level settings, with physical having precedence
        mapper = jsonMapperBuilder()
                .withCoercionConfig(LogicalType.POJO,
                        cfg -> cfg.setCoercion(shape, CoercionAction.AsEmpty)
                            .setAcceptBlankAsEmpty(allowEmpty))
                .withCoercionConfig(Bean.class,
                        cfg -> cfg.setCoercion(shape, CoercionAction.Fail))
                .build();
        _verifyFromEmptyFail(mapper, json);
    }

    private Bean _verifyFromEmptyPass(ObjectMapper m, String json) throws Exception {
        return _verifyFromEmptyPass(m.reader(), json);
    }

    private Bean _verifyFromEmptyPass(ObjectReader r, String json) throws Exception
    {
        return r.forType(Bean.class)
                .readValue(json);
    }

    private void _verifyFromEmptyFail(ObjectMapper m, String json) throws Exception
    {
        try {
            m.readValue(json, Bean.class);
            fail("Should not accept Empty/Blank String for POJO with passed settings");
        } catch (MismatchedInputException e) {
            _verifyFailMessage(e);
        }
    }

    private void _verifyFailMessage(JacksonException e)
    {
        // 06-Nov-2020, tatu: tests for failure get rather fragile unfortunately,
        //   but this seems to be what we should be getting

        verifyException(e, "Cannot coerce empty String");
//        verifyException(e, "Cannot deserialize value of type ");
//        verifyException(e, " from empty String ", " from blank String ");

        assertValidLocation(e.getLocation());
    }
}
