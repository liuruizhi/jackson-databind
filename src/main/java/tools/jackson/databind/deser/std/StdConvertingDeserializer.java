package tools.jackson.databind.deser.std;

import java.util.Collection;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.*;
import tools.jackson.databind.jsontype.TypeDeserializer;
import tools.jackson.databind.type.LogicalType;
import tools.jackson.databind.util.AccessPattern;
import tools.jackson.databind.util.ClassUtil;
import tools.jackson.databind.util.Converter;
import tools.jackson.databind.util.NameTransformer;

/**
 * Deserializer implementation where given Java type is first deserialized
 * by a standard Jackson deserializer into a delegate type; and then
 * this delegate type is converted using a configured
 * {@link Converter} into desired target type.
 * Common delegate types to use are {@link java.util.Map}
 * and {@link tools.jackson.databind.JsonNode}.
 *<p>
 * Note that although types (delegate, target) may be related, they must not be same; trying
 * to do this will result in an exception.
 *<p>
 * Also note that in Jackson 2.x, this class was named {@code StdDelegatingDeserializer}
 *
 * @param <T> Target type to convert to, from delegate type
 *
 * @see StdNodeBasedDeserializer
 * @see Converter
 */
public class StdConvertingDeserializer<T>
    extends StdDeserializer<T>
{
    /**
     * Converter that was used for creating {@link #_delegateDeserializer}.
     */
    protected final Converter<Object,T> _converter;

    /**
     * Fully resolved delegate type, with generic information if any available.
     */
    protected final JavaType _delegateType;

    /**
     * Underlying serializer for type <code>T</code>.
     */
    protected final ValueDeserializer<Object> _delegateDeserializer;

    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */

    @SuppressWarnings("unchecked")
    public StdConvertingDeserializer(Converter<?,T> converter)
    {
        super(Object.class);
        _converter = (Converter<Object,T>)converter;
        _delegateType = null;
        _delegateDeserializer = null;
    }

    @SuppressWarnings("unchecked")
    public StdConvertingDeserializer(Converter<Object,T> converter,
            JavaType delegateType, ValueDeserializer<?> delegateDeserializer)
    {
        super(delegateType);
        _converter = converter;
        _delegateType = delegateType;
        _delegateDeserializer = (ValueDeserializer<Object>) delegateDeserializer;
    }

    protected StdConvertingDeserializer(StdConvertingDeserializer<T> src)
    {
        super(src);
        _converter = src._converter;
        _delegateType = src._delegateType;
        _delegateDeserializer = src._delegateDeserializer;
    }

    /**
     * Method used for creating resolved contextual instances. Must be
     * overridden when sub-classing.
     */
    protected StdConvertingDeserializer<T> withDelegate(Converter<Object,T> converter,
            JavaType delegateType, ValueDeserializer<?> delegateDeserializer)
    {
        ClassUtil.verifyMustOverride(StdConvertingDeserializer.class, this, "withDelegate");
        return new StdConvertingDeserializer<T>(converter, delegateType, delegateDeserializer);
    }

    @Override
    public ValueDeserializer<T> unwrappingDeserializer(DeserializationContext ctxt,
            NameTransformer unwrapper) {
        ClassUtil.verifyMustOverride(StdConvertingDeserializer.class, this, "unwrappingDeserializer");
        return replaceDelegatee(_delegateDeserializer.unwrappingDeserializer(ctxt, unwrapper));
    }

    @Override
    public ValueDeserializer<T> replaceDelegatee(ValueDeserializer<?> delegatee) {
        ClassUtil.verifyMustOverride(StdConvertingDeserializer.class, this, "replaceDelegatee");
        if (delegatee == _delegateDeserializer) {
            return this;
        }
        return new StdConvertingDeserializer<T>(_converter, _delegateType, delegatee);
    }

    /*
    /**********************************************************************
    /* Contextualization
    /**********************************************************************
     */

    // Note: unlikely to get called since most likely instances explicitly constructed;
    // if so, caller must ensure delegating deserializer is properly resolve()d.
    @Override
    public void resolve(DeserializationContext ctxt)
    {
        if (_delegateDeserializer != null) {
            _delegateDeserializer.resolve(ctxt);
        }
    }

    @Override
    public ValueDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property)
    {
        // First: if already got deserializer to delegate to, contextualize it:
        if (_delegateDeserializer != null) {
            ValueDeserializer<?> deser = ctxt.handleSecondaryContextualization(_delegateDeserializer,
                    property, _delegateType);
            if (deser != _delegateDeserializer) {
                return withDelegate(_converter, _delegateType, deser);
            }
            return this;
        }
        // Otherwise: figure out what is the fully generic delegate type, then find deserializer
        JavaType delegateType = _converter.getInputType(ctxt.getTypeFactory());
        return withDelegate(_converter, delegateType,
                ctxt.findContextualValueDeserializer(delegateType, property));
    }

    /*
    /**********************************************************************
    /* Deserialization
    /**********************************************************************
     */

    @Override
    public T deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException
    {
        Object delegateValue = _delegateDeserializer.deserialize(p, ctxt);
        if (delegateValue == null) {
            return null;
        }
        return convertValue(delegateValue);
    }

    @Override
    public Object deserializeWithType(JsonParser p, DeserializationContext ctxt,
            TypeDeserializer typeDeserializer) throws JacksonException
    {
        /* 12-Apr-2016, tatu: As predicted, earlier handling does not work
         *   (see [databind#1189] for details). There does not seem to be any compelling
         *   way to combine polymorphic types, Converters, but the least sucky way
         *   is probably to use Converter and ignore polymorphic type. Alternative
         *   would be to try to change `TypeDeserializer` to accept `Converter` to
         *   invoke... but that is more intrusive, yet not guaranteeing success.
         */
        // method called up to 2.7.3:
//        Object delegateValue = _delegateDeserializer.deserializeWithType(p, ctxt, typeDeserializer);

        // method called since 2.7.4
        Object delegateValue = _delegateDeserializer.deserialize(p, ctxt);
        if (delegateValue == null) {
            return null;
        }
        return convertValue(delegateValue);
    }

    @SuppressWarnings("unchecked")
    @Override
    public T deserialize(JsonParser p, DeserializationContext ctxt, Object intoValue)
        throws JacksonException
    {
        if (_delegateType.getRawClass().isAssignableFrom(intoValue.getClass())){
            return (T) _delegateDeserializer.deserialize(p, ctxt, intoValue);
        }
        return (T) _handleIncompatibleUpdateValue(p, ctxt, intoValue);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object deserializeWithType(JsonParser p, DeserializationContext ctxt,
            TypeDeserializer typeDeserializer, T intoValue)
        throws JacksonException
    {
        // 21-Jan-2023, tatu: Override was missing from 2.15. Tricky to
        //    support but follow example of the other "deserializeWithType()"
        //   It seems unlikely to actually work (isn't type check just... wrong?)
        //   but for now has to do I guess.
        if (!_delegateType.getRawClass().isAssignableFrom(intoValue.getClass())){
            return (T) _handleIncompatibleUpdateValue(p, ctxt, intoValue);
        }
        return (T) _delegateDeserializer.deserialize(p, ctxt, intoValue);
    }

    /**
     * Overridable handler method called when {@link #deserialize(JsonParser, DeserializationContext, Object)}
     * has been called with a value that is not compatible with delegate value.
     * Since no conversion are expected for such "updateValue" case, this is normally not
     * an operation that can be permitted, and the default behavior is to throw exception.
     * Sub-classes may choose to try alternative approach if they have more information on
     * exact usage and constraints.
     */
    protected Object _handleIncompatibleUpdateValue(JsonParser p, DeserializationContext ctxt, Object intoValue)
        throws JacksonException
    {
        throw new UnsupportedOperationException(String.format
                ("Cannot update object of type %s (using deserializer for type %s)",
                        intoValue.getClass().getName(), _delegateType));
    }

    /*
    /**********************************************************************
    /* Accessors
    /**********************************************************************
     */

    @Override
    public Class<?> handledType() {
        return _delegateDeserializer.handledType();
    }

    @Override
    public LogicalType logicalType() {
        return _delegateDeserializer.logicalType();
    }

    // Let's assume we should be cachable if delegate is
    @Override
    public boolean isCachable() {
        return (_delegateDeserializer != null) && _delegateDeserializer.isCachable();
    }

    @Override
    public ValueDeserializer<?> getDelegatee() {
        return _delegateDeserializer;
    }

    @Override
    public Collection<Object> getKnownPropertyNames() {
        return _delegateDeserializer.getKnownPropertyNames();
    }

    /*
    /**********************************************************
    /* Null/empty/absent accessors
    /**********************************************************
     */

    @Override
    public T getNullValue(DeserializationContext ctxt) {
        return _convertIfNonNull(_delegateDeserializer.getNullValue(ctxt));
    }

    @Override
    public AccessPattern getNullAccessPattern() {
        return _delegateDeserializer.getNullAccessPattern();
    }

    @Override
    public Object getAbsentValue(DeserializationContext ctxt) {
        return _convertIfNonNull(_delegateDeserializer.getAbsentValue(ctxt));
    }

    @Override
    public Object getEmptyValue(DeserializationContext ctxt) {
        return _convertIfNonNull(_delegateDeserializer.getEmptyValue(ctxt));
    }

    @Override
    public AccessPattern getEmptyAccessPattern() {
        return _delegateDeserializer.getEmptyAccessPattern();
    }

    /*
    /**********************************************************
    /* Other accessors
    /**********************************************************
     */

    @Override
    public Boolean supportsUpdate(DeserializationConfig config) {
        return _delegateDeserializer.supportsUpdate(config);
    }

    /*
    /**********************************************************************
    /* Overridable methods
    /**********************************************************************
     */

    /**
     * Method called to convert from "delegate value" (which was deserialized
     * from JSON using standard Jackson deserializer for delegate type)
     * into desired target type.
     *<P>
     * The default implementation uses configured {@link Converter} to do
     * conversion.
     *
     * @param delegateValue
     *
     * @return Result of conversion
     */
    protected T convertValue(Object delegateValue) {
        return _converter.convert(delegateValue);
    }

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */

    protected T _convertIfNonNull(Object delegateValue) {
        return (delegateValue == null) ? null
                : _converter.convert(delegateValue);
    }
}
