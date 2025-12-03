package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.wb.PromotionAdvertsResponse;
import ru.oparin.solution.model.BidType;
import ru.oparin.solution.model.CampaignStatus;
import ru.oparin.solution.model.CampaignType;
import ru.oparin.solution.model.CampaignArticle;
import ru.oparin.solution.model.ProductCard;
import ru.oparin.solution.model.PromotionCampaign;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.CampaignArticleRepository;
import ru.oparin.solution.repository.ProductCardRepository;
import ru.oparin.solution.repository.PromotionCampaignRepository;

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
public class PromotionCampaignService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final PromotionCampaignRepository campaignRepository;
    private final CampaignArticleRepository campaignArticleRepository;
    private final ProductCardRepository productCardRepository;

    /**
     * Сохраняет или обновляет кампании из ответа WB API.
     *
     * @param response ответ от WB API со списком кампаний
     * @param seller   продавец, владелец кампаний
     */
    @Transactional
    public void saveOrUpdateCampaigns(PromotionAdvertsResponse response, User seller) {
        if (isEmptyResponse(response)) {
            log.info("Ответ со списком кампаний пуст, сохранение/обновление не требуется.");
            return;
        }

        ProcessingResult result = processCampaigns(response.getAdverts(), seller);

        log.info("Обработано кампаний: создано {}, обновлено {}, пропущено {}",
                result.savedCount(), result.updatedCount(), result.skippedCount());
    }

    private boolean isEmptyResponse(PromotionAdvertsResponse response) {
        return response == null
                || response.getAdverts() == null
                || response.getAdverts().isEmpty();
    }

    private ProcessingResult processCampaigns(List<PromotionAdvertsResponse.Campaign> campaignDtos, User seller) {
        int savedCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;

        for (PromotionAdvertsResponse.Campaign campaignDto : campaignDtos) {
            if (!isValidCampaign(campaignDto)) {
                skippedCount++;
                continue;
            }

            try {
                Optional<SaveResult> result = processCampaign(campaignDto, seller);
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

    private boolean isValidCampaign(PromotionAdvertsResponse.Campaign campaignDto) {
        if (campaignDto == null || campaignDto.getAdvertId() == null) {
            log.warn("Получена некорректная DTO кампании (null или advertId null), пропускаем.");
            return false;
        }
        return true;
    }

    private Optional<SaveResult> processCampaign(PromotionAdvertsResponse.Campaign campaignDto, User seller) {
        PromotionCampaign campaign = mapToPromotionCampaign(campaignDto, seller);
        if (campaign == null) {
            return Optional.empty();
        }

        Optional<PromotionCampaign> existingCampaign = campaignRepository.findByAdvertIdAndSellerId(
                campaign.getAdvertId(),
                seller.getId()
        );

        if (existingCampaign.isPresent()) {
            PromotionCampaign existing = existingCampaign.get();
            updateCampaign(existing, campaign);
            campaignRepository.save(existing);
            // Обновляем связи с артикулами только для активных кампаний или на паузе
            if (shouldUpdateCampaignArticles(campaign.getStatus())) {
                updateCampaignArticles(campaignDto, existing.getAdvertId(), seller.getId());
            } else {
                // Для завершенных кампаний удаляем связи
                campaignArticleRepository.deleteByCampaignId(existing.getAdvertId());
            }
            return Optional.of(new SaveResult(false));
        } else {
            campaignRepository.save(campaign);
            // Сохраняем связи с артикулами только для активных кампаний или на паузе
            if (shouldUpdateCampaignArticles(campaign.getStatus())) {
                saveCampaignArticles(campaignDto, campaign.getAdvertId(), seller.getId());
            }
            return Optional.of(new SaveResult(true));
        }
    }
    
    /**
     * Сохраняет связи кампании с артикулами.
     */
    private void saveCampaignArticles(PromotionAdvertsResponse.Campaign campaignDto, Long campaignId, Long sellerId) {
        if (campaignDto.getNmIds() == null || campaignDto.getNmIds().isEmpty()) {
            return;
        }
        
        // Удаляем старые связи
        campaignArticleRepository.deleteByCampaignId(campaignId);
        
        // Создаем новые связи
        for (Long nmId : campaignDto.getNmIds()) {
            // Проверяем, что артикул принадлежит продавцу
            ProductCard productCard = productCardRepository.findByNmId(nmId)
                    .filter(card -> card.getSeller().getId().equals(sellerId))
                    .orElse(null);
            
            if (productCard == null) {
                log.warn("Артикул {} не найден или не принадлежит продавцу {}, пропускаем связь с кампанией {}", 
                        nmId, sellerId, campaignId);
                continue;
            }
            
            CampaignArticle campaignArticle = new CampaignArticle();
            campaignArticle.setCampaignId(campaignId);
            campaignArticle.setNmId(nmId);
            
            campaignArticleRepository.save(campaignArticle);
        }
        
        log.debug("Сохранено {} связей артикулов для кампании {}", campaignDto.getNmIds().size(), campaignId);
    }
    
    /**
     * Обновляет связи кампании с артикулами.
     */
    private void updateCampaignArticles(PromotionAdvertsResponse.Campaign campaignDto, Long campaignId, Long sellerId) {
        saveCampaignArticles(campaignDto, campaignId, sellerId);
    }
    
    /**
     * Проверяет, нужно ли обновлять связи кампании с артикулами.
     * Обновляем только для активных кампаний (9) и на паузе (11).
     * Завершенные кампании (7) не обновляем.
     */
    private boolean shouldUpdateCampaignArticles(CampaignStatus status) {
        if (status == null) {
            return false;
        }
        // Обновляем только для активных и на паузе
        return status == CampaignStatus.ACTIVE || status == CampaignStatus.PAUSED;
    }

    /**
     * Преобразует DTO кампании в сущность PromotionCampaign.
     */
    private PromotionCampaign mapToPromotionCampaign(PromotionAdvertsResponse.Campaign campaignDto, User seller) {
        try {
            ru.oparin.solution.model.CampaignType campaignType = resolveCampaignType(campaignDto.getType());
            BidType bidType = resolveBidType(campaignDto.getBidType());
            CampaignStatus status = resolveCampaignStatus(campaignDto.getStatus());

            return PromotionCampaign.builder()
                    .advertId(campaignDto.getAdvertId())
                    .seller(seller)
                    .name(campaignDto.getName())
                    .type(campaignType)
                    .status(status)
                    .bidType(bidType)
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

    private CampaignType resolveCampaignType(Integer typeCode) {
        CampaignType campaignType = CampaignType.fromCode(typeCode);
        if (campaignType == null) {
            log.warn("Неизвестный тип кампании: {}. Используем AUCTION по умолчанию.", typeCode);
            return CampaignType.AUCTION;
        }
        return campaignType;
    }

    private BidType resolveBidType(Integer bidTypeCode) {
        BidType bidType = BidType.fromCode(bidTypeCode);
        if (bidType == null && bidTypeCode != null) {
            log.warn("Неизвестный тип ставки: {}. Пропускаем.", bidTypeCode);
        }
        return bidType;
    }

    private CampaignStatus resolveCampaignStatus(Integer statusCode) {
        CampaignStatus status = CampaignStatus.fromCode(statusCode);
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
            return OffsetDateTime.parse(dateTimeStr, DATE_TIME_FORMATTER).toLocalDateTime();
        } catch (DateTimeParseException e) {
            log.warn("Не удалось распарсить дату '{}': {}", dateTimeStr, e.getMessage());
            return null;
        }
    }

    /**
     * Обновляет существующую кампанию данными из новой кампании.
     */
    private void updateCampaign(PromotionCampaign existing, PromotionCampaign updated) {
        existing.setName(updated.getName());
        existing.setType(updated.getType());
        existing.setStatus(updated.getStatus());
        existing.setBidType(updated.getBidType());
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

