package ru.oparin.solution.dto.ozon;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/**
 * Ozon иногда отдаёт поле как строку, иногда как массив строк (например {@code primary_image}).
 */
public class OzonStringOrStringArrayDeserializer extends JsonDeserializer<String> {

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonToken token = parser.currentToken();
        if (token == JsonToken.VALUE_STRING) {
            String value = parser.getText();
            return value != null && !value.isBlank() ? value.trim() : null;
        }
        if (token == JsonToken.START_ARRAY) {
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                if (parser.currentToken() == JsonToken.VALUE_STRING) {
                    String value = parser.getText();
                    if (value != null && !value.isBlank()) {
                        return value.trim();
                    }
                }
            }
            return null;
        }
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }
        parser.skipChildren();
        return null;
    }
}
