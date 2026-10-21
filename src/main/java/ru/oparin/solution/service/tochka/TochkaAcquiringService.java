package ru.oparin.solution.service.tochka;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import ru.oparin.solution.config.TochkaProperties;
import ru.oparin.solution.model.Plan;
import ru.oparin.solution.model.User;

import java.math.BigDecimal;

/**
 * Оформление платёжных ссылок Точка Банк для подписок.
 */
@Service
@RequiredArgsConstructor
public class TochkaAcquiringService {

    private final TochkaApiClient tochkaApiClient;
    private final TochkaProperties tochkaProperties;

    /**
     * Создаёт платёжную ссылку с чеком для тарифного плана (сумма = цена плана).
     */
    public TochkaPaymentOperationResult createSubscriptionPayment(
            User user,
            Plan plan,
            Long paymentId,
            String brandName
    ) {
        return createSubscriptionPayment(user, plan, paymentId, brandName, plan.getPriceRub());
    }

    /**
     * Создаёт платёжную ссылку с произвольной суммой (например prorate-апгрейд).
     */
    public TochkaPaymentOperationResult createSubscriptionPayment(
            User user,
            Plan plan,
            Long paymentId,
            String brandName,
            BigDecimal amount
    ) {
        String paymentLinkId = "cm-" + paymentId;
        String successUrl = buildRedirectUrl(tochkaProperties.getRedirectSuccessUrl(), paymentId);
        String failUrl = buildRedirectUrl(tochkaProperties.getRedirectFailUrl(), paymentId);
        String purpose = truncate("Подписка " + brandName + ": " + plan.getName(), 140);

        return tochkaApiClient.createPaymentWithReceipt(
                TochkaCreatePaymentParams.builder()
                        .amount(amount)
                        .purpose(purpose)
                        .paymentLinkId(paymentLinkId)
                        .redirectUrl(successUrl)
                        .failRedirectUrl(failUrl)
                        .clientEmail(user.getEmail())
                        .itemName(plan.getName())
                        .build()
        );
    }

    /**
     * Запрашивает актуальный статус операции в Точка.API.
     */
    public TochkaPaymentOperationStatus fetchOperationStatus(String operationId) {
        return tochkaApiClient.getPaymentOperation(operationId);
    }

    private String buildRedirectUrl(String baseUrl, Long paymentId) {
        return UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("paymentId", paymentId)
                .build()
                .toUriString();
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }
}
