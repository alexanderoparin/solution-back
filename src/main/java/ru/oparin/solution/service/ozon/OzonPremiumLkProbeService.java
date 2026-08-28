package ru.oparin.solution.service.ozon;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
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

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final List<String> PROBE_METRICS = List.of("ordered_units", "revenue");

    /** Период старше окна 3 месяцев для бесплатного тарифа. */
    private static final int LOOKBACK_DAYS_FROM = 150;
    private static final int LOOKBACK_DAYS_TO = 120;

    private final OzonProductsApiClient productsApiClient;

    /**
     * Probe: запрос analytics за период 120–150 дней назад (вне лимита 3 месяцев без Premium).
     */
    public OzonPremiumLkProbeResult probe(String clientId, String apiKey) {
        if (clientId == null || clientId.isBlank() || apiKey == null || apiKey.isBlank()) {
            return OzonPremiumLkProbeResult.INCONCLUSIVE;
        }
        LocalDate today = LocalDate.now();
        LocalDate lookbackFrom = today.minusDays(LOOKBACK_DAYS_FROM);
        LocalDate lookbackTo = today.minusDays(LOOKBACK_DAYS_TO);
        if (!lookbackTo.isAfter(lookbackFrom)) {
            return OzonPremiumLkProbeResult.INCONCLUSIVE;
        }

        try {
            productsApiClient.getAnalyticsData(
                    clientId,
                    apiKey,
                    lookbackFrom.format(DATE_FORMATTER),
                    lookbackTo.format(DATE_FORMATTER),
                    1,
                    0,
                    PROBE_METRICS
            );
            return OzonPremiumLkProbeResult.HAS_PREMIUM;
        } catch (HttpClientErrorException e) {
            if (isPremiumLkDenied(e)) {
                return OzonPremiumLkProbeResult.NO_PREMIUM;
            }
            log.info(
                    "Ozon Premium LK probe: lookback HTTP {} — INCONCLUSIVE, clientId={}",
                    e.getStatusCode().value(),
                    clientId
            );
            return OzonPremiumLkProbeResult.INCONCLUSIVE;
        } catch (RestClientException e) {
            log.info("Ozon Premium LK probe: lookback error — INCONCLUSIVE, clientId={}, msg={}",
                    clientId, e.getMessage());
            return OzonPremiumLkProbeResult.INCONCLUSIVE;
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
