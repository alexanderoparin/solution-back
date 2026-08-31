package ru.oparin.solution.service.analytics.wb;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.analytics.ArticleSummaryDto;
import ru.oparin.solution.dto.analytics.ArticleSummarySortField;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.CabinetTokenType;
import ru.oparin.solution.model.MarketplaceType;
import ru.oparin.solution.model.User;
import ru.oparin.solution.model.WbProductCard;
import ru.oparin.solution.repository.WbCampaignArticleRepository;
import ru.oparin.solution.repository.WbProductCardRepository;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.analytics.WbProductCardFilter;
import ru.oparin.solution.util.ArticleRatingUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Каталог артикулов WB: список, фильтры, маппинг, приоритет.
 */
@Service
@RequiredArgsConstructor
public class WbArticleCatalogQuery {

    private final WbProductCardRepository productCardRepository;
    private final WbCampaignArticleRepository campaignArticleRepository;
    private final CabinetService cabinetService;

    /**
     * Список артикулов для попапа фильтра.
     */
    @Transactional(readOnly = true)
    public List<ArticleSummaryDto> getArticleList(
            User seller,
            Long cabinetId,
            Boolean onlyWithPhoto,
            Boolean onlyPriority,
            Boolean onlyInAdvertising
    ) {
        List<WbProductCard> allCards = applyCatalogFilters(
                getVisibleCards(seller.getId(), cabinetId, null),
                seller.getId(),
                cabinetId,
                onlyWithPhoto,
                onlyPriority,
                onlyInAdvertising
        );
        sortProductCards(allCards, ArticleSummarySortField.WB_CREATED_AT, Sort.Direction.DESC);
        return mapToArticleSummaries(allCards, isItemRatingSupported(seller.getId(), cabinetId));
    }

    /**
     * Карточка артикула с проверкой владельца.
     */
    @Transactional(readOnly = true)
    public WbProductCard findCardBySeller(Long nmId, Long sellerId, Long cabinetId) {
        WbProductCard card = (cabinetId != null
                ? productCardRepository.findByNmIdAndCabinetIdWithCabinetAndUser(nmId, cabinetId)
                : productCardRepository.findByNmIdWithCabinetAndUser(nmId))
                .orElseThrow(() -> new UserException("Артикул не найден: " + nmId, HttpStatus.NOT_FOUND));

        if (card.getCabinet() == null
                || card.getCabinet().getUser() == null
                || !card.getCabinet().getUser().getId().equals(sellerId)) {
            throw new UserException("Артикул не принадлежит продавцу", HttpStatus.FORBIDDEN);
        }
        return card;
    }

    /**
     * Сохраняет флаг приоритета артикула.
     */
    @Transactional
    public void updateArticlePriority(User seller, Long cabinetId, Long nmId, boolean isPriority) {
        WbProductCard card = findCardBySeller(nmId, seller.getId(), cabinetId);
        card.setIsPriority(isPriority);
        productCardRepository.save(card);
    }

    /**
     * Артикулы продавца/кабинета без excluded nmId.
     */
    public List<WbProductCard> getVisibleCards(Long sellerId, Long cabinetId, List<Long> excludedNmIds) {
        List<WbProductCard> allCards = cabinetId != null
                ? productCardRepository.findByCabinet_Id(cabinetId)
                : productCardRepository.findByCabinet_User_Id(sellerId);
        return WbProductCardFilter.filterVisibleCards(allCards, excludedNmIds);
    }

    /**
     * Фильтры каталога: фото, приоритет, участие в незавершённых РК.
     */
    public List<WbProductCard> applyCatalogFilters(
            List<WbProductCard> cards,
            Long sellerId,
            Long cabinetId,
            Boolean onlyWithPhoto,
            Boolean onlyPriority,
            Boolean onlyInAdvertising
    ) {
        List<WbProductCard> result = cards;
        if (Boolean.TRUE.equals(onlyWithPhoto)) {
            result = result.stream()
                    .filter(this::cardHasAnyPhoto)
                    .collect(Collectors.toList());
        }
        if (Boolean.TRUE.equals(onlyPriority)) {
            result = result.stream()
                    .filter(c -> Boolean.TRUE.equals(c.getIsPriority()))
                    .collect(Collectors.toList());
        }
        if (Boolean.TRUE.equals(onlyInAdvertising)) {
            Set<Long> advertisedNmIds = getNmIdsInNonFinishedCampaigns(sellerId, cabinetId);
            result = result.stream()
                    .filter(c -> c.getNmId() != null && advertisedNmIds.contains(c.getNmId()))
                    .collect(Collectors.toList());
        }
        return result;
    }

    /**
     * Поиск по названию, vendorCode или nmId.
     */
    public List<WbProductCard> filterCardsBySearch(List<WbProductCard> cards, String search) {
        String lower = search.toLowerCase();
        return cards.stream()
                .filter(card ->
                        (card.getTitle() != null && card.getTitle().toLowerCase().contains(lower))
                                || (card.getVendorCode() != null && card.getVendorCode().toLowerCase().contains(lower))
                                || (card.getNmId() != null && String.valueOf(card.getNmId()).contains(lower))
                )
                .collect(Collectors.toList());
    }

    /**
     * Сортировка списка карточек по полю сводной.
     */
    public void sortProductCards(
            List<WbProductCard> cards,
            ArticleSummarySortField sortBy,
            Sort.Direction sortDir
    ) {
        ArticleSummarySortField effectiveSortBy = sortBy != null ? sortBy : ArticleSummarySortField.WB_CREATED_AT;
        Sort.Direction effectiveSortDir = sortDir != null ? sortDir : Sort.Direction.DESC;
        Comparator<WbProductCard> comparator = switch (effectiveSortBy) {
            case WB_CREATED_AT -> Comparator.comparing(
                    WbProductCard::getWbCreatedAt,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
        };
        if (effectiveSortDir == Sort.Direction.DESC) {
            comparator = comparator.reversed();
        }
        cards.sort(comparator);
    }

    /**
     * Маппинг списка карточек в DTO сводной.
     */
    public List<ArticleSummaryDto> mapToArticleSummaries(List<WbProductCard> cards, boolean itemRatingSupported) {
        return cards.stream()
                .map(card -> mapToArticleSummary(card, itemRatingSupported))
                .collect(Collectors.toList());
    }

    /**
     * Маппинг одной карточки WB в DTO сводной.
     */
    public ArticleSummaryDto mapToArticleSummary(WbProductCard card, boolean itemRatingSupported) {
        return ArticleSummaryDto.builder()
                .nmId(card.getNmId())
                .marketplaceType(MarketplaceType.WB)
                .title(card.getTitle())
                .brand(card.getBrand())
                .subjectName(card.getSubjectName())
                .photoTm(card.getPhotoTm())
                .photoC246x328(card.getPhotoC246x328())
                .vendorCode(card.getVendorCode())
                .rating(itemRatingSupported ? ArticleRatingUtils.toDisplayRating(card.getRating()) : null)
                .isPriority(Boolean.TRUE.equals(card.getIsPriority()))
                .wbCreatedAt(card.getWbCreatedAt())
                .build();
    }

    /**
     * Item-rating WB недоступен для кабинетов с базовым токеном.
     */
    public boolean isItemRatingSupported(Long sellerId, Long cabinetId) {
        if (cabinetId != null) {
            return cabinetService.findById(cabinetId)
                    .map(c -> CabinetTokenType.effective(c.getTokenType()).supportsItemRating())
                    .orElse(false);
        }
        if (sellerId == null) {
            return false;
        }
        return cabinetService.findDefaultByUserId(sellerId)
                .map(c -> CabinetTokenType.effective(c.getTokenType()).supportsItemRating())
                .orElse(false);
    }

    private boolean cardHasAnyPhoto(WbProductCard card) {
        return (card.getPhotoTm() != null && !card.getPhotoTm().isBlank())
                || (card.getPhotoC246x328() != null && !card.getPhotoC246x328().isBlank());
    }

    private Set<Long> getNmIdsInNonFinishedCampaigns(Long sellerId, Long cabinetId) {
        if (cabinetId != null) {
            return campaignArticleRepository.findDistinctNmIdsByCabinetIdExcludingFinishedCampaigns(cabinetId);
        }
        return campaignArticleRepository.findDistinctNmIdsBySellerIdExcludingFinishedCampaigns(sellerId);
    }
}
