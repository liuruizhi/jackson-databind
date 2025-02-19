package tools.jackson.databind.ser;

import java.util.Map;

import tools.jackson.core.*;
import tools.jackson.databind.*;
import tools.jackson.databind.introspect.AnnotatedMember;
import tools.jackson.databind.ser.jdk.MapSerializer;

/**
 * Class similar to {@link BeanPropertyWriter}, but that will be used
 * for serializing {@link com.fasterxml.jackson.annotation.JsonAnyGetter} annotated
 * (Map) properties
 */
public class AnyGetterWriter
{
    protected final BeanProperty _property;

    /**
     * Method (or field) that represents the "any getter"
     */
    protected final AnnotatedMember _accessor;

    protected ValueSerializer<Object> _serializer;

    protected MapSerializer _mapSerializer;

    @SuppressWarnings("unchecked")
    public AnyGetterWriter(BeanProperty property,
            AnnotatedMember accessor, ValueSerializer<?> serializer)
    {
        _accessor = accessor;
        _property = property;
        _serializer = (ValueSerializer<Object>) serializer;
        if (serializer instanceof MapSerializer) {
            _mapSerializer = (MapSerializer) serializer;
        }
    }

    public void fixAccess(SerializationConfig config) {
        _accessor.fixAccess(
                config.isEnabled(MapperFeature.OVERRIDE_PUBLIC_ACCESS_MODIFIERS));
    }

    // Note: NOT part of ResolvableSerializer...
    @SuppressWarnings("unchecked")
    public void resolve(SerializationContext provider)
    {
        // 05-Sep-2013, tatu: I _think_ this can be considered a primary property...
        ValueSerializer<?> ser = provider.handlePrimaryContextualization(_serializer, _property);
        _serializer = (ValueSerializer<Object>) ser;
        if (ser instanceof MapSerializer) {
            _mapSerializer = (MapSerializer) ser;
        }
    }

    public void getAndSerialize(Object bean, JsonGenerator gen, SerializationContext provider)
        throws Exception
    {
        Object value = _accessor.getValue(bean);
        if (value == null) {
            return;
        }
        if (!(value instanceof Map<?,?>)) {
            provider.reportBadDefinition(_property.getType(), String.format(
                    "Value returned by 'any-getter' %s() not java.util.Map but %s",
                    _accessor.getName(), value.getClass().getName()));
        }
        // 23-Feb-2015, tatu: Nasty, but has to do (for now)
        if (_mapSerializer != null) {
            _mapSerializer.serializeWithoutTypeInfo((Map<?,?>) value, gen, provider);
            return;
        }
        _serializer.serialize(value, gen, provider);
    }

    public void getAndFilter(Object bean, JsonGenerator gen, SerializationContext provider,
            PropertyFilter filter)
        throws Exception
    {
        Object value = _accessor.getValue(bean);
        if (value == null) {
            return;
        }
        if (!(value instanceof Map<?,?>)) {
            provider.reportBadDefinition(_property.getType(),
                    String.format("Value returned by 'any-getter' (%s()) not java.util.Map but %s",
                    _accessor.getName(), value.getClass().getName()));
        }
        // 19-Oct-2014, tatu: Should we try to support @JsonInclude options here?
        if (_mapSerializer != null) {
            _mapSerializer.serializeFilteredAnyProperties(provider, gen, bean,(Map<?,?>) value,
                    filter, null);
            return;
        }
        // ... not sure how custom handler would do it
        _serializer.serialize(value, gen, provider);
    }
}
