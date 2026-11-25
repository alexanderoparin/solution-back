package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.wb.WbPromotionBalanceResponse;
import ru.oparin.solution.dto.wb.WbPromotionConfigResponse;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.CabinetTokenType;
import ru.oparin.solution.model.MarketplaceType;
import ru.oparin.solution.service.wb.WbPromotionApiClient;

/**
 * Синхронизация валюты профиля продавца WB на кабинет.
 * Источники: GET /api/advert/v1/config (personal), иначе GET /adv/v1/balance.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WbCabinetCurrencyService {

    public static final String DEFAULT_CURRENCY = "RUB";

    private final CabinetService cabinetService;
    private final WbPromotionApiClient promotionApiClient;

    /**
     * Обновляет {@link Cabinet#getCurrency()} из WB API, если удалось получить код.
     * Ошибки API не пробрасываются — валюта остаётся прежней.
     *
     * @param cabinet кабинет WB с API-ключом
     */
    @Transactional
    public void syncCurrencyBestEffort(Cabinet cabinet) {
        if (cabinet == null || cabinet.getId() == null) {
            return;
        }
        if (cabinet.getMarketplaceType() != null && cabinet.getMarketplaceType() != MarketplaceType.WB) {
            return;
        }
        if (cabinet.getApiKey() == null || cabinet.getApiKey().isBlank()) {
            return;
        }

        String resolved = resolveCurrencyCode(cabinet);
        if (resolved == null) {
            return;
        }
        applyCurrency(cabinet.getId(), resolved);
    }

    /**
     * Пишет валюту на кабинет, если она валидна и отличается от текущей.
     *
     * @param cabinetId ID кабинета
     * @param currencyCode ISO 4217 (например BYN)
     */
    @Transactional
    public void applyCurrency(Long cabinetId, String currencyCode) {
        String normalized = normalizeCurrency(currencyCode);
        if (normalized == null || cabinetId == null) {
            return;
        }
        Cabinet managed = cabinetService.findById(cabinetId).orElse(null);
        if (managed == null) {
            return;
        }
        if (normalized.equalsIgnoreCase(managed.getCurrency())) {
            return;
        }
        String previous = managed.getCurrency();
        managed.setCurrency(normalized);
        cabinetService.save(managed);
        log.info("Валюта кабинета {} обновлена: {} → {}", cabinetId, previous, normalized);
    }

    private String resolveCurrencyCode(Cabinet cabinet) {
        CabinetTokenType tokenType = CabinetTokenType.effective(cabinet.getTokenType());
        if (tokenType.supportsPromotionConfig()) {
            try {
                WbPromotionConfigResponse config = promotionApiClient.getPromotionConfig(cabinet.getApiKey());
                String fromConfig = normalizeCurrency(config != null ? config.getCurrency() : null);
                if (fromConfig != null) {
                    return fromConfig;
                }
            } catch (Exception e) {
                log.debug("Не удалось получить валюту из config для кабинета {}: {}",
                        cabinet.getId(), e.getMessage());
            }
        }
        try {
            WbPromotionBalanceResponse balance = promotionApiClient.getBalance(cabinet.getApiKey());
            return normalizeCurrency(balance != null ? balance.getCurrency() : null);
        } catch (Exception e) {
            log.debug("Не удалось получить валюту из balance для кабинета {}: {}",
                    cabinet.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * Нормализует код валюты ISO 4217.
     *
     * @param raw сырое значение
     * @return верхний регистр из 3 букв или null
     */
    public static String normalizeCurrency(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toUpperCase();
        if (value.length() != 3 || !value.chars().allMatch(Character::isLetter)) {
            return null;
        }
        return value;
    }

    /**
     * Эффективный код валюты кабинета; пустое → {@link #DEFAULT_CURRENCY}.
     *
     * @param currency код из кабинета
     * @return ISO 4217
     */
    public static String effectiveCurrency(String currency) {
        String normalized = normalizeCurrency(currency);
        return normalized != null ? normalized : DEFAULT_CURRENCY;
    }

    /**
     * Символ валюты для пользовательских сообщений.
     *
     * @param currency ISO 4217
     * @return символ или код
     */
    public static String currencySymbol(String currency) {
        return switch (effectiveCurrency(currency)) {
            case "RUB" -> "₽";
            case "CNY" -> "¥";
            case "BYN" -> "Br";
            case "KGS" -> "сом";
            case "UZS" -> "сум";
            case "KZT" -> "₸";
            case "USD" -> "$";
            case "EUR" -> "€";
            default -> effectiveCurrency(currency);
        };
    }

    /**
     * Минимальная сумма пополнения бюджета РК WB в валюте кабинета.
     *
     * @param currency ISO 4217
     * @return минимум (CNY — 10, иначе 1000)
     */
    public static int minTopUpAmount(String currency) {
        return "CNY".equals(effectiveCurrency(currency)) ? 10 : 1000;
    }
}
