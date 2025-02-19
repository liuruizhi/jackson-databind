package tools.jackson.databind.deser.impl;

import java.util.Collection;
import java.util.Map;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.*;
import tools.jackson.databind.deser.SettableBeanProperty;

/**
 * Wrapper property that is used to handle managed (forward) properties
 * Basically just needs to delegate first to actual forward property, and
 * then to back property.
 */
public final class ManagedReferenceProperty
    // Changed to extends delegating base class in 2.9
    extends SettableBeanProperty.Delegating
{
    private static final long serialVersionUID = 1L;

    protected final String _referenceName;

    /**
     * Flag that indicates whether property to handle is a container type
     * (array, Collection, Map) or not.
     */
    protected final boolean _isContainer;

    protected final SettableBeanProperty _backProperty;

    public ManagedReferenceProperty(SettableBeanProperty forward, String refName,
            SettableBeanProperty backward, boolean isContainer)
    {
        super(forward);
        _referenceName = refName;
        _backProperty = backward;
        _isContainer = isContainer;
    }

    @Override
    protected SettableBeanProperty withDelegate(SettableBeanProperty d) {
        throw new IllegalStateException("Should never try to reset delegate");
    }

    // need to override to ensure both get fixed
    @Override
    public void fixAccess(DeserializationConfig config) {
        delegate.fixAccess(config);
        _backProperty.fixAccess(config);
    }

    /*
    /**********************************************************
    /* Overridden methods
    /**********************************************************
     */

    @Override
    public void deserializeAndSet(JsonParser p, DeserializationContext ctxt, Object instance)
            throws JacksonException
    {
        set(ctxt, instance, delegate.deserialize(p, ctxt));
    }

    @Override
    public Object deserializeSetAndReturn(JsonParser p, DeserializationContext ctxt, Object instance)
            throws JacksonException
    {
        return setAndReturn(ctxt, instance, deserialize(p, ctxt));
    }

    @Override
    public final void set(DeserializationContext ctxt, Object instance, Object value) {
        setAndReturn(ctxt, instance, value);
    }

    @Override
    public Object setAndReturn(DeserializationContext ctxt, Object instance, Object value)
    {
        /* 04-Feb-2014, tatu: As per [#390], it may be necessary to switch the
         *   ordering of forward/backward references, and start with back ref.
         */
        if (value != null) {
            if (_isContainer) { // ok, this gets ugly... but has to do for now
                if (value instanceof Object[]) {
                    for (Object ob : (Object[]) value) {
                        if (ob != null) { _backProperty.set(ctxt, ob, instance); }
                    }
                } else if (value instanceof Collection<?>) {
                    for (Object ob : (Collection<?>) value) {
                        if (ob != null) { _backProperty.set(ctxt, ob, instance); }
                    }
                } else if (value instanceof Map<?,?>) {
                    for (Object ob : ((Map<?,?>) value).values()) {
                        if (ob != null) { _backProperty.set(ctxt, ob, instance); }
                    }
                } else {
                    throw new IllegalStateException("Unsupported container type ("+value.getClass().getName()
                            +") when resolving reference '"+_referenceName+"'");
                }
            } else {
                _backProperty.set(ctxt, value, instance);
            }
        }
        // and then the forward reference itself
        return delegate.setAndReturn(ctxt, instance, value);
	}
}
