package ru.oparin.solution.service.ozon;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Определяет Premium в ЛК Ozon: без подписки {@code /v1/analytics/data} ограничен последними 3 месяцами.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OzonPremiumLkProbeService {

    private static final ZoneId OZON_ZONE = ZoneId.of("Europe/Moscow");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final List<String> PROBE_METRICS = List.of("ordered_units", "revenue");
    private static final List<String> PROBE_DIMENSION = List.of("day");

    private static final int PROBE_PERIOD_DAYS = 6;
    private static final int MAX_ATTEMPTS = 3;
    private static final long RATE_LIMIT_RETRY_MS = 65_000L;

    private final OzonProductsApiClient productsApiClient;

    /**
     * Probe: запрос analytics за период ~4 месяца назад (вне лимита 3 месяцев без Premium).
     */
    public OzonPremiumLkProbeResult probe(String clientId, String apiKey) {
        if (clientId == null || clientId.isBlank() || apiKey == null || apiKey.isBlank()) {
            return OzonPremiumLkProbeResult.INCONCLUSIVE;
        }
        LocalDate today = LocalDate.now(OZON_ZONE);
        LocalDate periodFrom = today.minusMonths(4).withDayOfMonth(1);
        LocalDate periodTo = periodFrom.plusDays(PROBE_PERIOD_DAYS);
        if (!periodTo.isAfter(periodFrom)) {
            return OzonPremiumLkProbeResult.INCONCLUSIVE;
        }

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String dateFrom = formatProbeDateFrom(periodFrom, attempt);
            String dateTo = formatProbeDateTo(periodTo, attempt);
            try {
                productsApiClient.getAnalyticsData(
                        clientId,
                        apiKey,
                        dateFrom,
                        dateTo,
                        1,
                        0,
                        PROBE_METRICS,
                        PROBE_DIMENSION
                );
                log.info(
                        "Ozon Premium LK probe: lookback OK — HAS_PREMIUM, clientId={}, period={}..{}, format={}",
                        clientId,
                        periodFrom,
                        periodTo,
                        attempt == 0 ? "date" : "instant"
                );
                return OzonPremiumLkProbeResult.HAS_PREMIUM;
            } catch (HttpClientErrorException e) {
                if (isPremiumLkDenied(e)) {
                    log.info(
                            "Ozon Premium LK probe: lookback denied — NO_PREMIUM, clientId={}, HTTP {}",
                            clientId,
                            e.getStatusCode().value()
                    );
                    return OzonPremiumLkProbeResult.NO_PREMIUM;
                }
                int status = e.getStatusCode().value();
                if (status == 429 && attempt < MAX_ATTEMPTS - 1) {
                    log.info(
                            "Ozon Premium LK probe: HTTP 429, retry after {} ms, clientId={}",
                            RATE_LIMIT_RETRY_MS,
                            clientId
                    );
                    sleepQuietly(RATE_LIMIT_RETRY_MS);
                    continue;
                }
                if (isDateValidationError(e) && attempt < MAX_ATTEMPTS - 1) {
                    log.info(
                            "Ozon Premium LK probe: HTTP 400 date validation, retry with instant format, clientId={}",
                            clientId
                    );
                    continue;
                }
                if (isDateValidationError(e)) {
                    log.info(
                            "Ozon Premium LK probe: lookback period rejected — NO_PREMIUM, clientId={}, period={}..{}",
                            clientId,
                            periodFrom,
                            periodTo
                    );
                    return OzonPremiumLkProbeResult.NO_PREMIUM;
                }
                log.info(
                        "Ozon Premium LK probe: lookback HTTP {} — INCONCLUSIVE, clientId={}",
                        status,
                        clientId
                );
                return OzonPremiumLkProbeResult.INCONCLUSIVE;
            } catch (RestClientException e) {
                log.info(
                        "Ozon Premium LK probe: lookback error — INCONCLUSIVE, clientId={}, msg={}",
                        clientId,
                        e.getMessage()
                );
                return OzonPremiumLkProbeResult.INCONCLUSIVE;
            }
        }
        return OzonPremiumLkProbeResult.INCONCLUSIVE;
    }

    private static String formatProbeDateFrom(LocalDate date, int attempt) {
        if (attempt == 0) {
            return date.format(DATE_FORMATTER);
        }
        return date.atStartOfDay(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
    }

    private static String formatProbeDateTo(LocalDate date, int attempt) {
        if (attempt == 0) {
            return date.format(DATE_FORMATTER);
        }
        return date.atTime(23, 59, 59).atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
    }

    private static boolean isDateValidationError(HttpClientErrorException e) {
        if (e.getStatusCode().value() != 400) {
            return false;
        }
        String body = e.getResponseBodyAsString();
        return body != null
                && body.toLowerCase(Locale.ROOT).contains("date_to must be greater than date_from");
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Отказ из‑за лимита периода / подписки (не ошибка валидации дат).
     */
    static boolean isPremiumLkDenied(HttpClientErrorException e) {
        int status = e.getStatusCode().value();
        if (status == 403 || status == 402) {
            return true;
        }
        String body = e.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return false;
        }
        String lower = body.toLowerCase(Locale.ROOT);
        if (lower.contains("date_to must be greater than date_from")) {
            return false;
        }
        if (status == 400 || status == 422) {
            return lower.contains("premium")
                    || lower.contains("subscription")
                    || lower.contains("подписк")
                    || lower.contains("month")
                    || lower.contains("месяц")
                    || lower.contains("period")
                    || lower.contains("период");
        }
        return false;
    }
}
