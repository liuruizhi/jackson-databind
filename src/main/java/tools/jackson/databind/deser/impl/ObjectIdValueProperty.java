package tools.jackson.databind.deser.impl;

import java.lang.annotation.Annotation;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.*;
import tools.jackson.databind.deser.*;
import tools.jackson.databind.introspect.AnnotatedMember;

/**
 * Specialized {@link SettableBeanProperty} implementation used
 * for virtual property that represents Object Id that is used
 * for some POJO types (or properties).
 */
public final class ObjectIdValueProperty
    extends SettableBeanProperty
{
    private static final long serialVersionUID = 1L;

    protected final ObjectIdReader _objectIdReader;

    public ObjectIdValueProperty(ObjectIdReader objectIdReader,
            PropertyMetadata metadata)
    {
        super(objectIdReader.propertyName, objectIdReader.getIdType(), metadata,
                objectIdReader.getDeserializer());
        _objectIdReader = objectIdReader;
    }

    protected ObjectIdValueProperty(ObjectIdValueProperty src, ValueDeserializer<?> deser,
            NullValueProvider nva)
    {
        super(src, deser, nva);
        _objectIdReader = src._objectIdReader;
    }

    protected ObjectIdValueProperty(ObjectIdValueProperty src, PropertyName newName) {
        super(src, newName);
        _objectIdReader = src._objectIdReader;
    }

    @Override
    public SettableBeanProperty withName(PropertyName newName) {
        return new ObjectIdValueProperty(this, newName);
    }

    @Override
    public SettableBeanProperty withValueDeserializer(ValueDeserializer<?> deser) {
        if (_valueDeserializer == deser) {
            return this;
        }
        // 07-May-2019, tatu: As per [databind#2303], must keep VD/NVP in-sync if they were
        NullValueProvider nvp = (_valueDeserializer == _nullProvider) ? deser : _nullProvider;
        return new ObjectIdValueProperty(this, deser, nvp);
    }

    @Override
    public SettableBeanProperty withNullProvider(NullValueProvider nva) {
        return new ObjectIdValueProperty(this, _valueDeserializer, nva);
    }

    // // // BeanProperty impl

    @Override
    public <A extends Annotation> A getAnnotation(Class<A> acls) {
        return null;
    }

    @Override public AnnotatedMember getMember() {  return null; }

    /*
    /**********************************************************
    /* Deserialization methods
    /**********************************************************
     */

    @Override
    public void deserializeAndSet(JsonParser p, DeserializationContext ctxt,
            Object instance) throws JacksonException
    {
        deserializeSetAndReturn(p, ctxt, instance);
    }

    @Override
    public Object deserializeSetAndReturn(JsonParser p,
    		DeserializationContext ctxt, Object instance) throws JacksonException
    {
        /* 02-Apr-2015, tatu: Actually, as per [databind#742], let it be;
         *  missing or null id is needed for some cases, such as cases where id
         *  will be generated externally, at a later point, and is not available
         *  quite yet. Typical use case is with DB inserts.
         */
        // note: no null checks (unlike usually); deserializer should fail if one found
        if (p.hasToken(JsonToken.VALUE_NULL)) {
            return null;
        }
        Object id = _valueDeserializer.deserialize(p, ctxt);
        ReadableObjectId roid = ctxt.findObjectId(id, _objectIdReader.generator, _objectIdReader.resolver);
        roid.bindItem(ctxt, instance);
        // also: may need to set a property value as well
        SettableBeanProperty idProp = _objectIdReader.idProperty;
        if (idProp != null) {
            return idProp.setAndReturn(ctxt, instance, id);
        }
        return instance;
    }

    @Override
    public void set(DeserializationContext ctxt,Object instance, Object value) {
        setAndReturn(ctxt, instance, value);
    }

    @Override
    public Object setAndReturn(DeserializationContext ctxt, Object instance, Object value)
    {
        SettableBeanProperty idProp = _objectIdReader.idProperty;
        if (idProp == null) {
            throw new UnsupportedOperationException(
                    "Should not call set() on ObjectIdProperty that has no SettableBeanProperty");
        }
        return idProp.setAndReturn(ctxt, instance, value);
    }
}
