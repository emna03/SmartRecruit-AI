package com.smartrecruit.common.json;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** Désérialiseurs sans conversion implicite, utilisés par {@link StrictInt} et {@link StrictBool}. */
public final class StrictDeserializers {

    private StrictDeserializers() {
    }

    public static final class IntegerOnly extends ValueDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT
                    || parser.getNumberType() != JsonParser.NumberType.INT) {
                return (Integer) context.handleUnexpectedToken(Integer.class, parser);
            }
            return parser.getIntValue();
        }
    }

    public static final class BooleanOnly extends ValueDeserializer<Boolean> {
        @Override
        public Boolean deserialize(JsonParser parser, DeserializationContext context) {
            JsonToken token = parser.currentToken();
            if (token != JsonToken.VALUE_TRUE && token != JsonToken.VALUE_FALSE) {
                return (Boolean) context.handleUnexpectedToken(Boolean.class, parser);
            }
            return token == JsonToken.VALUE_TRUE;
        }
    }
}
