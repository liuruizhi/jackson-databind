package tools.jackson.databind.ext.jdk8;

import java.util.stream.LongStream;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * {@link LongStream} serializer
 * <p>
 * Unfortunately there to common ancestor between number base stream, so we need to define each in a specific class
 * </p>
 */
public class LongStreamSerializer extends StdSerializer<LongStream>
{
    /**
     * Singleton instance
     */
    public static final LongStreamSerializer INSTANCE = new LongStreamSerializer();

    private LongStreamSerializer() {
        super(LongStream.class);
    }

    @Override
    public void serialize(LongStream stream, JsonGenerator g, SerializationContext ctxt)
        throws JacksonException
    {
        try (LongStream ls = stream) {
            g.writeStartArray(ls);
            ls.forEach(value -> {
                g.writeNumber(value);
            });
            g.writeEndArray();
        } catch (Exception e) {
            // For most regular serializers we won't both handling but streams are typically
            // root values so
            wrapAndThrow(ctxt, e, stream, g.streamWriteContext().getCurrentIndex());
        }
    }
}
