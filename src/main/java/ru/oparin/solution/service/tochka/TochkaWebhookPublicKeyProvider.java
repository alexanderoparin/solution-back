package ru.oparin.solution.service.tochka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

/**
 * Публичный ключ Точка Банк для проверки подписи входящих webhook (RS256).
 *
 * @see <a href="https://enter.tochka.com/doc/openapi/static/keys/public">Публичный ключ Open API</a>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TochkaWebhookPublicKeyProvider {

    private static final String PUBLIC_KEY_URL = "https://enter.tochka.com/doc/openapi/static/keys/public";

    private final ObjectMapper objectMapper;

    private final RestTemplate restTemplate = new RestTemplate();

    private volatile RSAPublicKey publicKey;

    /**
     * Загружает JWK с сайта Точка при старте приложения.
     */
    @PostConstruct
    void loadPublicKey() {
        try {
            String json = restTemplate.getForObject(PUBLIC_KEY_URL, String.class);
            if (json == null || json.isBlank()) {
                throw new IllegalStateException("Пустой ответ при загрузке публичного ключа");
            }
            publicKey = parseJwk(json, objectMapper);
            log.info("Tochka webhook: публичный ключ RS256 загружен");
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Не удалось загрузить публичный ключ Точка для webhook: " + e.getMessage(), e);
        }
    }

    /**
     * Возвращает публичный ключ для верификации JWT webhook.
     */
    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    static RSAPublicKey parseJwk(String json, ObjectMapper objectMapper) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        String modulus = node.path("n").asText(null);
        String exponent = node.path("e").asText(null);
        if (modulus == null || exponent == null) {
            throw new IllegalArgumentException("JWK не содержит полей n/e");
        }
        return fromJwk(modulus, exponent);
    }

    private static RSAPublicKey fromJwk(String modulusBase64Url, String exponentBase64Url) throws Exception {
        byte[] modulusBytes = Base64.getUrlDecoder().decode(modulusBase64Url);
        byte[] exponentBytes = Base64.getUrlDecoder().decode(exponentBase64Url);
        RSAPublicKeySpec spec = new RSAPublicKeySpec(
                new BigInteger(1, modulusBytes),
                new BigInteger(1, exponentBytes)
        );
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
    }
}
