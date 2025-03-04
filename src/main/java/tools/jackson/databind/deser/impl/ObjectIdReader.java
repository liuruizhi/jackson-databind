package tools.jackson.databind.deser.impl;

import com.fasterxml.jackson.annotation.ObjectIdGenerator;
import com.fasterxml.jackson.annotation.ObjectIdResolver;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.*;
import tools.jackson.databind.deser.SettableBeanProperty;

/**
 * Object that knows how to deserialize Object Ids.
 */
public class ObjectIdReader
    implements java.io.Serializable
{
    private static final long serialVersionUID = 1L;

    protected final JavaType _idType;

    public final PropertyName propertyName;

    /**
     * Blueprint generator instance: actual instance will be
     * fetched from {@link SerializationContext} using this as
     * the key.
     */
    public final ObjectIdGenerator<?> generator;

    public final ObjectIdResolver resolver;

    /**
     * Deserializer used for deserializing id values.
     */
    protected final ValueDeserializer<Object> _deserializer;

    public final SettableBeanProperty idProperty;

    /*
    /**********************************************************
    /* Life-cycle
    /**********************************************************
     */

    @SuppressWarnings("unchecked")
    protected ObjectIdReader(JavaType t, PropertyName propName, ObjectIdGenerator<?> gen,
            ValueDeserializer<?> deser, SettableBeanProperty idProp, ObjectIdResolver resolver)
    {
        _idType = t;
        propertyName = propName;
        generator = gen;
        this.resolver = resolver;
        _deserializer = (ValueDeserializer<Object>) deser;
        idProperty = idProp;
    }

    /**
     * Factory method called by {@link tools.jackson.databind.ser.bean.BeanSerializerBase}
     * with the initial information based on standard settings for the type
     * for which serializer is being built.
     */
    public static ObjectIdReader construct(JavaType idType, PropertyName propName,
            ObjectIdGenerator<?> generator, ValueDeserializer<?> deser,
            SettableBeanProperty idProp, ObjectIdResolver resolver)
    {
        return new ObjectIdReader(idType, propName, generator, deser, idProp, resolver);
    }

    /*
    /**********************************************************
    /* API
    /**********************************************************
     */

    public ValueDeserializer<Object> getDeserializer() {
        return _deserializer;
    }

    public JavaType getIdType() {
        return _idType;
    }

    /**
     * Convenience method, equivalent to calling:
     *<code>
     *  readerInstance.generator.maySerializeAsObject();
     *</code>
     * and used to determine whether Object Ids handled by the underlying
     * generator may be in form of (JSON) Objects.
     * Used for optimizing handling in cases where method returns false.
     */
    public boolean maySerializeAsObject() {
        return generator.maySerializeAsObject();
    }

    /**
     * Convenience method, equivalent to calling:
     *<code>
     *  readerInstance.generator.isValidReferencePropertyName(name, parser);
     *</code>
     * and used to determine whether Object Ids handled by the underlying
     * generator may be in form of (JSON) Objects.
     * Used for optimizing handling in cases where method returns false.
     */
    public boolean isValidReferencePropertyName(String name, JsonParser parser) {
        return generator.isValidReferencePropertyName(name, parser);
    }

    /**
     * Method called to read value that is expected to be an Object Reference
     * (that is, value of an Object Id used to refer to another object).
     */
    public Object readObjectReference(JsonParser p, DeserializationContext ctxt)
        throws JacksonException
    {
        return _deserializer.deserialize(p, ctxt);
    }
}
