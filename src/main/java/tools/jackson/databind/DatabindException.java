package tools.jackson.databind;

import java.io.Closeable;

import tools.jackson.core.*;
import tools.jackson.core.exc.JacksonIOException;

/**
 * Exception used to signal fatal problems with mapping of
 * content, distinct from low-level I/O problems (signaled using
 * simple {@link JacksonIOException}s) or data encoding/decoding
 * problems (signaled with {@link tools.jackson.core.exc.StreamReadException},
 * {@link tools.jackson.core.exc.StreamWriteException}).
 *<p>
 * One additional feature is the ability to denote relevant path
 * of references (during serialization/deserialization) to help in
 * troubleshooting.
 */
public class DatabindException
    extends JacksonException
{
    private static final long serialVersionUID = 3L;

    /*
    /**********************************************************************
    /* Life-cycle: constructors for local use, sub-classes
    /**********************************************************************
     */

    protected DatabindException(Closeable processor, String msg) {
        super(processor, msg);
    }

    protected DatabindException(Closeable processor, String msg, Throwable problem) {
        super(processor, msg, problem);
    }

    protected DatabindException(Closeable processor, String msg, TokenStreamLocation loc)
    {
        super(msg, loc, null);
    }

    protected DatabindException(String msg, TokenStreamLocation loc, Throwable rootCause) {
        super(msg, loc, rootCause);
    }

    protected DatabindException(String msg) {
        super(msg);
    }

    /*
    /**********************************************************************
    /* Life-cycle: simple factory methods (for actual construction)
    /**********************************************************************
     */

    public static DatabindException from(JsonParser p, String msg) {
        return new DatabindException(p, msg);
    }

    public static DatabindException from(JsonParser p, String msg, Throwable problem) {
        return new DatabindException(p, msg, problem);
    }

    public static DatabindException from(JsonGenerator g, String msg) {
        return new DatabindException(g, msg, (Throwable) null);
    }

    public static DatabindException from(JsonGenerator g, String msg, Throwable problem) {
        return new DatabindException(g, msg, problem);
    }

    public static DatabindException from(DeserializationContext ctxt, String msg) {
        return new DatabindException(_parser(ctxt), msg);
    }

    private static JsonParser _parser(DeserializationContext ctxt) {
        return (ctxt == null) ? null : ctxt.getParser();
    }

    public static DatabindException from(SerializationContext ctxt, String msg) {
        return new DatabindException(_generator(ctxt), msg);
    }

    public static DatabindException from(SerializationContext ctxt, String msg, Throwable problem) {
        // 17-Aug-2015, tatu: As per [databind#903] this is bit problematic as
        //   SerializationContext instance does not currently hold on to generator...
        return new DatabindException(_generator(ctxt), msg, problem);
    }

    private static JsonGenerator _generator(SerializationContext ctxt) {
        return (ctxt == null) ? null : ctxt.getGenerator();
    }
}
