package ru.oparin.solution.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.oparin.solution.service.tochka.TochkaWebhookPublicKeyProvider;

/**
 * Разбор webhook-событий Точка.API.
 * Тело webhook — JWT-строка (RS256), подписанная Точка Банк.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TochkaWebhookService {

    private final ObjectMapper objectMapper;
    private final TochkaWebhookPublicKeyProvider publicKeyProvider;

    /**
     * Извлекает operationId и статус из тела webhook acquiringInternetPayment.
     * Возвращает {@code null}, если тело невалидно или подпись не прошла проверку.
     */
    public TochkaWebhookEvent parseAcquiringPaymentEvent(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return null;
        }
        String body = rawBody.trim();
        if (body.startsWith("{")) {
            return parseJsonEvent(body);
        }
        return parseJwtEvent(body);
    }

    private TochkaWebhookEvent parseJwtEvent(String jwt) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(publicKeyProvider.getPublicKey())
                    .build()
                    .parseSignedClaims(jwt)
                    .getPayload();
            return new TochkaWebhookEvent(
                    claimAsString(claims, "webhookType"),
                    claimAsString(claims, "operationId"),
                    claimAsString(claims, "status"),
                    claimAsString(claims, "paymentLinkId")
            );
        } catch (Exception e) {
            log.warn("Failed to verify or parse Tochka webhook JWT: {}", e.getMessage());
            return null;
        }
    }

    private TochkaWebhookEvent parseJsonEvent(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("Data");
            if (data.isMissingNode() || data.isNull()) {
                data = root;
            }
            return new TochkaWebhookEvent(
                    firstText(root, root, "webhookType"),
                    firstText(root, data, "operationId"),
                    firstText(root, data, "status"),
                    firstText(root, data, "paymentLinkId")
            );
        } catch (Exception e) {
            log.warn("Failed to parse Tochka webhook JSON: {}", e.getMessage());
            return null;
        }
    }

    private static String claimAsString(Claims claims, String name) {
        Object value = claims.get(name);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    private static String firstText(JsonNode root, JsonNode data, String field) {
        String fromData = textOrNull(data, field);
        if (fromData != null) {
            return fromData;
        }
        return textOrNull(root, field);
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }

    /**
     * Событие webhook по платёжной операции.
     */
    public record TochkaWebhookEvent(
            String webhookType,
            String operationId,
            String status,
            String paymentLinkId
    ) {
    }
}
