package tools.jackson.databind.ext.jdk8;

import java.util.OptionalDouble;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.JsonTokenId;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.type.LogicalType;

public class OptionalDoubleDeserializer extends BaseScalarOptionalDeserializer<OptionalDouble>
{
    static final OptionalDoubleDeserializer INSTANCE = new OptionalDoubleDeserializer();

    public OptionalDoubleDeserializer() {
        super(OptionalDouble.class, OptionalDouble.empty());
    }

    @Override
    public LogicalType logicalType() { return LogicalType.Float; }

    @Override
    public OptionalDouble deserialize(JsonParser p, DeserializationContext ctxt)
        throws JacksonException
    {
        // minor optimization, first, for common case
        if (p.hasToken(JsonToken.VALUE_NUMBER_FLOAT)) {
            return OptionalDouble.of(p.getDoubleValue());
        }
        switch (p.currentTokenId()) {
        case JsonTokenId.ID_STRING:
            {
                String text = p.getString();
                // 19-Nov-2020, ckozak: see jackson-databind#2942: Special case, floating point special
                //     values as String (e.g. "NaN", "Infinity", "-Infinity" need to be considered
                //     "native" representation as JSON does not allow as numbers, and hence not bound
                //     by coercion rules
                Double specialValue = _checkDoubleSpecialValue(text);
                if (specialValue != null) {
                    return OptionalDouble.of(specialValue);
                }
                CoercionAction act = _checkFromStringCoercion(ctxt, text);
                if (act == CoercionAction.AsNull) {
                    return (OptionalDouble) getNullValue(ctxt);
                }
                if (act == CoercionAction.AsEmpty) {
                    return (OptionalDouble) getEmptyValue(ctxt);
                }
                text = text.trim();
                if (_checkTextualNull(ctxt, text)) {
                    return _empty;
                }
                return OptionalDouble.of(_parseDoublePrimitive(p, ctxt, text));
            }
        case JsonTokenId.ID_NUMBER_INT: // coercion here should be fine
            return OptionalDouble.of(p.getDoubleValue());
        case JsonTokenId.ID_NULL:
            return _empty;
        case JsonTokenId.ID_START_ARRAY:
            if (ctxt.isEnabled(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)) {
                p.nextToken();
                final OptionalDouble parsed = deserialize(p, ctxt);
                _verifyEndArrayForSingle(p, ctxt);
                return parsed;
            }
            break;
        }
        return (OptionalDouble) ctxt.handleUnexpectedToken(getValueType(ctxt), p);
    }
}
