package tools.jackson.databind.util;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.SerializableString;
import tools.jackson.databind.JacksonSerializable;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.jsontype.TypeSerializer;

/**
 * Helper class used to encapsulate "raw values", pre-encoded textual content
 * that can be output as opaque value with no quoting/escaping, using
 * {@link tools.jackson.core.JsonGenerator#writeRawValue(String)}.
 * It may be stored in {@link TokenBuffer}, as well as in Tree Model
 * ({@link tools.jackson.databind.JsonNode})
 */
public class RawValue
    implements JacksonSerializable
{
    /**
     * Contents to serialize. Untyped because there are multiple types that are
     * supported: {@link java.lang.String}, {@link JacksonSerializable}, {@link SerializableString}.
     */
    protected Object _value;

    public RawValue(String v) {
        _value = v;
    }

    public RawValue(SerializableString v) {
        _value = v;
    }

    public RawValue(JacksonSerializable v) {
        _value = v;
    }

    /**
     * Constructor that may be used by sub-classes, and allows passing value
     * types other than ones for which explicit constructor exists. Caller has to
     * take care that values of types not supported by base implementation are
     * handled properly, usually by overriding some of existing serialization
     * methods.
     */
    protected RawValue(Object value, boolean bogus) {
        _value = value;
    }

    /**
     * Accessor for returning enclosed raw value in whatever form it was created in
     * (usually {@link java.lang.String}, {link SerializableString}, or any {@link JacksonSerializable}).
     */
    public Object rawValue() {
        return _value;
    }

    @Override
    public void serialize(JsonGenerator gen, SerializationContext serializers) throws JacksonException
    {
        if (_value instanceof JacksonSerializable) {
            ((JacksonSerializable) _value).serialize(gen, serializers);
        } else {
            _serialize(gen);
        }
    }

    @Override
    public void serializeWithType(JsonGenerator gen, SerializationContext serializers,
            TypeSerializer typeSer) throws JacksonException
    {
        if (_value instanceof JacksonSerializable) {
            ((JacksonSerializable) _value).serializeWithType(gen, serializers, typeSer);
        } else if (_value instanceof SerializableString) {
            /* Since these are not really to be deserialized (with or without type info),
             * just re-route as regular write, which will create one... hopefully it works
             */
            serialize(gen, serializers);
        }
    }

    public void serialize(JsonGenerator gen) throws JacksonException
    {
        if (_value instanceof JacksonSerializable) {
            // No SerializationContext passed, must go via generator, callback
            gen.writePOJO(_value);
        } else {
            _serialize(gen);
        }
    }

    protected void _serialize(JsonGenerator gen) throws JacksonException
    {
        if (_value instanceof SerializableString) {
            gen.writeRawValue((SerializableString) _value);
        } else {
            gen.writeRawValue(String.valueOf(_value));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof RawValue)) return false;
        RawValue other = (RawValue) o;

        if (_value == other._value) {
            return true;
        }
        return (_value != null) && _value.equals(other._value);
    }

    @Override
    public int hashCode() {
        return (_value == null) ? 0 : _value.hashCode();
    }

    @Override
    public String toString() {
        return String.format("[RawValue of type %s]", ClassUtil.classNameOf(_value));
    }
}
