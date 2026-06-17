package ru.oparin.solution.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Разбор webhook-событий Точка.API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TochkaWebhookService {

    private final ObjectMapper objectMapper;

    /**
     * Извлекает operationId и статус из тела webhook acquiringInternetPayment.
     */
    public TochkaWebhookEvent parseAcquiringPaymentEvent(String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            JsonNode data = root.path("Data");
            if (data.isMissingNode() || data.isNull()) {
                data = root;
            }
            String operationId = firstText(root, data, "operationId");
            String status = firstText(root, data, "status");
            String paymentLinkId = firstText(root, data, "paymentLinkId");
            String webhookType = firstText(root, root, "webhookType");
            if (operationId == null) {
                log.warn("Tochka webhook without operationId: {}", rawBody);
                return null;
            }
            return new TochkaWebhookEvent(webhookType, operationId, status, paymentLinkId);
        } catch (Exception e) {
            log.warn("Failed to parse Tochka webhook: {}", e.getMessage());
            return null;
        }
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
