package ru.oparin.solution.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * Извлекает claim {@code sid} из JWT WB API-ключа без проверки подписи.
 * Нужен для идентификации одного и того же магазина WB при перевыпуске токена.
 */
public final class WbApiKeyJwtSid {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WbApiKeyJwtSid() {
    }

    /**
     * @param apiKey JWT или иной API-ключ WB
     * @return sid из payload либо empty, если ключ не JWT / нет claim
     */
    public static Optional<String> extract(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }
        String trimmed = apiKey.trim();
        String[] parts = trimmed.split("\\.");
        if (parts.length < 2) {
            return Optional.empty();
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(padBase64Url(parts[1]));
            JsonNode root = MAPPER.readTree(new String(decoded, StandardCharsets.UTF_8));
            JsonNode sidNode = root.get("sid");
            if (sidNode == null || sidNode.isNull()) {
                return Optional.empty();
            }
            String sid = sidNode.asText();
            if (sid == null || sid.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(sid.trim());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String padBase64Url(String value) {
        int mod = value.length() % 4;
        if (mod == 0) {
            return value;
        }
        return value + "====".substring(mod);
    }
}
