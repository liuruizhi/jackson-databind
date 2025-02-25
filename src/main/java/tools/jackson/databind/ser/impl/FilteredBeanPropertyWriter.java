package tools.jackson.databind.ser.impl;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.util.NameTransformer;

/**
 * Decorated {@link BeanPropertyWriter} that will filter out properties
 * that are not to be included in currently active JsonView.
 */
public abstract class FilteredBeanPropertyWriter
{
    public static BeanPropertyWriter constructViewBased(BeanPropertyWriter base,
            Class<?>[] viewsToIncludeIn)
    {
        if (viewsToIncludeIn.length == 1) {
            return new SingleView(base, viewsToIncludeIn[0]);
        }
        return new MultiView(base, viewsToIncludeIn);
    }

    /*
    /**********************************************************************
    /* Concrete sub-classes
    /**********************************************************************
     */

    private final static class SingleView
        extends BeanPropertyWriter
        implements java.io.Serializable
    {
        private static final long serialVersionUID = 1L;

        protected final BeanPropertyWriter _delegate;

        protected final Class<?> _view;

        protected SingleView(BeanPropertyWriter delegate, Class<?> view)
        {
            super(delegate);
            _delegate = delegate;
            _view = view;
        }

        @Override
        public SingleView rename(NameTransformer transformer) {
            return new SingleView(_delegate.rename(transformer), _view);
        }

        @Override
        public void assignSerializer(ValueSerializer<Object> ser) {
            _delegate.assignSerializer(ser);
        }

        @Override
        public void assignNullSerializer(ValueSerializer<Object> nullSer) {
            _delegate.assignNullSerializer(nullSer);
        }

        @Override
        public void serializeAsProperty(Object bean, JsonGenerator gen, SerializationContext prov)
            throws Exception
        {
            Class<?> activeView = prov.getActiveView();
            if (activeView == null || _view.isAssignableFrom(activeView)) {
                _delegate.serializeAsProperty(bean, gen, prov);
            } else {
                _delegate.serializeAsOmittedProperty(bean, gen, prov);
            }
        }

        @Override
        public void serializeAsElement(Object bean, JsonGenerator gen, SerializationContext prov)
            throws Exception
        {
            Class<?> activeView = prov.getActiveView();
            if (activeView == null || _view.isAssignableFrom(activeView)) {
                _delegate.serializeAsElement(bean, gen, prov);
            } else {
                _delegate.serializeAsOmittedElement(bean, gen, prov);
            }
        }

        @Override
        public void depositSchemaProperty(JsonObjectFormatVisitor v,
                SerializationContext provider)
        {
            Class<?> activeView = provider.getActiveView();
            if (activeView == null || _view.isAssignableFrom(activeView)) {
                super.depositSchemaProperty(v, provider);
            }
        }
    }

    private final static class MultiView
        extends BeanPropertyWriter
        implements java.io.Serializable
    {
        private static final long serialVersionUID = 1L;

        protected final BeanPropertyWriter _delegate;

        protected final Class<?>[] _views;

        protected MultiView(BeanPropertyWriter delegate, Class<?>[] views) {
            super(delegate);
            _delegate = delegate;
            _views = views;
        }

        @Override
        public MultiView rename(NameTransformer transformer) {
            return new MultiView(_delegate.rename(transformer), _views);
        }

        @Override
        public void assignSerializer(ValueSerializer<Object> ser) {
            _delegate.assignSerializer(ser);
        }

        @Override
        public void assignNullSerializer(ValueSerializer<Object> nullSer) {
            _delegate.assignNullSerializer(nullSer);
        }

        @Override
        public void serializeAsProperty(Object bean, JsonGenerator gen, SerializationContext prov)
            throws Exception
        {
            if (_inView(prov.getActiveView())) {
                _delegate.serializeAsProperty(bean, gen, prov);
                return;
            }
            _delegate.serializeAsOmittedProperty(bean, gen, prov);
        }

        @Override
        public void serializeAsElement(Object bean, JsonGenerator gen, SerializationContext prov)
            throws Exception
        {
            if (_inView(prov.getActiveView())) {
                _delegate.serializeAsElement(bean, gen, prov);
                return;
            }
            _delegate.serializeAsOmittedElement(bean, gen, prov);
        }

        @Override
        public void depositSchemaProperty(JsonObjectFormatVisitor v,
                SerializationContext provider)
        {
            if (_inView(provider.getActiveView())) {
                super.depositSchemaProperty(v, provider);
            }
        }

        private final boolean _inView(Class<?> activeView)
        {
            if (activeView == null) {
                return true;
            }
            final int len = _views.length;
            for (int i = 0; i < len; ++i) {
                if (_views[i].isAssignableFrom(activeView)) {
                    return true;
                }
            }
            return false;
        }
    }
}
