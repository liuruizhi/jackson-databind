package tools.jackson.databind.contextual;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JacksonAnnotation;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.*;
import tools.jackson.databind.annotation.JsonDeserialize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ContextualWithAnnDeserializerTest
{
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @JacksonAnnotation
    public @interface Name {
        public String value();
    }

    static class StringValue {
        protected String value;

        public StringValue(String v) { value = v; }
    }

    static class AnnotatedContextualClassBean
    {
        @Name("xyz")
        @JsonDeserialize(using=AnnotatedContextualDeserializer.class)
        public StringValue value;
    }

    static class AnnotatedContextualDeserializer
        extends ValueDeserializer<StringValue>
    {
        protected final String _fieldName;

        public AnnotatedContextualDeserializer() { this(""); }
        public AnnotatedContextualDeserializer(String fieldName) {
            _fieldName = fieldName;
        }

        @Override
        public StringValue deserialize(JsonParser p, DeserializationContext ctxt)
        {
            return new StringValue(""+_fieldName+"="+p.getString());
        }

        @Override
        public ValueDeserializer<?> createContextual(DeserializationContext ctxt,
                BeanProperty property)
        {
            Name ann = property.getAnnotation(Name.class);
            if (ann == null) {
                ann = property.getContextAnnotation(Name.class);
            }
            String propertyName = (ann == null) ?  "UNKNOWN" : ann.value();
            return new AnnotatedContextualDeserializer(propertyName);
        }
    }

    // ensure that direct associations also work
    @Test
    public void testAnnotatedContextual() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        AnnotatedContextualClassBean bean = mapper.readValue(
                "{\"value\":\"a\"}",
              AnnotatedContextualClassBean.class);
        assertNotNull(bean);
        assertEquals("xyz=a", bean.value.value);
    }
}
