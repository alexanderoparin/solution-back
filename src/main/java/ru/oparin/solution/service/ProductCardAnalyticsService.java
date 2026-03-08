package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import ru.oparin.solution.dto.wb.CardsListResponse;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.exception.WbApiUnauthorizedScopeException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.ProductCard;
import ru.oparin.solution.model.User;
import ru.oparin.solution.service.sync.*;
import ru.oparin.solution.service.wb.WbApiCategory;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Оркестратор полного обновления данных кабинета: карточки, цены, остатки, кампании, аналитика.
 * Делегирует загрузку специализированным сервисам синхронизации.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductCardAnalyticsService {

    private static final int STOCKS_UPDATE_MIN_INTERVAL_HOURS = 1;
    private static final String MSG_SCOPE_ACCESS_DENIED =
            "Для кабинета {} нет доступа к категории WB API: {}. Проверьте настройки токена в ЛК продавца.";

    private final WbCardsSyncService wbCardsSyncService;
    private final ProductCardService productCardService;
    private final ProductPricesSyncService productPricesSyncService;
    private final ProductStocksService stocksService;
    private final PromotionCampaignSyncService campaignSyncService;
    private final ProductCardAnalyticsLoadService analyticsLoadService;
    private final PromotionCalendarService promotionCalendarService;
    private final FeedbacksSyncService feedbacksSyncService;
    private final CabinetService cabinetService;
    private final CabinetScopeStatusService cabinetScopeStatusService;

    // --- Публичный API (асинхронные вызовы, транзакции, валидация) ---

    /**
     * Обновляет все карточки и загружает аналитику за указанный период (кабинет по умолчанию продавца).
     * API-ключ берётся из кабинета.
     */
    @Async("taskExecutor")
    public void updateCardsAndLoadAnalytics(User seller, LocalDate dateFrom, LocalDate dateTo) {
        Cabinet cabinet = cabinetService.findDefaultByUserIdOrThrow(seller.getId());
        updateCardsAndLoadAnalytics(cabinet, dateFrom, dateTo);
    }

    /**
     * Обновляет карточки и загружает аналитику для указанного кабинета (ключ берётся из кабинета).
     * Выполняется асинхронно. Включает синхронизацию акций календаря (для ручного/одиночного запуска).
     */
    @Async("taskExecutor")
    public void updateCardsAndLoadAnalytics(Cabinet cabinet, LocalDate dateFrom, LocalDate dateTo) {
        MDC.put("cabinetTag", "[cabinet:" + cabinet.getId() + "]");
        try {
            Cabinet managed = findManagedCabinet(cabinet.getId());
            doUpdateCabinetAnalytics(managed, dateFrom, dateTo, true);
        } finally {
            MDC.remove("cabinetTag");
        }
    }

    /**
     * Обновление аналитики одного кабинета в одной транзакции.
     * Вызывается только из оркестратора полного обновления; синхронизацию акций оркестратор вызывает отдельно.
     */
    @Transactional
    public void updateCabinetAnalyticsInTransaction(Cabinet cabinet, LocalDate dateFrom, LocalDate dateTo) {
        Cabinet managed = findManagedCabinet(cabinet.getId());
        doUpdateCabinetAnalytics(managed, dateFrom, dateTo, false);
    }

    /**
     * Проверка возможности запуска обновления остатков: не чаще одного раза в час по полю кабинета lastStocksUpdateRequestedAt.
     *
     * @param cabinetId ID кабинета
     * @throws UserException если с последнего запуска прошло меньше часа
     */
    public void validateStocksUpdateInterval(Long cabinetId) {
        Cabinet cabinet = cabinetService.findByIdWithUserOrThrow(cabinetId);

        LocalDateTime lastRequested = cabinet.getLastStocksUpdateRequestedAt();
        if (lastRequested == null) return;

        long hoursSince = Duration.between(lastRequested, LocalDateTime.now()).toHours();
        if (hoursSince < STOCKS_UPDATE_MIN_INTERVAL_HOURS) {
            long remaining = STOCKS_UPDATE_MIN_INTERVAL_HOURS - hoursSince;
            throw new UserException(
                    "Обновление остатков доступно не чаще раза в час. Следующее обновление через " + remaining + " " + formatHoursWord(remaining) + ".",
                    HttpStatus.TOO_MANY_REQUESTS
            );
        }
    }

    /**
     * Сохраняет дату-время запуска обновления остатков по кабинету (вызывать после validate, перед runStocksUpdateOnly).
     */
    @Transactional
    public void recordStocksUpdateTriggered(Long cabinetId) {
        Cabinet cabinet = cabinetService.findByIdWithUserOrThrow(cabinetId);
        cabinet.setLastStocksUpdateRequestedAt(LocalDateTime.now());
        cabinetService.save(cabinet);
    }

    /**
     * Асинхронное обновление только остатков по кабинету.
     */
    @Async("taskExecutor")
    public void runStocksUpdateOnly(Long cabinetId) {
        MDC.put("cabinetTag", "[cabinet:" + cabinetId + "]");
        try {
            Cabinet managed = cabinetService.findByIdWithUserOrThrow(cabinetId);
            String apiKey = managed.getApiKey();
            if (apiKey == null || apiKey.isBlank()) {
                log.warn("У кабинета (ID: {}) не задан API-ключ, обновление остатков пропущено", cabinetId);
                return;
            }
            List<Long> nmIds = collectNmIdsFromCabinet(cabinetId);
            if (nmIds.isEmpty()) {
                log.info("У кабинета (ID: {}) нет карточек, обновление остатков пропущено", cabinetId);
                return;
            }
            stocksService.updateStocksForCabinet(managed, apiKey, nmIds);
            cabinetScopeStatusService.recordSuccess(cabinetId, WbApiCategory.ANALYTICS);
        } catch (WbApiUnauthorizedScopeException e) {
            cabinetScopeStatusService.recordFailure(cabinetId, e.getCategory(), e.getMessage());
            logScopeAccessDenied(cabinetId, e);
        } finally {
            MDC.remove("cabinetTag");
        }
    }

    // --- Основной поток: один уровень абстракции ---

    private void doUpdateCabinetAnalytics(Cabinet managed, LocalDate dateFrom, LocalDate dateTo, boolean syncPromotion) {
        long cabinetId = managed.getId();
        if (skipIfNoApiKey(managed, cabinetId)) return;

        markUpdateRequested(managed);
        User seller = managed.getUser();
        log.info("Начало обновления карточек, кампаний и загрузки аналитики для кабинета (ID: {}, продавец: {}) за период {} - {}",
                cabinetId, seller.getEmail(), dateFrom, dateTo);

        try {
            runWithScopeGuard(cabinetId, WbApiCategory.CONTENT, () -> syncCards(managed, managed.getApiKey()));
            runWithScopeGuard(cabinetId, () -> syncPricesAndSpp(managed, managed.getApiKey()));

            List<ProductCard> productCards = productCardService.findByCabinetId(cabinetId);
            List<Long> nmIds = collectNmIds(productCards);

            runWithScopeGuard(cabinetId, WbApiCategory.PROMOTION, () -> syncCampaignsAndStatistics(managed, managed.getApiKey(), cabinetId, seller, dateFrom, dateTo));

            log.info("Найдено карточек для загрузки аналитики: {}", productCards.size());
            runWithScopeGuard(cabinetId, WbApiCategory.ANALYTICS, () -> syncAnalytics(managed, cabinetId, productCards, dateFrom, dateTo));

            markUpdateCompleted(managed);

            if (syncPromotion) {
                syncPromotionCalendarWithGuard(managed, cabinetId);
            }
            syncFeedbacksWithGuard(managed, cabinetId);
            runWithScopeGuard(cabinetId, WbApiCategory.ANALYTICS, () -> syncStocks(managed, nmIds));

        } catch (HttpClientErrorException e) {
            handle401AndInvalidateKey(managed, e);
            throw e;
        }
    }

    // --- Шаги обновления (одна ответственность на метод) ---

    private Cabinet findManagedCabinet(Long cabinetId) {
        return cabinetService.findByIdWithUserOrThrow(cabinetId);
    }

    private boolean skipIfNoApiKey(Cabinet managed, long cabinetId) {
        String apiKey = managed.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("У кабинета (ID: {}) не задан API-ключ, обновление пропущено", cabinetId);
            cabinetService.save(managed);
            return true;
        }
        return false;
    }

    private void markUpdateRequested(Cabinet managed) {
        managed.setLastDataUpdateRequestedAt(LocalDateTime.now());
        cabinetService.save(managed);
    }

    private void markUpdateCompleted(Cabinet managed) {
        managed.setLastDataUpdateAt(LocalDateTime.now());
        cabinetService.save(managed);
    }

    private void syncCards(Cabinet managed, String apiKey) {
        CardsListResponse cardsResponse = wbCardsSyncService.fetchAllCards(apiKey);
        productCardService.saveOrUpdateCards(cardsResponse, managed);
    }

    private void syncPricesAndSpp(Cabinet managed, String apiKey) {
        productPricesSyncService.updateProductPrices(managed, apiKey);
        productPricesSyncService.updateSppFromOrders(managed, apiKey);
    }

    private void syncCampaignsAndStatistics(Cabinet managed, String apiKey, long cabinetId, User seller,
                                            LocalDate dateFrom, LocalDate dateTo) {
        List<Long> campaignIds = campaignSyncService.updateCampaigns(managed, apiKey);
        if (campaignIds.isEmpty()) return;

        List<Long> nonFinishedIds = campaignSyncService.filterNonFinishedCampaigns(cabinetId, campaignIds);
        if (nonFinishedIds.isEmpty()) return;

        campaignSyncService.updateStatistics(seller, apiKey, nonFinishedIds, cabinetId, dateFrom, dateTo);
    }

    private void syncAnalytics(Cabinet managed, long cabinetId, List<ProductCard> productCards,
                               LocalDate dateFrom, LocalDate dateTo) {
        String apiKey = managed.getApiKey();
        ProductCardAnalyticsLoadService.ProcessingResult result =
                analyticsLoadService.loadAnalyticsForAllCards(productCards, apiKey, dateFrom, dateTo);
        log.info("Завершено обновление карточек, кампаний и загрузка аналитики для кабинета (ID: {}): успешно {}, ошибок {}",
                cabinetId, result.successCount(), result.errorCount());
    }

    private void syncPromotionCalendarWithGuard(Cabinet managed, long cabinetId) {
        try {
            promotionCalendarService.syncPromotionsForCabinet(managed);
        } catch (WbApiUnauthorizedScopeException e) {
            cabinetScopeStatusService.recordFailure(cabinetId, e.getCategory(), e.getMessage());
            logScopeAccessDenied(cabinetId, e);
        } catch (Exception e) {
            log.warn("Синхронизация акций календаря для кабинета {} завершилась с ошибкой: {}", cabinetId, e.getMessage());
        }
    }

    private void syncFeedbacksWithGuard(Cabinet managed, long cabinetId) {
        try {
            feedbacksSyncService.syncFeedbacksForCabinetInNewTransaction(managed, managed.getApiKey());
        } catch (WbApiUnauthorizedScopeException e) {
            cabinetScopeStatusService.recordFailure(cabinetId, e.getCategory(), e.getMessage());
            logScopeAccessDenied(cabinetId, e);
        } catch (Exception e) {
            log.warn("Синхронизация отзывов для кабинета {} завершилась с ошибкой: {}", cabinetId, e.getMessage());
        }
    }

    private void syncStocks(Cabinet managed, List<Long> nmIds) {
        if (nmIds.isEmpty()) return;
        stocksService.updateStocksForCabinet(managed, managed.getApiKey(), nmIds);
    }

    // --- Обработка ошибок ---

    /**
     * Выполняет блок с одной категорией WB API: при успехе пишет success, при 401 — failure.
     */
    private void runWithScopeGuard(long cabinetId, WbApiCategory category, Runnable action) {
        try {
            action.run();
            cabinetScopeStatusService.recordSuccess(cabinetId, category);
        } catch (WbApiUnauthorizedScopeException e) {
            cabinetScopeStatusService.recordFailure(cabinetId, e.getCategory(), e.getMessage());
            logScopeAccessDenied(cabinetId, e);
        }
    }

    /**
     * Выполняет блок, который сам записывает успех по категориям (например syncPricesAndSpp — две категории).
     * Только ловит 401 и пишет failure, success не вызывается здесь.
     */
    private void runWithScopeGuard(long cabinetId, Runnable action) {
        try {
            action.run();
        } catch (WbApiUnauthorizedScopeException e) {
            cabinetScopeStatusService.recordFailure(cabinetId, e.getCategory(), e.getMessage());
            logScopeAccessDenied(cabinetId, e);
        }
    }

    private void logScopeAccessDenied(long cabinetId, WbApiUnauthorizedScopeException e) {
        log.warn(MSG_SCOPE_ACCESS_DENIED, cabinetId, e.getCategory().getDisplayName());
    }

    private void handle401AndInvalidateKey(Cabinet managed, HttpClientErrorException e) {
        HttpStatusCode code = e.getStatusCode();
        if (code.value() == 401) {
            log.warn("API-ключ кабинета (ID: {}) отклонён WB (401). Помечаем ключ как недействительный.", managed.getId());
            managed.setIsValid(false);
            managed.setValidationError("Ключ отклонён WB (401). Обновите ключ в настройках кабинета.");
            cabinetService.save(managed);
        }
    }

    // --- Вспомогательные методы ---

    private List<Long> collectNmIds(List<ProductCard> productCards) {
        return productCards.stream()
                    .map(ProductCard::getNmId)
                .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
    }

    private List<Long> collectNmIdsFromCabinet(long cabinetId) {
        List<ProductCard> cards = productCardService.findByCabinetId(cabinetId);
        return collectNmIds(cards);
    }

    private static String formatHoursWord(long hours) {
        if (hours == 1) return "час";
        if (hours >= 2 && hours <= 4) return "часа";
        return "часов";
    }
}
