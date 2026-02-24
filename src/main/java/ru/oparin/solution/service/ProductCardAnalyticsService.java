package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatusCode;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import ru.oparin.solution.dto.wb.CardsListResponse;
import ru.oparin.solution.exception.WbApiUnauthorizedScopeException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.ProductCard;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.CabinetRepository;
import ru.oparin.solution.repository.ProductCardRepository;
import ru.oparin.solution.service.sync.*;

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

    private final CabinetRepository cabinetRepository;
    private final ProductCardRepository productCardRepository;
    private final WbCardsSyncService wbCardsSyncService;
    private final ProductCardService productCardService;
    private final ProductPricesSyncService productPricesSyncService;
    private final ProductStocksService stocksService;
    private final PromotionCampaignSyncService campaignSyncService;
    private final ProductCardAnalyticsLoadService analyticsLoadService;
    private final PromotionCalendarService promotionCalendarService;
    private final FeedbacksSyncService feedbacksSyncService;

    /**
     * Обновляет все карточки и загружает аналитику за указанный период (кабинет по умолчанию продавца).
     */
    @Async("taskExecutor")
    public void updateCardsAndLoadAnalytics(User seller, String apiKey, LocalDate dateFrom, LocalDate dateTo) {
        Cabinet cabinet = cabinetRepository.findDefaultByUserId(seller.getId())
                .orElseThrow(() -> new IllegalStateException("У продавца нет кабинета по умолчанию"));
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
            Cabinet managed = cabinetRepository.findByIdWithUser(cabinet.getId())
                    .orElseThrow(() -> new IllegalStateException("Кабинет не найден: " + cabinet.getId()));
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
        Cabinet managed = cabinetRepository.findByIdWithUser(cabinet.getId())
                .orElseThrow(() -> new IllegalStateException("Кабинет не найден: " + cabinet.getId()));
        doUpdateCabinetAnalytics(managed, dateFrom, dateTo, false);
    }

    private void doUpdateCabinetAnalytics(Cabinet managed, LocalDate dateFrom, LocalDate dateTo, boolean syncPromotion) {
        long cabinetId = managed.getId();
        String apiKey = managed.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("У кабинета (ID: {}) не задан API-ключ, обновление пропущено", cabinetId);
            cabinetRepository.save(managed);
            return;
        }

        // Фиксируем время запуска обновления (ночной шедулер или ручной запуск)
        managed.setLastDataUpdateRequestedAt(LocalDateTime.now());
        cabinetRepository.save(managed);

        User seller = managed.getUser();
        log.info("Начало обновления карточек, кампаний и загрузки аналитики для кабинета (ID: {}, продавец: {}) за период {} - {}",
                cabinetId, seller.getEmail(), dateFrom, dateTo);

        try {
            try {
                CardsListResponse cardsResponse = wbCardsSyncService.fetchAllCards(apiKey);
                productCardService.saveOrUpdateCards(cardsResponse, managed);
            } catch (WbApiUnauthorizedScopeException e) {
                log.warn("Для кабинета {} нет доступа к категории WB API: {}. Проверьте настройки токена в ЛК продавца.", cabinetId, e.getCategory().getDisplayName());
            }

            try {
                productPricesSyncService.updateProductPrices(managed, apiKey);
            } catch (WbApiUnauthorizedScopeException e) {
                log.warn("Для кабинета {} нет доступа к категории WB API: {}. Проверьте настройки токена в ЛК продавца.", cabinetId, e.getCategory().getDisplayName());
            }
            try {
                productPricesSyncService.updateSppFromOrders(managed, apiKey);
            } catch (WbApiUnauthorizedScopeException e) {
                log.warn("Для кабинета {} нет доступа к категории WB API: {}. Проверьте настройки токена в ЛК продавца.", cabinetId, e.getCategory().getDisplayName());
            }

            List<ProductCard> productCards = productCardRepository.findByCabinet_Id(cabinetId);
            List<Long> nmIds = productCards.stream()
                    .map(ProductCard::getNmId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            try {
                if (!nmIds.isEmpty()) {
                    stocksService.updateStocksForCabinet(managed, apiKey, nmIds);
                }
            } catch (WbApiUnauthorizedScopeException e) {
                log.warn("Для кабинета {} нет доступа к категории WB API: {}. Проверьте настройки токена в ЛК продавца.", cabinetId, e.getCategory().getDisplayName());
            }

            try {
                List<Long> campaignIds = campaignSyncService.updateCampaigns(managed, apiKey);
                if (!campaignIds.isEmpty()) {
                    List<Long> nonFinishedIds = campaignSyncService.filterNonFinishedCampaigns(cabinetId, campaignIds);
                    if (!nonFinishedIds.isEmpty()) {
                        campaignSyncService.updateStatistics(seller, apiKey, nonFinishedIds, cabinetId, dateFrom, dateTo);
                    }
                }
            } catch (WbApiUnauthorizedScopeException e) {
                log.warn("Для кабинета {} нет доступа к категории WB API: {}. Проверьте настройки токена в ЛК продавца.", cabinetId, e.getCategory().getDisplayName());
            }

            log.info("Найдено карточек для загрузки аналитики: {}", productCards.size());
            try {
                ProductCardAnalyticsLoadService.ProcessingResult result =
                        analyticsLoadService.loadAnalyticsForAllCards(productCards, apiKey, dateFrom, dateTo);
                log.info("Завершено обновление карточек, кампаний и загрузка аналитики для кабинета (ID: {}): успешно {}, ошибок {}",
                        cabinetId, result.successCount(), result.errorCount());
            } catch (WbApiUnauthorizedScopeException e) {
                log.warn("Для кабинета {} нет доступа к категории WB API: {}. Проверьте настройки токена в ЛК продавца.", cabinetId, e.getCategory().getDisplayName());
            }

            managed.setLastDataUpdateAt(LocalDateTime.now());
            cabinetRepository.save(managed);

            if (syncPromotion) {
                try {
                    promotionCalendarService.syncPromotionsForCabinet(managed);
                } catch (WbApiUnauthorizedScopeException e) {
                    log.warn("Для кабинета {} нет доступа к категории WB API: {}. Проверьте настройки токена в ЛК продавца.", cabinetId, e.getCategory().getDisplayName());
                } catch (Exception e) {
                    log.warn("Синхронизация акций календаря для кабинета {} завершилась с ошибкой: {}", cabinetId, e.getMessage());
                }
            }

            try {
                feedbacksSyncService.syncFeedbacksForCabinet(managed, apiKey);
            } catch (WbApiUnauthorizedScopeException e) {
                log.warn("Для кабинета {} нет доступа к категории WB API: {}. Проверьте настройки токена в ЛК продавца.", cabinetId, e.getCategory().getDisplayName());
            } catch (Exception e) {
                log.warn("Синхронизация отзывов для кабинета {} завершилась с ошибкой: {}", cabinetId, e.getMessage());
            }

        } catch (HttpClientErrorException e) {
            HttpStatusCode code = e.getStatusCode();
            if (code.value() == 401) {
                log.warn("API-ключ кабинета (ID: {}) отклонён WB (401). Помечаем ключ как недействительный.", cabinetId);
                managed.setIsValid(false);
                managed.setValidationError("Ключ отклонён WB (401). Обновите ключ в настройках кабинета.");
                cabinetRepository.save(managed);
            }
            throw e;
        }
    }
}
