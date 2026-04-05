package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.model.CampaignNoteFile;
import ru.oparin.solution.repository.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Шаги удаления данных кабинета, каждый в отдельной транзакции (REQUIRES_NEW).
 * Удаление выполняется пачками по 20 строк, чтобы не нагружать БД одним большим DELETE.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CabinetDeletionService {

    private static final int BATCH_SIZE = 50;

    private final PromotionCampaignStatisticsRepository promotionCampaignStatisticsRepository;
    private final CampaignArticleRepository campaignArticleRepository;
    private final PromotionCampaignRepository promotionCampaignRepository;
    private final ProductPriceHistoryRepository productPriceHistoryRepository;
    private final ProductStockRepository productStockRepository;
    private final ProductBarcodeRepository productBarcodeRepository;
    private final ProductCardAnalyticsRepository productCardAnalyticsRepository;
    private final ProductCardRepository productCardRepository;
    private final ArticleNoteRepository articleNoteRepository;
    private final CampaignNoteRepository campaignNoteRepository;
    private final CampaignNoteFileRepository campaignNoteFileRepository;

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

    /**
     * Сначала удаляем файлы заметок РК с диска и строки в campaign_note_files, иначе при удалении заметок останутся сироты на диске.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteStepCampaignNoteFiles(Long cabinetId) {
        while (true) {
            Page<CampaignNoteFile> page = campaignNoteFileRepository.findByNote_CabinetId(cabinetId, PageRequest.of(0, BATCH_SIZE));
            if (page.isEmpty()) {
                break;
            }
            for (CampaignNoteFile f : page.getContent()) {
                deleteCampaignNoteFileFromDisk(f.getFilePath());
            }
            campaignNoteFileRepository.deleteAll(page.getContent());
            log.info("[Удаление кабинета]   Файлы заметок РК: удалено записей {}", page.getContent().size());
        }
    }

    private void deleteCampaignNoteFileFromDisk(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (Files.exists(path)) {
                Files.delete(path);
            }
        } catch (IOException e) {
            log.warn("[Удаление кабинета] Не удалось удалить файл заметки РК с диска: {}", filePath, e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteStepCampaignNotes(Long cabinetId) {
        deleteByIdBatches("Заметки по РК",
                () -> campaignNoteRepository.findIdByCabinetId(cabinetId, PageRequest.of(0, BATCH_SIZE)),
                campaignNoteRepository::deleteAllById);
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
