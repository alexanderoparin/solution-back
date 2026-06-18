package ru.oparin.solution.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Настройки интеграции с Точка.API (интернет-эквайринг).
 *
 * @see <a href="https://developers.tochka.com/docs/tochka-api/api/create-payment-operation-with-receipt-acquiring-v-1-0-payments-with-receipt-post">Create Payment Operation With Receipt</a>
 */
@Getter
@Setter
@Slf4j
@Component
@ConfigurationProperties(prefix = "tochka")
public class TochkaProperties {

    /** Продакшен: {@value PRODUCTION_BASE_URL}. */
    public static final String PRODUCTION_BASE_URL = "https://enter.tochka.com/uapi/";

    /** Песочница: {@value SANDBOX_BASE_URL}. */
    public static final String SANDBOX_BASE_URL = "https://enter.tochka.com/sandbox/v2/";

    /** Код клиента в Точка (customerType: Business). */
    public static final String CUSTOMER_CODE = "305028716";

    /** POST — создание платёжной ссылки с чеком (относительно base URL). */
    public static final String CREATE_PAYMENT_WITH_RECEIPT_PATH = "acquiring/v1.0/payments_with_receipt";

    /** GET — статус операции (относительно base URL). */
    public static final String PAYMENT_INFO_PATH = "acquiring/v1.0/payments/{operationId}";

    /** Все способы оплаты Точка.API; на странице оплаты покупатель выберет доступные. */
    public static final List<String> ALL_PAYMENT_MODES = List.of("card", "tinkoff", "sbp", "dolyame");

    /** Система налогообложения в чеке (УСН доходы). */
    public static final String TAX_SYSTEM_CODE = "usn_income";

    /** Ставка НДС в позиции чека. */
    public static final String VAT_TYPE = "none";

    private boolean enabled = false;

    /**
     * {@code true} — песочница; {@code false} — прод.
     */
    private boolean sandbox = false;

    private String jwtToken = "";

    private String clientId = "";

    private String redirectSuccessUrl = "";

    private String redirectFailUrl = "";

    private String webhookPath = "/webhooks/tochka";

    /**
     * Полный URL метода Create Payment Operation With Receipt.
     */
    public String getCreatePaymentWithReceiptUrl() {
        return getEffectiveBaseUrl() + CREATE_PAYMENT_WITH_RECEIPT_PATH;
    }

    /**
     * Базовый URL API: прод или песочница по флагу {@link #sandbox}.
     */
    public String getEffectiveBaseUrl() {
        return sandbox ? SANDBOX_BASE_URL : PRODUCTION_BASE_URL;
    }

    /**
     * Проверяет, что оплата через Точка настроена для создания платёжных ссылок.
     */
    public boolean isConfiguredForPayments() {
        return enabled && jwtToken != null && !jwtToken.isBlank();
    }

    @PostConstruct
    void logConfiguration() {
        if (!enabled) {
            return;
        }
        String mode = sandbox ? "песочница" : "прод";
        log.info("Точка.API: режим {}, POST {}", mode, getCreatePaymentWithReceiptUrl());
    }
}
