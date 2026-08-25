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
 * При недоступности URL (SSL/сеть) приложение стартует; ключ подгружается лениво при первом webhook.
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

    private volatile String lastLoadError;

    /**
     * Пытается загрузить JWK при старте; ошибка не валит контекст (локальный SSL/сеть).
     */
    @PostConstruct
    void loadPublicKeyOnStartup() {
        tryLoadPublicKey();
        if (publicKey == null) {
            log.warn("Tochka webhook: публичный ключ не загружен при старте ({}). "
                    + "Приложение продолжит работу; повтор при первом webhook.", lastLoadError);
        }
    }

    /**
     * Возвращает публичный ключ для верификации JWT webhook.
     * При отсутствии ключа — повторная попытка загрузки.
     *
     * @throws IllegalStateException если ключ недоступен
     */
    public RSAPublicKey getPublicKey() {
        RSAPublicKey key = publicKey;
        if (key != null) {
            return key;
        }
        synchronized (this) {
            if (publicKey == null) {
                tryLoadPublicKey();
            }
            if (publicKey == null) {
                throw new IllegalStateException(
                        "Публичный ключ Точка для webhook недоступен: " + lastLoadError);
            }
            return publicKey;
        }
    }

    private void tryLoadPublicKey() {
        try {
            String json = restTemplate.getForObject(PUBLIC_KEY_URL, String.class);
            if (json == null || json.isBlank()) {
                lastLoadError = "пустой ответ при загрузке публичного ключа";
                return;
            }
            publicKey = parseJwk(json, objectMapper);
            lastLoadError = null;
            log.info("Tochka webhook: публичный ключ RS256 загружен");
        } catch (Exception e) {
            lastLoadError = e.getMessage();
            log.debug("Tochka webhook: не удалось загрузить публичный ключ: {}", e.getMessage());
        }
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
