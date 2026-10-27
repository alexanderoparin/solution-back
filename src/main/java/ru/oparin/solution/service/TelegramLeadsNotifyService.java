package ru.oparin.solution.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Дублирование заявок с лендинга / индивидуального тарифа в Telegram.
 * Ошибки Telegram не пробрасываются — заявка на почту уже ушла.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramLeadsNotifyService {

    private static final int CONNECT_TIMEOUT_MS = 8_000;
    private static final int READ_TIMEOUT_MS = 15_000;

    private final ObjectMapper objectMapper;

    @Value("${app.telegram-leads.enabled:false}")
    private boolean enabled;

    @Value("${app.telegram-leads.bot-token:}")
    private String botToken;

    @Value("${app.telegram-leads.chat-id:}")
    private String chatId;

    /**
     * SOCKS5/HTTP прокси к api.telegram.org, например {@code socks5://wb-drr-xray:1080}.
     * Пусто — прямое подключение.
     */
    @Value("${app.telegram-leads.proxy:}")
    private String proxyUrl;

    /**
     * Отправляет текст заявки в настроенный чат. При выключенном или битом конфиге — no-op.
     *
     * @param text полный текст сообщения
     */
    public void notifyLead(String text) {
        if (!enabled) {
            return;
        }
        if (botToken == null || botToken.isBlank() || chatId == null || chatId.isBlank()) {
            log.warn("Telegram leads: enabled, но bot-token или chat-id не заданы — пропуск");
            return;
        }
        if (text == null || text.isBlank()) {
            return;
        }
        try {
            RestTemplate restTemplate = createRestTemplate();
            String url = "https://api.telegram.org/bot" + botToken.trim() + "/sendMessage";
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("chat_id", chatId.trim());
            body.put("text", truncate(text.trim(), 4000));
            body.put("disable_web_page_preview", true);

            // Пишем UTF-8 байты сами — StringHttpMessageConverter иначе может исказить кириллицу.
            byte[] jsonUtf8 = objectMapper.writeValueAsBytes(body);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
            headers.setContentLength(jsonUtf8.length);
            restTemplate.postForEntity(URI.create(url), new HttpEntity<>(jsonUtf8, headers), String.class);
            log.info("Заявка продублирована в Telegram chat_id={}", chatId.trim());
        } catch (Exception e) {
            log.error("Не удалось отправить заявку в Telegram: {}", e.getMessage(), e);
        }
    }

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        Proxy proxy = resolveProxy(proxyUrl);
        if (proxy != null) {
            factory.setProxy(proxy);
        }
        return new RestTemplate(factory);
    }

    /**
     * Разбор {@code socks5://host:port} / {@code http://host:port}.
     */
    static Proxy resolveProxy(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        try {
            URI uri = URI.create(value.contains("://") ? value : "socks5://" + value);
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase() : "socks5";
            String host = uri.getHost();
            int port = uri.getPort();
            if (host == null || port <= 0) {
                logWarnBadProxy(value);
                return null;
            }
            Proxy.Type type = "http".equals(scheme) || "https".equals(scheme)
                    ? Proxy.Type.HTTP
                    : Proxy.Type.SOCKS;
            return new Proxy(type, new InetSocketAddress(host, port));
        } catch (Exception e) {
            logWarnBadProxy(value);
            return null;
        }
    }

    private static void logWarnBadProxy(String value) {
        log.warn("Telegram leads: некорректный proxy «{}» — прямое подключение", value);
    }

    private static String truncate(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max - 1) + "…";
    }
}
