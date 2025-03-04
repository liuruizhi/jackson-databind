package tools.jackson.databind.jsonFormatVisitors;

import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationContext;

/**
 * Interface for visitor callbacks, when type in question can be any of
 * legal JSON types.
 *<p>
 * In most cases it will make more sense to extend {@link JsonFormatVisitorWrapper.Base}
 * instead of directly implementing this interface.
 */
public interface JsonFormatVisitorWrapper extends JsonFormatVisitorWithSerializationContext
{
    /**
     * @param type Declared type of visited property (or List element) in Java
     */
    public JsonObjectFormatVisitor expectObjectFormat(JavaType type);

    /**
     * @param type Declared type of visited property (or List element) in Java
     */
    public JsonArrayFormatVisitor expectArrayFormat(JavaType type);

    /**
     * @param type Declared type of visited property (or List element) in Java
     */
    public JsonStringFormatVisitor expectStringFormat(JavaType type);

    /**
     * @param type Declared type of visited property (or List element) in Java
     */
    public JsonNumberFormatVisitor expectNumberFormat(JavaType type);

    /**
     * @param type Declared type of visited property (or List element) in Java
     */
    public JsonIntegerFormatVisitor expectIntegerFormat(JavaType type);

    /**
     * @param type Declared type of visited property (or List element) in Java
     */
    public JsonBooleanFormatVisitor expectBooleanFormat(JavaType type);

    /**
     * @param type Declared type of visited property (or List element) in Java
     */
    public JsonNullFormatVisitor expectNullFormat(JavaType type);

    /**
     * @param type Declared type of visited property (or List element) in Java
     */
    public JsonAnyFormatVisitor expectAnyFormat(JavaType type);

    /**
     * Method called when type is of Java {@link java.util.Map} type, and will
     * be serialized as a JSON Object.
     *
     * @since 2.2
     */
    public JsonMapFormatVisitor expectMapFormat(JavaType type);

    /**
     * Empty "no-op" implementation of {@link JsonFormatVisitorWrapper}, suitable for
     * sub-classing. Does implement {@link #setContext(SerializationContext)} and
     * {@link #getContext()} as expected; other methods simply return null
     * and do nothing.
     *
     * @since 2.5
     */
    public static class Base implements JsonFormatVisitorWrapper {
        protected SerializationContext _context;

        public Base() { }

        public Base(SerializationContext p) {
            _context = p;
        }

        @Override
        public SerializationContext getContext() {
            return _context;
        }

        @Override
        public void setContext(SerializationContext p) {
            _context = p;
        }

        @Override
        public JsonObjectFormatVisitor expectObjectFormat(JavaType type) {
            return null;
        }

        @Override
        public JsonArrayFormatVisitor expectArrayFormat(JavaType type) {
            return null;
        }

        @Override
        public JsonStringFormatVisitor expectStringFormat(JavaType type) {
            return null;
        }

        @Override
        public JsonNumberFormatVisitor expectNumberFormat(JavaType type) {
            return null;
        }

        @Override
        public JsonIntegerFormatVisitor expectIntegerFormat(JavaType type) {
            return null;
        }

        @Override
        public JsonBooleanFormatVisitor expectBooleanFormat(JavaType type) {
            return null;
        }

        @Override
        public JsonNullFormatVisitor expectNullFormat(JavaType type) {
            return null;
        }

        @Override
        public JsonAnyFormatVisitor expectAnyFormat(JavaType type) {
            return null;
        }

        @Override
        public JsonMapFormatVisitor expectMapFormat(JavaType type) {
            return null;
        }
   }
}
