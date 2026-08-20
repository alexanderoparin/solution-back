package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.wb.WbPromotionAdvertsResponse;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.WbCampaignArticleRepository;
import ru.oparin.solution.repository.WbProductCardRepository;
import ru.oparin.solution.repository.WbPromotionCampaignRepository;
import ru.oparin.solution.repository.WbPromotionCampaignStatisticsRepository;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/**
 * Сервис для работы с рекламными кампаниями.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WbPromotionCampaignService {

    private final WbPromotionCampaignRepository campaignRepository;
    private final WbCampaignArticleRepository campaignArticleRepository;
    private final WbProductCardRepository productCardRepository;
    private final WbPromotionCampaignStatisticsRepository campaignStatisticsRepository;
    private final CabinetService cabinetService;

    /**
     * Сохраняет или обновляет кампании из ответа WB API (кабинет по умолчанию для продавца).
     */
    @Transactional
    public void saveOrUpdateCampaigns(WbPromotionAdvertsResponse response, User seller) {
        if (isEmptyResponse(response)) {
            log.info("Ответ со списком кампаний пуст, сохранение/обновление не требуется.");
            return;
        }
        Cabinet cabinet = cabinetService.findDefaultByUserIdOrThrow(seller.getId());
        saveOrUpdateCampaigns(response, cabinet);
    }

    /**
     * Сохраняет или обновляет кампании из ответа WB API для указанного кабинета.
     */
    @Transactional
    public void saveOrUpdateCampaigns(WbPromotionAdvertsResponse response, Cabinet cabinet) {
        if (isEmptyResponse(response)) {
            log.info("Ответ со списком кампаний пуст, сохранение/обновление не требуется.");
            return;
        }
        ProcessingResult result = processCampaigns(response.getAdverts(), cabinet);
        log.info("Обработано кампаний: создано {}, обновлено {}, пропущено {}",
                result.savedCount(), result.updatedCount(), result.skippedCount());
    }

    private boolean isEmptyResponse(WbPromotionAdvertsResponse response) {
        return response == null
                || response.getAdverts() == null
                || response.getAdverts().isEmpty();
    }

    private ProcessingResult processCampaigns(List<WbPromotionAdvertsResponse.Campaign> campaignDtos, Cabinet cabinet) {
        int savedCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;

        for (WbPromotionAdvertsResponse.Campaign campaignDto : campaignDtos) {
            if (!isValidCampaign(campaignDto)) {
                skippedCount++;
                continue;
            }

            try {
                Optional<SaveResult> result = processCampaign(campaignDto, cabinet);
                if (result.isPresent()) {
                    if (result.get().isNew()) {
                        savedCount++;
                    } else {
                        updatedCount++;
                    }
                } else {
                    skippedCount++;
                }
            } catch (Exception e) {
                log.error("Ошибка при обработке кампании advertId {}: {}",
                        campaignDto.getAdvertId(), e.getMessage(), e);
                skippedCount++;
            }
        }

        return new ProcessingResult(savedCount, updatedCount, skippedCount);
    }

    private boolean isValidCampaign(WbPromotionAdvertsResponse.Campaign campaignDto) {
        if (campaignDto == null || campaignDto.getAdvertId() == null) {
            log.warn("Получена некорректная DTO кампании (null или advertId null), пропускаем.");
            return false;
        }
        return true;
    }

    private Optional<SaveResult> processCampaign(WbPromotionAdvertsResponse.Campaign campaignDto, Cabinet cabinet) {
        WbPromotionCampaign campaign = mapToWbPromotionCampaign(campaignDto, cabinet);
        if (campaign == null) {
            return Optional.empty();
        }

        Optional<WbPromotionCampaign> existingCampaign = campaignRepository.findByAdvertIdAndCabinet_Id(
                campaign.getAdvertId(),
                cabinet.getId()
        );

        if (existingCampaign.isPresent()) {
            WbPromotionCampaign existing = existingCampaign.get();
            updateCampaign(existing, campaign);
            campaignRepository.save(existing);
            // Обновляем связи с артикулами только для активных кампаний или на паузе
            if (shouldUpdateWbCampaignArticles(campaign.getStatus())) {
                updateWbCampaignArticles(campaignDto, existing.getAdvertId(), cabinet.getId());
            } else {
                // Для завершенных кампаний удаляем связи
                campaignArticleRepository.deleteByCampaignId(existing.getAdvertId());
            }
            return Optional.of(new SaveResult(false));
        } else {
            campaignRepository.save(campaign);
            // Сохраняем связи с артикулами только для активных кампаний или на паузе
            if (shouldUpdateWbCampaignArticles(campaign.getStatus())) {
                saveWbCampaignArticles(campaignDto, campaign.getAdvertId(), cabinet.getId());
            }
            return Optional.of(new SaveResult(true));
        }
    }
    
    /**
     * Сохраняет связи кампании с артикулами (по кабинету).
     */
    private void saveWbCampaignArticles(WbPromotionAdvertsResponse.Campaign campaignDto, Long campaignId, Long cabinetId) {
        if (campaignDto.getNmIds() == null || campaignDto.getNmIds().isEmpty()) {
            log.warn("У кампании advertId {} в ответе WB нет артикулов (nmIds пустой или null), восстанавливаем связи из promotion_campaign_statistics", campaignId);
            syncWbCampaignArticlesFromStatistics(campaignId, cabinetId);
            return;
        }

        campaignArticleRepository.deleteByCampaignId(campaignId);

        int savedCount = 0;
        for (Long nmId : campaignDto.getNmIds()) {
            try {
                WbProductCard productCard = productCardRepository.findByNmIdAndCabinet_Id(nmId, cabinetId)
                        .orElse(null);

                if (productCard == null) {
                    log.warn("Артикул {} не найден в кабинете {}, пропускаем связь с кампанией {}",
                            nmId, cabinetId, campaignId);
                    continue;
                }

                WbCampaignArticle campaignArticle = new WbCampaignArticle();
                campaignArticle.setCampaignId(campaignId);
                campaignArticle.setNmId(nmId);

                campaignArticleRepository.save(campaignArticle);
                savedCount++;
            } catch (Exception e) {
                log.error("Ошибка при сохранении связи кампания {} — артикул {}: {}, пропускаем",
                        campaignId, nmId, e.getMessage(), e);
            }
        }

        log.debug("Сохранено {} из {} связей артикулов для кампании {}", savedCount, campaignDto.getNmIds().size(), campaignId);
    }

    /**
     * Восстанавливает связи campaign_articles из promotion_campaign_statistics по кабинету.
     */
    private void syncWbCampaignArticlesFromStatistics(Long campaignId, Long cabinetId) {
        List<Long> nmIdsFromStats = campaignStatisticsRepository.findDistinctNmIdsByCampaignAdvertId(campaignId);
        if (nmIdsFromStats == null || nmIdsFromStats.isEmpty()) {
            log.debug("У кампании advertId {} нет записей в promotion_campaign_statistics, связи не созданы", campaignId);
            return;
        }
        campaignArticleRepository.deleteByCampaignId(campaignId);
        int savedCount = 0;
        for (Long nmId : nmIdsFromStats) {
            try {
                WbProductCard productCard = productCardRepository.findByNmIdAndCabinet_Id(nmId, cabinetId)
                        .orElse(null);
                if (productCard == null) {
                    log.warn("Артикул {} из статистики кампании {} не найден в кабинете {}, пропускаем",
                            nmId, campaignId, cabinetId);
                    continue;
                }
                WbCampaignArticle campaignArticle = new WbCampaignArticle();
                campaignArticle.setCampaignId(campaignId);
                campaignArticle.setNmId(nmId);
                campaignArticleRepository.save(campaignArticle);
                savedCount++;
            } catch (Exception e) {
                log.error("Ошибка при сохранении связи кампания {} — артикул {} (из статистики): {}, пропускаем",
                        campaignId, nmId, e.getMessage(), e);
            }
        }
        log.info("Кампания advertId {}: восстановлено {} связей campaign_articles из promotion_campaign_statistics", campaignId, savedCount);
    }
    
    /**
     * Обновляет связи кампании с артикулами.
     */
    private void updateWbCampaignArticles(WbPromotionAdvertsResponse.Campaign campaignDto, Long campaignId, Long cabinetId) {
        saveWbCampaignArticles(campaignDto, campaignId, cabinetId);
    }
    
    /**
     * Проверяет, нужно ли обновлять связи кампании с артикулами.
     * Обновляем только для активных кампаний (9) и на паузе (11).
     * Завершенные кампании (7) не обновляем.
     */
    private boolean shouldUpdateWbCampaignArticles(WbCampaignStatus status) {
        if (status == null) {
            return false;
        }
        // Обновляем только для активных и на паузе
        return status == WbCampaignStatus.ACTIVE || status == WbCampaignStatus.PAUSED;
    }

    /**
     * Преобразует DTO кампании в сущность WbPromotionCampaign.
     */
    private WbPromotionCampaign mapToWbPromotionCampaign(WbPromotionAdvertsResponse.Campaign campaignDto, Cabinet cabinet) {
        try {
            ru.oparin.solution.model.WbCampaignType campaignType = resolveWbCampaignType(campaignDto.getType());
            WbBidType bidType = resolveWbBidType(campaignDto.getBidType());
            WbCampaignPaymentType paymentType = resolvePaymentType(campaignDto.getPaymentType());
            WbCampaignStatus status = resolveWbCampaignStatus(campaignDto.getStatus());

            return WbPromotionCampaign.builder()
                    .advertId(campaignDto.getAdvertId())
                    .cabinet(cabinet)
                    .name(campaignDto.getName())
                    .type(campaignType)
                    .status(status)
                    .bidType(bidType)
                    .paymentType(paymentType)
                    .startTime(parseDateTime(campaignDto.getStartTime()))
                    .endTime(parseDateTime(campaignDto.getEndTime()))
                    .createTime(parseDateTime(campaignDto.getCreateTime()))
                    .changeTime(parseDateTime(campaignDto.getChangeTime()))
                    .build();
        } catch (Exception e) {
            log.error("Ошибка при преобразовании DTO кампании advertId {} в сущность: {}",
                    campaignDto.getAdvertId(), e.getMessage(), e);
            return null;
        }
    }

    private WbCampaignType resolveWbCampaignType(Integer typeCode) {
        WbCampaignType campaignType = WbCampaignType.fromCode(typeCode);
        if (campaignType == null) {
            log.warn("Неизвестный тип кампании: {}. Используем AUCTION по умолчанию.", typeCode);
            return WbCampaignType.AUCTION;
        }
        return campaignType;
    }

    private WbBidType resolveWbBidType(Integer bidTypeCode) {
        WbBidType bidType = WbBidType.fromCode(bidTypeCode);
        if (bidType == null && bidTypeCode != null) {
            log.warn("Неизвестный тип ставки: {}. Пропускаем.", bidTypeCode);
        }
        return bidType;
    }

    private WbCampaignPaymentType resolvePaymentType(String paymentTypeValue) {
        WbCampaignPaymentType paymentType = WbCampaignPaymentType.fromApiValue(paymentTypeValue);
        if (paymentType == null && paymentTypeValue != null && !paymentTypeValue.isBlank()) {
            log.warn("Неизвестная модель оплаты кампании: {}. Пропускаем.", paymentTypeValue);
        }
        return paymentType;
    }

    private WbCampaignStatus resolveWbCampaignStatus(Integer statusCode) {
        WbCampaignStatus status = WbCampaignStatus.fromCode(statusCode);
        if (status == null && statusCode != null) {
            log.warn("Неизвестный статус кампании: {}. Пропускаем.", statusCode);
        }
        return status;
    }

    /**
     * Парсит строку даты в LocalDateTime.
     * WB API возвращает даты с Offset, например "2025-07-31T11:38:25.803116+03:00"
     */
    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime();
        } catch (DateTimeParseException e) {
            log.warn("Не удалось распарсить дату '{}': {}", dateTimeStr, e.getMessage());
            return null;
        }
    }

    /**
     * Обновляет существующую кампанию данными из новой кампании.
     */
    private void updateCampaign(WbPromotionCampaign existing, WbPromotionCampaign updated) {
        existing.setName(updated.getName());
        existing.setType(updated.getType());
        existing.setStatus(updated.getStatus());
        existing.setBidType(updated.getBidType());
        existing.setPaymentType(updated.getPaymentType());
        existing.setStartTime(updated.getStartTime());
        existing.setEndTime(updated.getEndTime());
        existing.setCreateTime(updated.getCreateTime());
        existing.setChangeTime(updated.getChangeTime());
    }

    /**
     * Результат обработки кампаний.
     */
    private record ProcessingResult(int savedCount, int updatedCount, int skippedCount) {
    }

    /**
     * Результат сохранения кампании.
     */
    private record SaveResult(boolean isNew) {
    }
}

