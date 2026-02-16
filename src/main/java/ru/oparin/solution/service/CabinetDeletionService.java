package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.repository.*;

import java.util.List;

/**
 * Шаги удаления данных кабинета, каждый в отдельной транзакции (REQUIRES_NEW).
 * Удаление выполняется пачками по 20 строк, чтобы не нагружать БД одним большим DELETE.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CabinetDeletionService {

    private static final int BATCH_SIZE = 20;

    private final PromotionCampaignStatisticsRepository promotionCampaignStatisticsRepository;
    private final CampaignArticleRepository campaignArticleRepository;
    private final PromotionCampaignRepository promotionCampaignRepository;
    private final ProductPriceHistoryRepository productPriceHistoryRepository;
    private final ProductStockRepository productStockRepository;
    private final ProductBarcodeRepository productBarcodeRepository;
    private final ProductCardAnalyticsRepository productCardAnalyticsRepository;
    private final ProductCardRepository productCardRepository;
    private final ArticleNoteRepository articleNoteRepository;
    private final CabinetRepository cabinetRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteStepStatisticsAndArticles(Long cabinetId) {
        deleteByIdBatches("Статистика кампаний",
                () -> promotionCampaignStatisticsRepository.findIdByCampaign_Cabinet_Id(cabinetId, PageRequest.of(0, BATCH_SIZE)),
                promotionCampaignStatisticsRepository::deleteAllById);
        deleteByIdBatches("Связи кампания–артикул",
                () -> campaignArticleRepository.findIdByCampaign_Cabinet_Id(cabinetId, PageRequest.of(0, BATCH_SIZE)),
                campaignArticleRepository::deleteAllById);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteStepCampaigns(Long cabinetId) {
        deleteByIdBatches("Рекламные кампании",
                () -> promotionCampaignRepository.findAdvertIdByCabinet_Id(cabinetId, PageRequest.of(0, BATCH_SIZE)),
                promotionCampaignRepository::deleteAllById);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteStepPriceHistory(Long cabinetId) {
        deleteByIdBatches("История цен",
                () -> productPriceHistoryRepository.findIdByCabinet_Id(cabinetId, PageRequest.of(0, BATCH_SIZE)),
                productPriceHistoryRepository::deleteAllById);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteStepStocks(Long cabinetId) {
        deleteByIdBatches("Остатки на складах",
                () -> productStockRepository.findIdByCabinet_Id(cabinetId, PageRequest.of(0, BATCH_SIZE)),
                productStockRepository::deleteAllById);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteStepBarcodes(Long cabinetId) {
        deleteByIdBatches("Штрихкоды товаров",
                () -> productBarcodeRepository.findBarcodeByCabinet_Id(cabinetId, PageRequest.of(0, BATCH_SIZE)),
                productBarcodeRepository::deleteAllById);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteStepCardAnalytics(Long cabinetId) {
        deleteByIdBatches("Аналитика карточек",
                () -> productCardAnalyticsRepository.findIdByCabinet_Id(cabinetId, PageRequest.of(0, BATCH_SIZE)),
                productCardAnalyticsRepository::deleteAllById);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteStepProductCards(Long cabinetId) {
        deleteByIdBatches("Карточки товаров",
                () -> productCardRepository.findNmIdByCabinet_Id(cabinetId, PageRequest.of(0, BATCH_SIZE)),
                productCardRepository::deleteAllById);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteStepArticleNotes(Long cabinetId) {
        deleteByIdBatches("Заметки по артикулам",
                () -> articleNoteRepository.findIdByCabinetId(cabinetId, PageRequest.of(0, BATCH_SIZE)),
                articleNoteRepository::deleteAllById);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteStepCabinetRecord(Long cabinetId) {
        log.info("[Удаление кабинета]   Запись кабинета");
        Cabinet cabinet = cabinetRepository.findById(cabinetId).orElse(null);
        if (cabinet != null) {
            cabinetRepository.delete(cabinet);
        }
    }

    /**
     * Удаление пачками по ключам: выборка только ID, затем deleteAllById.
     * После каждой пачки пишет в лог для контроля.
     */
    private <I> void deleteByIdBatches(String label, java.util.function.Supplier<List<I>> idBatchSupplier, java.util.function.Consumer<List<I>> deleteByIds) {
        while (true) {
            List<I> ids = idBatchSupplier.get();
            if (ids.isEmpty()) {
                break;
            }
            deleteByIds.accept(ids);
            log.info("[Удаление кабинета]   {}: ключи {}", label, ids);
        }
    }
}
