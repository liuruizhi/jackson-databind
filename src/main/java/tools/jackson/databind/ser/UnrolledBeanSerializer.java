package tools.jackson.databind.ser;

import java.util.Set;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.*;
import tools.jackson.databind.annotation.JacksonStdImpl;
import tools.jackson.databind.ser.bean.BeanAsArraySerializer;
import tools.jackson.databind.ser.bean.BeanSerializerBase;
import tools.jackson.databind.ser.bean.UnwrappingBeanSerializer;
import tools.jackson.databind.ser.impl.ObjectIdWriter;
import tools.jackson.databind.util.NameTransformer;

/**
 * @since 3.0
 */
@JacksonStdImpl
public class UnrolledBeanSerializer
    extends BeanSerializerBase
{
    /* 28-Oct-2017, tatu: Exact choice for max number of properties to unroll
     *    is difficult to pin down, but probably has to be at least 4, and
     *    at most 8. Partly this is due to "blocks of 4" that default bean
     *    serializer now uses, and partly guessing how aggressively JVM might
     *    inline larger methods (more unroll, bigger method).
     */
    private static final int MAX_PROPS = 6;

    protected final int _propCount;

    // // // We store separate references in form more easily accessed
    // // // from switch statement

    protected BeanPropertyWriter _prop1;
    protected BeanPropertyWriter _prop2;
    protected BeanPropertyWriter _prop3;
    protected BeanPropertyWriter _prop4;
    protected BeanPropertyWriter _prop5;
    protected BeanPropertyWriter _prop6;

    /*
    /**********************************************************************
    /* Life-cycle: constructors
    /**********************************************************************
     */

    /**
     * @param builder Builder object that contains collected information
     *   that may be needed for serializer
     * @param properties Property writers used for actual serialization
     */
    public UnrolledBeanSerializer(JavaType type, BeanSerializerBuilder builder,
            BeanPropertyWriter[] properties, BeanPropertyWriter[] filteredProperties)
    {
        super(type, builder, properties, filteredProperties);
        _propCount = _props.length;
        _calcUnrolled();
    }

    protected UnrolledBeanSerializer(UnrolledBeanSerializer src,
            Set<String> toIgnore, Set<String> toInclude) {
        super(src, toIgnore, toInclude);
        _propCount = _props.length;
        _calcUnrolled();
    }

    protected UnrolledBeanSerializer(UnrolledBeanSerializer src,
            BeanPropertyWriter[] properties, BeanPropertyWriter[] filteredProperties) {
        super(src, properties, filteredProperties);
        _propCount = _props.length;
        _calcUnrolled();
    }

    private void _calcUnrolled() {
        BeanPropertyWriter[] oProps = new BeanPropertyWriter[6];
        int offset = 6 - _propCount;
        System.arraycopy(_props, 0, oProps, offset, _propCount);
        _prop1 = oProps[0];
        _prop2 = oProps[1];
        _prop3 = oProps[2];
        _prop4 = oProps[3];
        _prop5 = oProps[4];
        _prop6 = oProps[5];
    }

    /**
     * Factory method that will construct optimized instance if all the constraints
     * are obeyed; or, if not, return `null` to indicate that instance can not be
     * created.
     */
    public static UnrolledBeanSerializer tryConstruct(JavaType type, BeanSerializerBuilder builder,
            BeanPropertyWriter[] properties, BeanPropertyWriter[] filteredProperties)
    {
        if ((properties.length > MAX_PROPS)
                || (builder.getFilterId() != null)) {
            return null;
        }
        return new UnrolledBeanSerializer(type, builder, properties, filteredProperties);
    }

    /*
    /**********************************************************************
    /* Life-cycle: factory methods, fluent factories
    /**********************************************************************
     */

    @Override
    public ValueSerializer<Object> unwrappingSerializer(NameTransformer unwrapper) {
        return new UnwrappingBeanSerializer(this, unwrapper);
    }

    @Override
    public BeanSerializerBase withObjectIdWriter(ObjectIdWriter objectIdWriter) {
        // Revert to Vanilla variant, if so:
        return new BeanSerializer(this, objectIdWriter, _propertyFilterId);
    }

    @Override
    public BeanSerializerBase withFilterId(Object filterId) {
        // Revert to Vanilla variant, if so:
        return new BeanSerializer(this, _objectIdWriter, filterId);
    }

    @Override
    public ValueSerializer<?> withIgnoredProperties(Set<String> toIgnore) {
        // Revert to Vanilla variant here as well
        return new BeanSerializer(this, toIgnore, null);
    }
    
    @Override
    protected BeanSerializerBase withByNameInclusion(Set<String> toIgnore, Set<String> toInclude) {
        return new UnrolledBeanSerializer(this, toIgnore, toInclude);
    }

    @Override
    protected BeanSerializerBase withProperties(BeanPropertyWriter[] properties,
            BeanPropertyWriter[] filteredProperties) {
        return new UnrolledBeanSerializer(this, properties, filteredProperties);
    }

    @Override
    protected BeanSerializerBase asArraySerializer()
    {
        if (canCreateArraySerializer()) {
            return BeanAsArraySerializer.construct(this);
        }
        // Can't... so use this one
        return this;
    }

    @Override
    public void resolve(SerializationContext provider)
    {
        super.resolve(provider);
        _calcUnrolled();
    }

    /*
    /**********************************************************************
    /* ValueSerializer implementation that differs between impls
    /**********************************************************************
     */

    @Override
    public void serialize(Object bean, JsonGenerator gen, SerializationContext provider)
        throws JacksonException
    {
        // NOTE! We have ensured that "JSON Filter" and "Object Id" cases
        // always use "vanilla" BeanSerializer, so no need to check here

        BeanPropertyWriter[] fProps = _filteredProps;
        if ((fProps != null) && (provider.getActiveView() != null)) {
            gen.writeStartObject(bean);
            _serializePropertiesMaybeView(bean, gen, provider, fProps);
            gen.writeEndObject();
            return;
        }
        serializeNonFiltered(bean, gen, provider);
    }

    protected void serializeNonFiltered(Object bean, JsonGenerator gen, SerializationContext provider)
        throws JacksonException
    {
        gen.writeStartObject(bean);

        BeanPropertyWriter prop = null;
        try {
            switch (_propCount) {
            default:
            //case 6:
                prop = _prop1;
                prop.serializeAsProperty(bean, gen, provider);
                // fall through
            case 5:
                prop = _prop2;
                prop.serializeAsProperty(bean, gen, provider);
            case 4:
                prop = _prop3;
                prop.serializeAsProperty(bean, gen, provider);
            case 3:
                prop = _prop4;
                prop.serializeAsProperty(bean, gen, provider);
            case 2:
                prop = _prop5;
                prop.serializeAsProperty(bean, gen, provider);
            case 1:
                prop = _prop6;
                prop.serializeAsProperty(bean, gen, provider);
            case 0:
            }
            prop = null;
            if (_anyGetterWriter != null) {
                _anyGetterWriter.getAndSerialize(bean, gen, provider);
            }
        } catch (Exception e) {
            String name = (prop == null) ? "[anySetter]" : prop.getName();
            wrapAndThrow(provider, e, bean, name);
        } catch (StackOverflowError e) {
            final String name = (prop == null) ? "[anySetter]" : prop.getName();
            throw DatabindException.from(gen, "Infinite recursion (StackOverflowError)", e)
                .prependPath(bean, name);
        }
        gen.writeEndObject();
    }
}
