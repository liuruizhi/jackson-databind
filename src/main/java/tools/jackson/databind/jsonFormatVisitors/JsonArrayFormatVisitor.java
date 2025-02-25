package tools.jackson.databind.jsonFormatVisitors;

import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationContext;

public interface JsonArrayFormatVisitor extends JsonFormatVisitorWithSerializationContext
{
    /**
     * Visit method called for structured types, as well as possibly
     * for leaf types (especially if handled by custom serializers).
     *
     * @param handler Serializer used, to allow for further callbacks
     * @param elementType Type of elements in JSON array value
     */
    void itemsFormat(JsonFormatVisitable handler, JavaType elementType);

    /**
     * Visit method that is called if the content type is a simple
     * scalar type like {@link JsonFormatTypes#STRING} (but not
     * for structured types like {@link JsonFormatTypes#OBJECT} since
     * they would be missing type information).
     */
    void itemsFormat(JsonFormatTypes format);

    /**
     * Default "empty" implementation, useful as the base to start on;
     * especially as it is guaranteed to implement all the method
     * of the interface, even if new methods are getting added.
     */
    public static class Base implements JsonArrayFormatVisitor {
        protected SerializationContext _provider;

        public Base() { }
        public Base(SerializationContext p) { _provider = p; }

        @Override
        public SerializationContext getContext() { return _provider; }

        @Override
        public void setContext(SerializationContext p) { _provider = p; }

        @Override
        public void itemsFormat(JsonFormatVisitable handler, JavaType elementType) { }

        @Override
        public void itemsFormat(JsonFormatTypes format) { }
    }
}

