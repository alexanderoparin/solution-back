package ru.oparin.solution.service.tochka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import ru.oparin.solution.config.TochkaProperties;
import ru.oparin.solution.exception.UserException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * HTTP-клиент Точка.API: {@value ru.oparin.solution.config.TochkaProperties#CREATE_PAYMENT_WITH_RECEIPT_PATH}
 * (платёжная ссылка с фискализацией чека).
 *
 * @see <a href="https://developers.tochka.com/docs/tochka-api/api/create-payment-operation-with-receipt-acquiring-v-1-0-payments-with-receipt-post">Create Payment Operation With Receipt</a>
 */
@Service
@Slf4j
public class TochkaApiClient {

    private final TochkaProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public TochkaApiClient(TochkaProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getEffectiveBaseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.getJwtToken())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Создаёт платёжную ссылку с фискализацией чека.
     */
    public TochkaPaymentOperationResult createPaymentWithReceipt(TochkaCreatePaymentParams params) {
        validateConfigured();
        ObjectNode body = buildCreatePaymentBody(params);
        try {
            String responseBody = restClient.post()
                    .uri(TochkaProperties.CREATE_PAYMENT_WITH_RECEIPT_PATH)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parseCreatePaymentResponse(responseBody);
        } catch (RestClientResponseException e) {
            log.error("Tochka create payment failed: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new UserException(
                    "Не удалось создать платёжную ссылку. Попробуйте позже.",
                    HttpStatus.BAD_GATEWAY
            );
        } catch (UserException e) {
            throw e;
        } catch (Exception e) {
            log.error("Tochka create payment error", e);
            throw new UserException(
                    "Ошибка при обращении к платёжной системе",
                    HttpStatus.BAD_GATEWAY
            );
        }
    }

    /**
     * Запрашивает статус платёжной операции (для polling после redirect).
     */
    public TochkaPaymentOperationStatus getPaymentOperation(String operationId) {
        validateConfigured();
        try {
            String responseBody = restClient.get()
                    .uri(TochkaProperties.PAYMENT_INFO_PATH, operationId)
                    .retrieve()
                    .body(String.class);
            return parsePaymentStatusResponse(responseBody, operationId);
        } catch (RestClientResponseException e) {
            log.warn("Tochka get payment failed: operationId={}, status={}", operationId, e.getStatusCode());
            throw new UserException("Платёж не найден в платёжной системе", HttpStatus.NOT_FOUND);
        } catch (UserException e) {
            throw e;
        } catch (Exception e) {
            log.error("Tochka get payment error: operationId={}", operationId, e);
            throw new UserException("Ошибка при проверке статуса платежа", HttpStatus.BAD_GATEWAY);
        }
    }

    private void validateConfigured() {
        if (!properties.isConfiguredForPayments()) {
            throw new UserException("Оплата временно недоступна", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private ObjectNode buildCreatePaymentBody(TochkaCreatePaymentParams params) {
        BigDecimal amount = params.getAmount().setScale(2, RoundingMode.HALF_UP);

        ObjectNode client = objectMapper.createObjectNode();
        client.put("email", params.getClientEmail());

        ObjectNode item = objectMapper.createObjectNode();
        item.put("name", truncate(params.getItemName(), 256));
        item.put("amount", amount);
        item.put("quantity", 1);
        item.put("paymentObject", "service");
        item.put("paymentMethod", "full_payment");
        item.put("vatType", TochkaProperties.VAT_TYPE);

        ArrayNode items = objectMapper.createArrayNode();
        items.add(item);

        ArrayNode paymentModes = objectMapper.createArrayNode();
        for (String mode : TochkaProperties.ALL_PAYMENT_MODES) {
            paymentModes.add(mode);
        }

        ObjectNode data = objectMapper.createObjectNode();
        data.put("customerCode", TochkaProperties.CUSTOMER_CODE);
        data.put("amount", amount);
        data.put("purpose", truncate(params.getPurpose(), 140));
        data.put("paymentLinkId", params.getPaymentLinkId());
        data.put("redirectUrl", params.getRedirectUrl());
        data.put("failRedirectUrl", params.getFailRedirectUrl());
        data.set("paymentMode", paymentModes);
        data.put("taxSystemCode", TochkaProperties.TAX_SYSTEM_CODE);
        data.set("Client", client);
        data.set("Items", items);

        ObjectNode root = objectMapper.createObjectNode();
        root.set("Data", data);
        return root;
    }

    private TochkaPaymentOperationResult parseCreatePaymentResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode data = root.path("Data");
        String operationId = textOrNull(data, "operationId");
        String paymentLink = textOrNull(data, "paymentLink");
        if (operationId == null || paymentLink == null) {
            log.error("Unexpected Tochka create response: {}", responseBody);
            throw new UserException("Некорректный ответ платёжной системы", HttpStatus.BAD_GATEWAY);
        }
        return TochkaPaymentOperationResult.builder()
                .operationId(operationId)
                .paymentLink(paymentLink)
                .status(textOrNull(data, "status"))
                .build();
    }

    private TochkaPaymentOperationStatus parsePaymentStatusResponse(String responseBody, String operationId)
            throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode data = root.path("Data");
        if (data.isMissingNode() || data.isNull()) {
            data = root;
        }
        return TochkaPaymentOperationStatus.builder()
                .operationId(operationId)
                .status(textOrNull(data, "status"))
                .paymentLinkId(textOrNull(data, "paymentLinkId"))
                .build();
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }
}
