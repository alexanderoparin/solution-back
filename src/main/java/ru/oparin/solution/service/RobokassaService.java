package ru.oparin.solution.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.oparin.solution.config.RobokassaProperties;
import ru.oparin.solution.dto.payment.ReceiptDto;
import ru.oparin.solution.dto.payment.ReceiptItemDto;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Сервис для работы с Робокассой: формирование URL оплаты и проверка подписи callback.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RobokassaService {

    private final RobokassaProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * Строит URL для перенаправления пользователя на оплату в Робокассе.
     *
     * @param outSum     сумма платежа
     * @param invId      идентификатор заказа (id платежа в нашей БД)
     * @param description описание платежа
     * @return полный URL для редиректа
     */
    public String buildPaymentUrl(BigDecimal outSum, String invId, String description) {
        ReceiptDto receipt = createDefaultReceipt(description, outSum);
        String signature = createSignature(outSum, invId, receipt);
        return buildUrl(outSum, invId, description, signature, receipt);
    }

    /**
     * Проверяет подпись Result URL от Робокассы.
     * Формат проверки: MD5(OutSum:InvId:Password#2).
     *
     * @param outSum    сумма из callback
     * @param invId     номер счёта из callback
     * @param signature подпись SignatureValue из callback
     * @return true, если подпись верна
     */
    public boolean verifyResultSignature(String outSum, String invId, String signature) {
        if (outSum == null || invId == null || signature == null) {
            return false;
        }
        String checkString = String.format("%s:%s:%s", outSum, invId, properties.getPassword2());
        String calculated = md5(checkString);
        boolean valid = calculated.equalsIgnoreCase(signature);
        log.info("Проверка подписи Робокассы invId={}: {}", invId, valid ? "OK" : "FAIL");
        return valid;
    }

    private String createSignature(BigDecimal amount, String invId, ReceiptDto receipt) {
        try {
            String receiptJson = objectMapper.writeValueAsString(receipt);
            String receiptEncoded = URLEncoder.encode(receiptJson, StandardCharsets.UTF_8);
            String checkString = String.format("%s:%s:%s:%s:%s",
                    properties.getMerchantLogin(),
                    amount.toString(),
                    invId,
                    receiptEncoded,
                    properties.getPassword1());
            return md5(checkString);
        } catch (JsonProcessingException e) {
            log.error("Ошибка сериализации чека: {}", e.getMessage());
            throw new RuntimeException("Не удалось создать подпись для платежа", e);
        }
    }

    private String buildUrl(BigDecimal amount, String invId, String description, String signature, ReceiptDto receipt) {
        StringBuilder url = new StringBuilder();
        url.append("https://auth.robokassa.ru/Merchant/Index.aspx");
        url.append("?MerchantLogin=").append(properties.getMerchantLogin());
        url.append("&OutSum=").append(amount.toString());
        url.append("&InvId=").append(invId);
        url.append("&Description=").append(URLEncoder.encode(description, StandardCharsets.UTF_8));
        url.append("&SignatureValue=").append(signature);
        url.append("&Culture=ru");
        url.append("&Encoding=utf-8");
        url.append("&ResultURL=").append(URLEncoder.encode(properties.getResultUrl(), StandardCharsets.UTF_8));
        url.append("&SuccessURL=").append(URLEncoder.encode(properties.getSuccessUrl(), StandardCharsets.UTF_8));
        url.append("&FailURL=").append(URLEncoder.encode(properties.getFailUrl(), StandardCharsets.UTF_8));

        try {
            String receiptJson = objectMapper.writeValueAsString(receipt);
            String receiptEncoded = URLEncoder.encode(receiptJson, StandardCharsets.UTF_8);
            String receiptDoubleEncoded = URLEncoder.encode(receiptEncoded, StandardCharsets.UTF_8);
            url.append("&Receipt=").append(receiptDoubleEncoded);
        } catch (JsonProcessingException e) {
            log.warn("Не удалось добавить Receipt в URL: {}", e.getMessage());
        }

        if (properties.isTest()) {
            url.append("&IsTest=1");
        }
        return url.toString();
    }

    private ReceiptDto createDefaultReceipt(String description, BigDecimal amount) {
        ReceiptItemDto item = ReceiptItemDto.builder()
                .name(description)
                .quantity(1)
                .sum(amount)
                .paymentMethod("full_prepayment")
                .paymentObject("service")
                .tax("none")
                .build();
        return ReceiptDto.builder()
                .sno("usn_income")
                .items(List.of(item))
                .build();
    }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 не найден", e);
        }
    }
}
