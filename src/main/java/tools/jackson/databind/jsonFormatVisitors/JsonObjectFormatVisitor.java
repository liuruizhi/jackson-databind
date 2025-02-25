package tools.jackson.databind.jsonFormatVisitors;

import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationContext;

/**
 * Visitor called when properties of a type that maps to JSON Object
 * are being visited: this usually means POJOs, but sometimes other
 * types use it too (like {@link java.util.EnumMap}).
 */
public interface JsonObjectFormatVisitor extends JsonFormatVisitorWithSerializationContext
{
    /**
     * Callback method called when a POJO property is being traversed.
     */
    public void property(BeanProperty writer);

    /**
     * Callback method called when a non-POJO property (typically something
     * like an Enum entry of {@link java.util.EnumMap} type) is being
     * traversed. With POJOs, {@link #property(BeanProperty)} is called instead.
     */
    public void property(String name, JsonFormatVisitable handler, JavaType propertyTypeHint);

    public void optionalProperty(BeanProperty writer);
    public void optionalProperty(String name, JsonFormatVisitable handler,
            JavaType propertyTypeHint);

    /**
     * Default "empty" implementation, useful as the base to start on;
     * especially as it is guaranteed to implement all the method
     * of the interface, even if new methods are getting added.
     */
    public static class Base
        implements JsonObjectFormatVisitor
    {
        protected SerializationContext _provider;

        public Base() { }
        public Base(SerializationContext p) { _provider = p; }

        @Override
        public SerializationContext getContext() { return _provider; }

        @Override
        public void setContext(SerializationContext p) { _provider = p; }

        @Override
        public void property(BeanProperty prop) { }

        @Override
        public void property(String name, JsonFormatVisitable handler,
                JavaType propertyTypeHint) { }

        @Override
        public void optionalProperty(BeanProperty prop) { }

        @Override
        public void optionalProperty(String name, JsonFormatVisitable handler,
                JavaType propertyTypeHint) { }
    }
}
