package tools.jackson.databind.deser;

import java.lang.annotation.Annotation;

import com.fasterxml.jackson.annotation.JacksonInject;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.*;
import tools.jackson.databind.exc.InvalidDefinitionException;
import tools.jackson.databind.introspect.AnnotatedMember;
import tools.jackson.databind.introspect.AnnotatedParameter;
import tools.jackson.databind.jsontype.TypeDeserializer;
import tools.jackson.databind.util.Annotations;
import tools.jackson.databind.util.ClassUtil;

/**
 * This concrete sub-class implements property that is passed
 * via Creator (constructor or static factory method).
 * It is not a full-featured implementation in that its set method
 * should usually not be called for primary mutation -- instead, value must separately passed --
 * but some aspects are still needed (specifically, injection).
 *<p>
 * Note on injectable values: unlike with other mutators, where
 * deserializer and injecting are separate, here we treat the two as related
 * things. This is necessary to add proper priority, as well as to simplify
 * coordination.
 */
public class CreatorProperty
    extends SettableBeanProperty
{
    private static final long serialVersionUID = 1L;

    /**
     * Placeholder that represents constructor parameter, when it is created
     * from actual constructor.
     * May be null when a synthetic instance is created.
     */
    protected final AnnotatedParameter _annotated;

    /**
     * Id of value to inject, if value injection should be used for this parameter
     * (in addition to, or instead of, regular deserialization).
     */
    protected final JacksonInject.Value _injectableValue;

    /**
     * In special cases, when implementing "updateValue", we cannot use
     * constructors or factory methods, but have to fall back on using a
     * setter (or mutable field property). If so, this refers to that fallback
     * accessor.
     *<p>
     * Mutable only to allow setting after construction, but must be strictly
     * set before any use.
     */
    protected SettableBeanProperty _fallbackSetter;

    protected final int _creatorIndex;

    /**
     * Marker flag that may have to be set during construction, to indicate that
     * although property may have been constructed and added as a placeholder,
     * it represents something that should be ignored during deserialization.
     * This mostly concerns Creator properties which may not be easily deleted
     * during processing.
     */
    protected boolean _ignorable;

    protected CreatorProperty(PropertyName name, JavaType type, PropertyName wrapperName,
            TypeDeserializer typeDeser,
            Annotations contextAnnotations, AnnotatedParameter param,
            int index, JacksonInject.Value injectable,
            PropertyMetadata metadata)
    {
        super(name, type, wrapperName, typeDeser, contextAnnotations, metadata);
        _annotated = param;
        _creatorIndex = index;
        _injectableValue = injectable;
        _fallbackSetter = null;
    }

    /**
     * Factory method for creating {@link CreatorProperty} instances
     *
     * @param name Name of the logical property
     * @param type Type of the property, used to find deserializer
     * @param wrapperName Possible wrapper to use for logical property, if any
     * @param typeDeser Type deserializer to use for handling polymorphic type
     *    information, if one is needed
     * @param contextAnnotations Contextual annotations (usually by class that
     *    declares creator [constructor, factory method] that includes
     *    this property)
     * @param param Representation of property, constructor or factory
     *    method parameter; used for accessing annotations of the property
     * @param injectable Information about injectable value, if any
     * @param index Index of this property within creator invocation
     */
    public static CreatorProperty construct(PropertyName name, JavaType type, PropertyName wrapperName,
            TypeDeserializer typeDeser,
            Annotations contextAnnotations, AnnotatedParameter param,
            int index, JacksonInject.Value injectable,
            PropertyMetadata metadata)
    {
        return new CreatorProperty(name, type, wrapperName, typeDeser, contextAnnotations,
                param, index, injectable, metadata);
    }

    protected CreatorProperty(CreatorProperty src, PropertyName newName) {
        super(src, newName);
        _annotated = src._annotated;
        _injectableValue = src._injectableValue;
        _fallbackSetter = src._fallbackSetter;
        _creatorIndex = src._creatorIndex;
        _ignorable = src._ignorable;
    }

    protected CreatorProperty(CreatorProperty src, ValueDeserializer<?> deser,
            NullValueProvider nva) {
        super(src, deser, nva);
        _annotated = src._annotated;
        _injectableValue = src._injectableValue;
        _fallbackSetter = src._fallbackSetter;
        _creatorIndex = src._creatorIndex;
        _ignorable = src._ignorable;
    }

    protected CreatorProperty(CreatorProperty src, TypeDeserializer typeDeser)
    {
        super(src, typeDeser);
        _annotated = src._annotated;
        _injectableValue = src._injectableValue;
        _fallbackSetter = src._fallbackSetter;
        _creatorIndex = src._creatorIndex;
        _ignorable = src._ignorable;
    }

    @Override
    public SettableBeanProperty withName(PropertyName newName) {
        return new CreatorProperty(this, newName);
    }

    @Override
    public SettableBeanProperty withValueDeserializer(ValueDeserializer<?> deser) {
        if (_valueDeserializer == deser) {
            return this;
        }
        // 07-May-2019, tatu: As per [databind#2303], must keep VD/NVP in-sync if they were
        NullValueProvider nvp = (_valueDeserializer == _nullProvider) ? deser : _nullProvider;
        return new CreatorProperty(this, deser, nvp);
    }

    @Override
    public SettableBeanProperty withNullProvider(NullValueProvider nva) {
        return new CreatorProperty(this, _valueDeserializer, nva);
    }

    // @since 3.0
    public SettableBeanProperty withValueTypeDeserializer(TypeDeserializer typeDeser) {
        if (_valueTypeDeserializer == typeDeser) {
            return this;
        }
        return new CreatorProperty(this, typeDeser);
    }

    @Override
    public void fixAccess(DeserializationConfig config) {
        if (_fallbackSetter != null) {
            _fallbackSetter.fixAccess(config);
        }
    }

    /**
     * NOTE: one exception to immutability, due to problems with CreatorProperty instances
     * being shared between Bean, separate PropertyBasedCreator
     */
    public void setFallbackSetter(SettableBeanProperty fallbackSetter) {
        _fallbackSetter = fallbackSetter;
    }

    @Override
    public void markAsIgnorable() {
        _ignorable = true;
    }

    @Override
    public boolean isIgnorable() {
        return _ignorable;
    }

    /*
    /**********************************************************************
    /* BeanProperty impl
    /**********************************************************************
     */

    @Override
    public <A extends Annotation> A getAnnotation(Class<A> acls) {
        if (_annotated == null) {
            return null;
        }
        return _annotated.getAnnotation(acls);
    }

    @Override public AnnotatedMember getMember() {  return _annotated; }

    @Override public int getCreatorIndex() {
        return _creatorIndex;
    }

    /*
    /**********************************************************************
    /* Overridden methods, SettableBeanProperty
    /**********************************************************************
     */

    @Override
    public void deserializeAndSet(JsonParser p, DeserializationContext ctxt,
            Object instance) throws JacksonException
    {
        _verifySetter();
        _fallbackSetter.set(ctxt, instance, deserialize(p, ctxt));
    }

    @Override
    public Object deserializeSetAndReturn(JsonParser p,
            DeserializationContext ctxt, Object instance) throws JacksonException
    {
        _verifySetter();
        return _fallbackSetter.setAndReturn(ctxt, instance, deserialize(p, ctxt));
    }

    @Override
    public void set(DeserializationContext ctxt, Object instance, Object value)
    {
        _verifySetter();
        _fallbackSetter.set(ctxt, instance, value);
    }

    @Override
    public Object setAndReturn(DeserializationContext ctxt, Object instance, Object value)
    {
        _verifySetter();
        return _fallbackSetter.setAndReturn(ctxt,instance, value);
    }

    @Override
    public PropertyMetadata getMetadata() {
        // 03-Jun-2019, tatu: Added as per [databind#2280] to support merge.
        //   Not 100% sure why it would be needed (or fixes things) but... appears to.
        //   Need to understand better in future as it seems like it should probably be
        //   linked earlier during construction or something.
        // 22-Sep-2019, tatu: Was hoping [databind#2458] fixed this, too, but no such luck
        PropertyMetadata md = super.getMetadata();
        if (_fallbackSetter != null) {
            return md.withMergeInfo(_fallbackSetter.getMetadata().getMergeInfo());
        }
        return md;
    }

    // Perhaps counter-intuitively, ONLY creator properties return non-null id
    @Override
    public Object getInjectableValueId() {
        return (_injectableValue == null) ? null : _injectableValue.getId();
    }

    @Override
    public boolean isInjectionOnly() {
        return (_injectableValue != null) && !_injectableValue.willUseInput(true);
    }

    //  public boolean isInjectionOnly() { return false; }

    /*
    /**********************************************************************
    /* Overridden methods, other
    /**********************************************************************
     */

    @Override
    public String toString() { return "[creator property, name "+ClassUtil.name(getName())+"; inject id '"+getInjectableValueId()+"']"; }

    /*
    /**********************************************************************
    /* Internal helper methods
    /**********************************************************************
     */

    private final void _verifySetter() throws JacksonException {
        if (_fallbackSetter == null) {
            _reportMissingSetter(null, null);
        }
    }

    private void _reportMissingSetter(JsonParser p, DeserializationContext ctxt)
            throws JacksonException
    {
        String clsDesc = (_annotated == null) ? "UNKNOWN TYPE"
                : ClassUtil.getClassDescription(_annotated.getOwner().getDeclaringClass());
        final String msg = String.format(
                "No fallback setter/field defined for creator property %s (of %s)",
                        ClassUtil.name(getName()), clsDesc);
        // Hmmmh. Should we return quietly (NOP), or error?
        // Perhaps better to throw an exception, since it's generally an error.
        if (ctxt != null ) {
            ctxt.reportBadDefinition(getType(), msg);
        } else {
            throw InvalidDefinitionException.from(p, msg, getType());
        }
    }
}
