package tools.jackson.databind.jsontype.ext;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.testutil.DatabindTestUtil;
import tools.jackson.databind.testutil.NoCheckSubTypeValidator;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

// Tests for External type id, one that exists at same level as typed Object,
// that is, property is not within typed object but a member of its parent.
public class ExternalTypeId96Test extends DatabindTestUtil
{
    // for [databind#96]
    static class ExternalBeanWithDefault
    {
        @JsonTypeInfo(use=Id.CLASS, include=As.EXTERNAL_PROPERTY, property="extType",
                defaultImpl=ValueBean.class)
        public Object bean;

        public ExternalBeanWithDefault() { }
        public ExternalBeanWithDefault(int v) {
            bean = new ValueBean(v);
        }
    }

    @JsonTypeName("vbean")
    static class ValueBean {
        public int value;

        public ValueBean() { }
        public ValueBean(int v) { value = v; }
    }

    /*
    /**********************************************************************
    /* Unit tests, deserialization
    /**********************************************************************
     */

    // Note: 3.0 PTV is too strict, need to reconfig:
    private final ObjectMapper MAPPER = jsonMapperBuilder()
            .polymorphicTypeValidator(NoCheckSubTypeValidator.instance)
            .build();

    // For [databind#96]: should allow use of default impl, if property missing
    /* 18-Jan-2013, tatu: Unfortunately this collides with [databind#118], and I don't
     *   know what the best resolution is. For now at least
     */
    @Test
    public void testWithDefaultAndMissing() throws Exception
    {
        ExternalBeanWithDefault input = new ExternalBeanWithDefault(13);
        // baseline: include type, verify things work:
        String fullJson = MAPPER.writeValueAsString(input);
        ExternalBeanWithDefault output = MAPPER.readValue(fullJson, ExternalBeanWithDefault.class);
        assertNotNull(output);
        assertNotNull(output.bean);
        // and then try without type info...
        ExternalBeanWithDefault defaulted = MAPPER.readValue("{\"bean\":{\"value\":13}}",
                ExternalBeanWithDefault.class);
        assertNotNull(defaulted);
        assertNotNull(defaulted.bean);
        assertSame(ValueBean.class, defaulted.bean.getClass());
    }
}
