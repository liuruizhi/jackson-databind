package tools.jackson.databind.node;

import java.math.BigDecimal;
import java.math.BigInteger;

import tools.jackson.core.*;
import tools.jackson.databind.SerializationContext;

/**
 * Numeric node that contains simple 16-bit integer values.
 */
public class ShortNode
    extends NumericNode
{
    private static final long serialVersionUID = 3L;

    protected final short _value;

    /*
    /**********************************************************************
    /* Construction
    /**********************************************************************
     */

    public ShortNode(short v) { _value = v; }

    public static ShortNode valueOf(short l) { return new ShortNode(l); }

    /*
    /**********************************************************************
    /* Overridden JsonNode methods
    /**********************************************************************
     */

    @Override public JsonToken asToken() { return JsonToken.VALUE_NUMBER_INT; }

    @Override
    public JsonParser.NumberType numberType() { return JsonParser.NumberType.INT; }			// should be SHORT


    @Override
    public boolean isIntegralNumber() { return true; }

    @Override
    public boolean isShort() { return true; }

    @Override public boolean canConvertToInt() { return true; }
    @Override public boolean canConvertToLong() { return true; }

    @Override
    public Number numberValue() {
        return Short.valueOf(_value);
    }

    @Override
    public short shortValue() { return _value; }

    @Override
    public int intValue() { return _value; }

    @Override
    public long longValue() { return _value; }

    @Override
    public float floatValue() { return _value; }

    @Override
    public double doubleValue() { return _value; }

    @Override
    public BigDecimal decimalValue() { return BigDecimal.valueOf(_value); }

    @Override
    public BigInteger bigIntegerValue() { return BigInteger.valueOf(_value); }

    @Override
    public String asString() {
        return String.valueOf(_value);
    }

    @Override
    public boolean asBoolean(boolean defaultValue) {
        return _value != 0;
    }

    @Override
    public final void serialize(JsonGenerator g, SerializationContext provider)
        throws JacksonException
    {
        g.writeNumber(_value);
    }

    @Override
    public boolean equals(Object o)
    {
        if (o == this) return true;
        if (o == null) return false;
        if (o instanceof ShortNode) {
            return ((ShortNode) o)._value == _value;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return _value;
    }
}
