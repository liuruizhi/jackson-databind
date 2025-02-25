package tools.jackson.databind.ser.bean;

import java.util.Set;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonToken;
import tools.jackson.core.type.WritableTypeId;
import tools.jackson.databind.*;
import tools.jackson.databind.jsontype.TypeSerializer;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.impl.ObjectIdWriter;
import tools.jackson.databind.util.NameTransformer;

/**
 * Specialization of {@link BeanAsArraySerializer}, optimized for handling
 * small number of properties where calls to property handlers can be
 * "unrolled" by eliminated looping. This can help optimize execution
 * significantly for some backends.
 */
public class UnrolledBeanAsArraySerializer
    extends BeanSerializerBase
{
    /**
     * Serializer that would produce JSON Object version; used in
     * cases where array output cannot be used.
     */
    protected final BeanSerializerBase _defaultSerializer;

    public static final int MAX_PROPS = 6;

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

    public UnrolledBeanAsArraySerializer(BeanSerializerBase src) {
        super(src, (ObjectIdWriter) null);
        _defaultSerializer = src;
        _propCount = _props.length;
        _calcUnrolled();
    }

    protected UnrolledBeanAsArraySerializer(BeanSerializerBase src,
            Set<String> toIgnore, Set<String> toInclude) {
        super(src, toIgnore, toInclude);
        _defaultSerializer = src;
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
    public static UnrolledBeanAsArraySerializer tryConstruct(BeanSerializerBase src)
    {
        if ((src.propertyCount() > MAX_PROPS)
                || (src.getFilterId() != null)
                || src.hasViewProperties()) {
            return null;
        }
        return new UnrolledBeanAsArraySerializer(src);
    }

    /*
    /**********************************************************************
    /* Life-cycle: factory methods, fluent factories
    /**********************************************************************
     */

    @Override
    public ValueSerializer<Object> unwrappingSerializer(NameTransformer transformer) {
        // If this gets called, we will just need delegate to the default
        // serializer, to "undo" as-array serialization
        return _defaultSerializer.unwrappingSerializer(transformer);
    }

    @Override
    public boolean isUnwrappingSerializer() {
        return false;
    }

    @Override
    public BeanSerializerBase withObjectIdWriter(ObjectIdWriter objectIdWriter) {
        // can't handle Object Ids, for now, so:
        return _defaultSerializer.withObjectIdWriter(objectIdWriter);
    }

    @Override
    public BeanSerializerBase withFilterId(Object filterId) {
        // Revert to Vanilla variant, if so:
        return new BeanAsArraySerializer(_defaultSerializer,
                _objectIdWriter, filterId);
    }

    @Override
    protected UnrolledBeanAsArraySerializer withByNameInclusion(Set<String> toIgnore,
            Set<String> toInclude) {
        return new UnrolledBeanAsArraySerializer(this, toIgnore, toInclude);
    }

    @Override
    protected BeanSerializerBase withProperties(BeanPropertyWriter[] properties,
            BeanPropertyWriter[] filteredProperties) {
        // Similar to regular as-array-serializer, let's NOT reorder properties
        return this;
    }

    @Override
    protected BeanSerializerBase asArraySerializer() {
        return this; // already is one...
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

    // Re-defined from base class, due to differing prefixes
    @Override
    public void serializeWithType(Object bean, JsonGenerator gen,
            SerializationContext ctxt, TypeSerializer typeSer)
        throws JacksonException
    {
        WritableTypeId typeIdDef = _typeIdDef(typeSer, bean, JsonToken.START_ARRAY);
        typeSer.writeTypePrefix(gen, ctxt, typeIdDef);
        // NOTE: instances NOT constructed if view-processing available
        serializeNonFiltered(bean, gen, ctxt);
        typeSer.writeTypeSuffix(gen, ctxt, typeIdDef);
    }

    /**
     * Main serialization method that will delegate actual output to
     * configured
     * {@link BeanPropertyWriter} instances.
     */
    @Override
    public final void serialize(Object bean, JsonGenerator gen, SerializationContext provider)
        throws JacksonException
    {
        if (provider.isEnabled(SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED)
                && hasSingleElement(provider)) {
            serializeNonFiltered(bean, gen, provider);
            return;
        }
        // note: it is assumed here that limitations (type id, object id,
        // any getter, filtering) have already been checked; so code here
        // is trivial.

        gen.writeStartArray(bean, _props.length);
        // NOTE: instances NOT constructed if view-processing available
        serializeNonFiltered(bean, gen, provider);
        gen.writeEndArray();
    }

    /*
    /**********************************************************************
    /* Property serialization methods
    /**********************************************************************
     */

    private boolean hasSingleElement(SerializationContext provider) {
        return _props.length == 1;
    }

    protected final void serializeNonFiltered(Object bean, JsonGenerator gen,
            SerializationContext provider)
        throws JacksonException
    {
        BeanPropertyWriter prop = null;
        try {
            switch (_propCount) {
            default:
            //case 6:
                prop = _prop1;
                prop.serializeAsElement(bean, gen, provider);
                // fall through
            case 5:
                prop = _prop2;
                prop.serializeAsElement(bean, gen, provider);
            case 4:
                prop = _prop3;
                prop.serializeAsElement(bean, gen, provider);
            case 3:
                prop = _prop4;
                prop.serializeAsElement(bean, gen, provider);
            case 2:
                prop = _prop5;
                prop.serializeAsElement(bean, gen, provider);
            case 1:
                prop = _prop6;
                prop.serializeAsElement(bean, gen, provider);
            case 0:
            }
            // NOTE: any getters cannot be supported either
        } catch (Exception e) {
            wrapAndThrow(provider, e, bean, prop.getName());
        } catch (StackOverflowError e) {
            throw DatabindException.from(gen, "Infinite recursion (StackOverflowError)", e)
                .prependPath(bean, prop.getName());
        }
    }
}
