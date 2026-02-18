package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import ru.oparin.solution.dto.wb.CardsListResponse;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.ProductCard;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.CabinetRepository;
import ru.oparin.solution.repository.ProductCardRepository;
import ru.oparin.solution.service.sync.ProductCardAnalyticsLoadService;
import ru.oparin.solution.service.sync.ProductPricesSyncService;
import ru.oparin.solution.service.sync.PromotionCampaignSyncService;
import ru.oparin.solution.service.sync.WbCardsSyncService;

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
     * Выполняется асинхронно.
     */
    @Async("taskExecutor")
    public void updateCardsAndLoadAnalytics(Cabinet cabinet, LocalDate dateFrom, LocalDate dateTo) {
        long cabinetId = cabinet.getId();

        Cabinet managed = cabinetRepository.findByIdWithUser(cabinetId)
                .orElseThrow(() -> new IllegalStateException("Кабинет не найден: " + cabinetId));
        String apiKey = managed.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("У кабинета (ID: {}) не задан API-ключ, обновление пропущено", cabinetId);
            managed.setLastDataUpdateRequestedAt(null);
            cabinetRepository.save(managed);
            return;
        }

        managed.setLastDataUpdateRequestedAt(null);
        cabinetRepository.save(managed);

        User seller = managed.getUser();
        log.info("Начало обновления карточек, кампаний и загрузки аналитики для кабинета (ID: {}, продавец: {}) за период {} - {}",
                cabinetId, seller.getEmail(), dateFrom, dateTo);

        try {
            CardsListResponse cardsResponse = wbCardsSyncService.fetchAllCards(apiKey);
            productCardService.saveOrUpdateCards(cardsResponse, managed);

            productPricesSyncService.updateProductPrices(managed, apiKey);
            productPricesSyncService.updateSppFromOrders(managed, apiKey);

            List<ProductCard> productCards = productCardRepository.findByCabinet_Id(cabinetId);
            List<Long> nmIds = productCards.stream()
                    .map(ProductCard::getNmId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            if (!nmIds.isEmpty()) {
                stocksService.updateStocksForCabinet(managed, apiKey, nmIds);
            }

            List<Long> campaignIds = campaignSyncService.updateCampaigns(managed, apiKey);
            if (!campaignIds.isEmpty()) {
                List<Long> nonFinishedIds = campaignSyncService.filterNonFinishedCampaigns(cabinetId, campaignIds);
                if (!nonFinishedIds.isEmpty()) {
                    campaignSyncService.updateStatistics(seller, apiKey, nonFinishedIds, cabinetId, dateFrom, dateTo);
                }
            }

            log.info("Найдено карточек для загрузки аналитики: {}", productCards.size());
            ProductCardAnalyticsLoadService.ProcessingResult result =
                    analyticsLoadService.loadAnalyticsForAllCards(productCards, apiKey, dateFrom, dateTo);

            log.info("Завершено обновление карточек, кампаний и загрузка аналитики для кабинета (ID: {}): успешно {}, ошибок {}",
                    cabinetId, result.successCount(), result.errorCount());

            managed.setLastDataUpdateAt(LocalDateTime.now());
            cabinetRepository.save(managed);

            try {
                promotionCalendarService.syncPromotionsForCabinet(managed);
            } catch (Exception e) {
                log.warn("Синхронизация акций календаря для кабинета {} завершилась с ошибкой: {}", cabinetId, e.getMessage());
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
