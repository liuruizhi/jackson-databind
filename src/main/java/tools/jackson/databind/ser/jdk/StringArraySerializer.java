package tools.jackson.databind.ser.jdk;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonFormat;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.*;
import tools.jackson.databind.annotation.JacksonStdImpl;
import tools.jackson.databind.introspect.AnnotatedMember;
import tools.jackson.databind.jsonFormatVisitors.JsonFormatTypes;
import tools.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import tools.jackson.databind.jsontype.TypeSerializer;
import tools.jackson.databind.ser.std.ArraySerializerBase;
import tools.jackson.databind.ser.std.StdContainerSerializer;
import tools.jackson.databind.type.TypeFactory;

/**
 * Standard serializer used for <code>String[]</code> values.
 */
@JacksonStdImpl
public class StringArraySerializer
    extends ArraySerializerBase<String[]>
{
    /* Note: not clean in general, but we are betting against
     * anyone re-defining properties of String.class here...
     */
    private final static JavaType VALUE_TYPE = TypeFactory.unsafeSimpleType(String.class);

    public final static StringArraySerializer instance = new StringArraySerializer();

    /**
     * Value serializer to use, if it's not the standard one
     * (if it is we can optimize serialization significantly)
     */
    protected final ValueSerializer<Object> _elementSerializer;

    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */

    protected StringArraySerializer() {
        super(String[].class);
        _elementSerializer = null;
    }

    @SuppressWarnings("unchecked")
    public StringArraySerializer(StringArraySerializer src,
            BeanProperty prop, ValueSerializer<?> ser, Boolean unwrapSingle) {
        super(src, prop, unwrapSingle);
        _elementSerializer = (ValueSerializer<Object>) ser;
    }

    @Override
    public ValueSerializer<?> _withResolved(BeanProperty prop, Boolean unwrapSingle) {
        return new StringArraySerializer(this, prop, _elementSerializer, unwrapSingle);
    }

    /**
     * Strings never add type info; hence, even if type serializer is suggested,
     * we'll ignore it...
     */
    @Override
    public StdContainerSerializer<?> _withValueTypeSerializer(TypeSerializer vts) {
        return this;
    }

    /*
    /**********************************************************************
    /* Post-processing
    /**********************************************************************
     */

    @Override
    public ValueSerializer<?> createContextual(SerializationContext provider,
            BeanProperty property)
    {
        // 29-Sep-2012, tatu: Actually, we need to do much more contextual
        //    checking here since we finally know for sure the property,
        //    and it may have overrides
        ValueSerializer<?> ser = null;

        // First: if we have a property, may have property-annotation overrides
        if (property != null) {
            final AnnotationIntrospector ai = provider.getAnnotationIntrospector();
            AnnotatedMember m = property.getMember();
            if (m != null) {
                ser = provider.serializerInstance(m,
                        ai.findContentSerializer(provider.getConfig(), m));
            }
        }
        // but since formats have both property overrides and global per-type defaults,
        // need to do that separately
        Boolean unwrapSingle = findFormatFeature(provider, property, String[].class,
                JsonFormat.Feature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED);
        if (ser == null) {
            ser = _elementSerializer;
        }
        // May have a content converter
        ser = findContextualConvertingSerializer(provider, property, ser);
        if (ser == null) {
            ser = provider.findContentValueSerializer(String.class, property);
        }
        // Optimization: default serializer just writes String, so we can avoid a call:
        if (isDefaultSerializer(ser)) {
            ser = null;
        }
        // note: will never have TypeSerializer, because Strings are "natural" type
        if ((ser == _elementSerializer) && (Objects.equals(unwrapSingle, _unwrapSingle))) {
            return this;
        }
        return new StringArraySerializer(this, property, ser, unwrapSingle);
    }

    /*
    /**********************************************************************
    /* Simple accessors
    /**********************************************************************
     */

    @Override
    public JavaType getContentType() {
        return VALUE_TYPE;
    }

    @Override
    public ValueSerializer<?> getContentSerializer() {
        return _elementSerializer;
    }

    @Override
    public boolean isEmpty(SerializationContext prov, String[] value) {
        return (value.length == 0);
    }

    @Override
    public boolean hasSingleElement(String[] value) {
        return (value.length == 1);
    }

    /*
    /**********************************************************************
    /* Actual serialization
    /**********************************************************************
     */

    @Override
    public final void serialize(String[] value, JsonGenerator gen, SerializationContext provider)
        throws JacksonException
    {
        final int len = value.length;
        if (len == 1) {
            if (((_unwrapSingle == null) &&
                    provider.isEnabled(SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED))
                    || (_unwrapSingle == Boolean.TRUE)) {
                serializeContents(value, gen, provider);
                return;
            }
        }
        gen.writeStartArray(value, len);
        serializeContents(value, gen, provider);
        gen.writeEndArray();
    }

    @Override
    public void serializeContents(String[] value, JsonGenerator gen, SerializationContext provider)
        throws JacksonException
    {
        final int len = value.length;
        if (len == 0) {
            return;
        }
        if (_elementSerializer != null) {
            serializeContentsSlow(value, gen, provider, _elementSerializer);
            return;
        }
        for (int i = 0; i < len; ++i) {
            String str = value[i];
            if (str == null) {
                gen.writeNull();
            } else {
                gen.writeString(value[i]);
            }
        }
    }

    private void serializeContentsSlow(String[] value, JsonGenerator gen, SerializationContext provider, ValueSerializer<Object> ser)
        throws JacksonException
    {
        for (int i = 0, len = value.length; i < len; ++i) {
            String str = value[i];
            if (str == null) {
                provider.defaultSerializeNullValue(gen);
            } else {
                ser.serialize(value[i], gen, provider);
            }
        }
    }

    @Override
    public void acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType typeHint)
    {
        visitArrayFormat(visitor, typeHint, JsonFormatTypes.STRING);
    }
}
