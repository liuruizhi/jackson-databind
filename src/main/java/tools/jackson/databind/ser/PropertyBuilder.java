package tools.jackson.databind.ser;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.databind.*;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.introspect.*;
import tools.jackson.databind.jsontype.TypeSerializer;
import tools.jackson.databind.util.*;

/**
 * Helper class for {@link BeanSerializerFactory} that is used to
 * construct {@link BeanPropertyWriter} instances. Can be sub-classed
 * to change behavior.
 */
public class PropertyBuilder
{
    private final static Object NO_DEFAULT_MARKER = Boolean.FALSE;

    final protected SerializationConfig _config;
    final protected BeanDescription _beanDesc;

    final protected AnnotationIntrospector _annotationIntrospector;

    /**
     * If a property has serialization inclusion value of
     * {@link com.fasterxml.jackson.annotation.JsonInclude.Include#NON_DEFAULT},
     * we may need to know the default value of the bean, to know if property value
     * equals default one.
     *<p>
     * NOTE: only used if enclosing class defines NON_DEFAULT, but NOT if it is the
     * global default OR per-property override.
     */
    protected Object _defaultBean;

    /**
     * Default inclusion mode for properties of the POJO for which
     * properties are collected; possibly overridden on
     * per-property basis. Combines global inclusion defaults and
     * per-type (annotation and type-override) inclusion overrides.
     */
    final protected JsonInclude.Value _defaultInclusion;

    /**
     * Marker flag used to indicate that "real" default values are to be used
     * for properties, as per per-type value inclusion of type <code>NON_DEFAULT</code>
     */
    final protected boolean _useRealPropertyDefaults;

    public PropertyBuilder(SerializationConfig config, BeanDescription beanDesc)
    {
        _config = config;
        _beanDesc = beanDesc;
        // 08-Sep-2016, tatu: This gets tricky, with 3 levels of definitions:
        //  (a) global default inclusion
        //  (b) per-type default inclusion (from annotation or config overrides;
        //     config override having precedence)
        //  (c) per-property override (from annotation on specific property or
        //     config overrides per type of property;
        //     annotation having precedence)
        //
        //  and not only requiring merging, but also considering special handling
        //  for NON_DEFAULT in case of (b) (vs (a) or (c))
        JsonInclude.Value inclPerType = JsonInclude.Value.merge(
                beanDesc.findPropertyInclusion(JsonInclude.Value.empty()),
                config.getDefaultPropertyInclusion(beanDesc.getBeanClass(),
                        JsonInclude.Value.empty()));
        _defaultInclusion = JsonInclude.Value.merge(config.getDefaultPropertyInclusion(),
                inclPerType);
        _useRealPropertyDefaults = inclPerType.getValueInclusion() == JsonInclude.Include.NON_DEFAULT;
        _annotationIntrospector = _config.getAnnotationIntrospector();
    }

    /*
    /**********************************************************************
    /* Public API
    /**********************************************************************
     */

    public Annotations getClassAnnotations() {
        return _beanDesc.getClassAnnotations();
    }

    /**
     * @param contentTypeSer Optional explicit type information serializer
     *    to use for contained values (only used for properties that are
     *    of container type)
     */
    protected BeanPropertyWriter buildWriter(SerializationContext ctxt,
            BeanPropertyDefinition propDef, JavaType declaredType, ValueSerializer<?> ser,
            TypeSerializer typeSer, TypeSerializer contentTypeSer,
            AnnotatedMember am, boolean defaultUseStaticTyping)
    {
        // do we have annotation that forces type to use (to declared type or its super type)?
        JavaType serializationType;
        try {
            serializationType = findSerializationType(am, defaultUseStaticTyping, declaredType);
        } catch (DatabindException e) {
            if (propDef == null) {
                return ctxt.reportBadDefinition(declaredType, ClassUtil.exceptionMessage(e));
            }
            return ctxt.reportBadPropertyDefinition(_beanDesc, propDef, ClassUtil.exceptionMessage(e));
        }

        // Container types can have separate type serializers for content (value / element) type
        if (contentTypeSer != null) {
            // 04-Feb-2010, tatu: Let's force static typing for collection, if there is
            //    type information for contents. Should work well (for JAXB case); can be
            //    revisited if this causes problems.
            if (serializationType == null) {
//                serializationType = TypeFactory.type(am.getGenericType(), _beanDesc.getType());
                serializationType = declaredType;
            }
            JavaType ct = serializationType.getContentType();
            // Not exactly sure why, but this used to occur; better check explicitly:
            if (ct == null) {
                ctxt.reportBadPropertyDefinition(_beanDesc, propDef,
                        "serialization type "+serializationType+" has no content");
            }
            serializationType = serializationType.withContentTypeHandler(contentTypeSer);
            ct = serializationType.getContentType();
        }

        Object valueToSuppress = null;
        boolean suppressNulls = false;

        // 12-Jul-2016, tatu: [databind#1256] Need to make sure we consider type refinement
        JavaType actualType = (serializationType == null) ? declaredType : serializationType;

        // 17-Mar-2017: [databind#1522] Allow config override per property type
        AnnotatedMember accessor = propDef.getAccessor(); // lgtm [java/dereferenced-value-may-be-null]
        if (accessor == null) {
            // neither Setter nor ConstructorParameter are expected here
            return ctxt.reportBadPropertyDefinition(_beanDesc, propDef,
                    "could not determine property type");
        }
        Class<?> rawPropertyType = accessor.getRawType();

        // 17-Aug-2016, tatu: Default inclusion covers global default (for all types), as well
        //   as type-default for enclosing POJO. What we need, then, is per-type default (if any)
        //   for declared property type... and finally property annotation overrides
        JsonInclude.Value inclV = _config.getDefaultInclusion(actualType.getRawClass(),
                rawPropertyType, _defaultInclusion);

        // property annotation override

        inclV = inclV.withOverrides(propDef.findInclusion());

        JsonInclude.Include inclusion = inclV.getValueInclusion();
        if (inclusion == JsonInclude.Include.USE_DEFAULTS) { // should not occur but...
            inclusion = JsonInclude.Include.ALWAYS;
        }
        switch (inclusion) {
        case NON_DEFAULT:
            // 11-Nov-2015, tatu: This is tricky because semantics differ between cases,
            //    so that if enclosing class has this, we may need to access values of property,
            //    whereas for global defaults OR per-property overrides, we have more
            //    static definition. Sigh.
            // First: case of class/type specifying it; try to find POJO property defaults
            Object defaultBean;

            // 16-Oct-2016, tatu: Note: if we cannot for some reason create "default instance",
            //    revert logic to the case of general/per-property handling, so both
            //    type-default AND null are to be excluded.
            //    (as per [databind#1417]
            if (_useRealPropertyDefaults && (defaultBean = getDefaultBean()) != null) {
                // 07-Sep-2016, tatu: may also need to front-load access forcing now
                if (ctxt.isEnabled(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS)) {
                    am.fixAccess(_config.isEnabled(MapperFeature.OVERRIDE_PUBLIC_ACCESS_MODIFIERS));
                }
                try {
                    valueToSuppress = am.getValue(defaultBean);
                } catch (Exception e) {
                    _throwWrapped(e, propDef.getName(), defaultBean);
                }
            } else {
                valueToSuppress = BeanUtil.getDefaultValue(actualType);
                suppressNulls = true;
            }
            if (valueToSuppress == null) {
                suppressNulls = true;
                // [databind#4471] Different behavior when Include.NON_DEFAULT
                //   setting is used on POJO vs global setting, as per documentation.
                if (!_useRealPropertyDefaults) {
                    // [databind#4464] NON_DEFAULT does not work with NON_EMPTY for custom serializer
                    valueToSuppress = BeanPropertyWriter.MARKER_FOR_EMPTY;
                }
            } else {
                if (valueToSuppress.getClass().isArray()) {
                    valueToSuppress = ArrayBuilders.getArrayComparator(valueToSuppress);
                }
            }
            break;
        case NON_ABSENT: // new with 2.6, to support Guava/JDK8 Optionals
            // always suppress nulls
            suppressNulls = true;
            // and for referential types, also "empty", which in their case means "absent"
            if (actualType.isReferenceType()) {
                valueToSuppress = BeanPropertyWriter.MARKER_FOR_EMPTY;
            }
            break;
        case NON_EMPTY:
            // always suppress nulls
            suppressNulls = true;
            // but possibly also 'empty' values:
            valueToSuppress = BeanPropertyWriter.MARKER_FOR_EMPTY;
            break;
        case CUSTOM: // new with 2.9
            valueToSuppress = ctxt.includeFilterInstance(propDef, inclV.getValueFilter());
            break;
        case NON_NULL:
            suppressNulls = true;
            // fall through
        case ALWAYS: // default
        default:
            // we may still want to suppress empty collections
            @SuppressWarnings("deprecation")
            final SerializationFeature emptyJsonArrays = SerializationFeature.WRITE_EMPTY_JSON_ARRAYS;
            if (actualType.isContainerType() && !_config.isEnabled(emptyJsonArrays)) {
                valueToSuppress = BeanPropertyWriter.MARKER_FOR_EMPTY;
            }
            break;
        }
        Class<?>[] views = propDef.findViews();
        if (views == null) {
            views = _beanDesc.findDefaultViews();
        }
        BeanPropertyWriter bpw = _constructPropertyWriter(propDef,
                am, _beanDesc.getClassAnnotations(), declaredType,
                ser, typeSer, serializationType, suppressNulls, valueToSuppress, views);

        // How about custom null serializer?
        Object serDef = _annotationIntrospector.findNullSerializer(_config, am);
        if (serDef != null) {
            bpw.assignNullSerializer(ctxt.serializerInstance(am, serDef));
        }
        // And then, handling of unwrapping
        NameTransformer unwrapper = _annotationIntrospector.findUnwrappingNameTransformer(_config, am);
        if (unwrapper != null) {
            bpw = bpw.unwrappingWriter(unwrapper);
        }
        return bpw;
    }

    /**
     * Overridable factory method for actual construction of {@link BeanPropertyWriter};
     * often needed if subclassing {@link #buildWriter} method.
     */
    protected BeanPropertyWriter _constructPropertyWriter(BeanPropertyDefinition propDef,
            AnnotatedMember member, Annotations contextAnnotations,
            JavaType declaredType,
            ValueSerializer<?> ser, TypeSerializer typeSer, JavaType serType,
            boolean suppressNulls, Object suppressableValue,
            Class<?>[] includeInViews)
    {
        return new BeanPropertyWriter(propDef,
                member, contextAnnotations, declaredType,
                ser, typeSer, serType, suppressNulls, suppressableValue, includeInViews);
    }

    /*
    /**********************************************************************
    /* Helper methods; annotation access
    /**********************************************************************
     */

    /**
     * Method that will try to determine statically defined type of property
     * being serialized, based on annotations (for overrides), and alternatively
     * declared type (if static typing for serialization is enabled).
     * If neither can be used (no annotations, dynamic typing), returns null.
     */
    protected JavaType findSerializationType(Annotated a, boolean useStaticTyping, JavaType declaredType)
    {
        JavaType secondary = _annotationIntrospector.refineSerializationType(_config, a, declaredType);

        // 11-Oct-2015, tatu: As of 2.7, not 100% sure following checks are needed. But keeping
        //    for now, just in case
        if (secondary != declaredType) {
            Class<?> serClass = secondary.getRawClass();
            // Must be a super type to be usable
            Class<?> rawDeclared = declaredType.getRawClass();
            if (serClass.isAssignableFrom(rawDeclared)) {
                // fine as is
            } else {
                /* 18-Nov-2010, tatu: Related to fixing [JACKSON-416], an issue with such
                 *   check is that for deserialization more specific type makes sense;
                 *   and for serialization more generic. But alas JAXB uses but a single
                 *   annotation to do both... Hence, we must just discard type, as long as
                 *   types are related
                 */
                if (!rawDeclared.isAssignableFrom(serClass)) {
                    throw new IllegalArgumentException("Illegal concrete-type annotation for method '"+a.getName()+"': class "+serClass.getName()+" not a super-type of (declared) class "+rawDeclared.getName());
                }
                /* 03-Dec-2010, tatu: Actually, ugh, we may need to further relax this
                 *   and actually accept subtypes too for serialization. Bit dangerous in theory
                 *   but need to trust user here...
                 */
            }
            useStaticTyping = true;
            declaredType = secondary;
        }
        // If using static typing, declared type is known to be the type...
        JsonSerialize.Typing typing = _annotationIntrospector.findSerializationTyping(_config, a);
        if ((typing != null) && (typing != JsonSerialize.Typing.DEFAULT_TYPING)) {
            useStaticTyping = (typing == JsonSerialize.Typing.STATIC);
        }
        if (useStaticTyping) {
            // 11-Oct-2015, tatu: Make sure JavaType also "knows" static-ness...
            return declaredType.withStaticTyping();

        }
        return null;
    }

    /*
    /**********************************************************************
    /* Helper methods for default value handling
    /**********************************************************************
     */

    protected Object getDefaultBean()
    {
        Object def = _defaultBean;
        if (def == null) {
            /* If we can fix access rights, we should; otherwise non-public
             * classes or default constructor will prevent instantiation
             */
            def = _beanDesc.instantiateBean(_config.canOverrideAccessModifiers());
            if (def == null) {
                // 06-Nov-2015, tatu: As per [databind#998], do not fail.
                /*
                Class<?> cls = _beanDesc.getClassInfo().getAnnotated();
                throw new IllegalArgumentException("Class "+cls.getName()+" has no default constructor; cannot instantiate default bean value to support 'properties=JsonSerialize.Inclusion.NON_DEFAULT' annotation");
                 */

                // And use a marker
                def = NO_DEFAULT_MARKER;
            }
            _defaultBean = def;
        }
        return (def == NO_DEFAULT_MARKER) ? null : _defaultBean;
    }

    /*
    /**********************************************************************
    /* Helper methods for exception handling
    /**********************************************************************
     */

    protected Object _throwWrapped(Exception e, String propName, Object defaultBean)
    {
        Throwable t = e;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        ClassUtil.throwIfError(t);
        ClassUtil.throwIfRTE(t);
        throw new IllegalArgumentException("Failed to get property '"+propName+"' of default "+defaultBean.getClass().getName()+" instance");
    }
}
