package ru.oparin.solution.dto.analytics;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import ru.oparin.solution.model.MarketplaceType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO для краткой информации об артикуле в сводной таблице.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ArticleSummaryDto {
    /**
     * Артикул WB (nmID). Для Ozon дублирует {@link #productId} для совместимости фильтров.
     */
    private Long nmId;

    /**
     * Идентификатор товара Ozon (product_id). Только для Ozon-кабинетов.
     */
    private Long productId;

    /**
     * Артикул продавца Ozon (offer_id).
     */
    private String offerId;

    /**
     * Маркетплейс карточки (WB | OZON).
     */
    private MarketplaceType marketplaceType;
    
    /**
     * Название товара.
     */
    private String title;
    
    /**
     * Бренд товара.
     */
    private String brand;
    
    /**
     * Название категории товара.
     */
    private String subjectName;
    
    /**
     * URL миниатюры первой фотографии товара.
     */
    private String photoTm;

    /**
     * URL превью 246×328 (WB); для шапки артикула и «в связке», если задано.
     */
    private String photoC246x328;

    /**
     * Артикул продавца.
     */
    private String vendorCode;

    /**
     * Средний рейтинг по отзывам WB (1–5).
     */
    private BigDecimal rating;

    /**
     * Приоритетная карточка для ускоренной обработки событий по nmID.
     */
    private Boolean isPriority;

    /**
     * Дата и время появления карточки на Wildberries.
     */
    private LocalDateTime wbCreatedAt;

    /**
     * Цена продавца (Ozon, последний снимок).
     */
    private BigDecimal price;

    /**
     * Старая цена (Ozon).
     */
    private BigDecimal oldPrice;

    /**
     * Дата снимка цены (Ozon).
     */
    private LocalDate priceDate;

    /**
     * Остаток FBO present (Ozon, сумма по SKU).
     */
    private Integer stockFbo;

    /**
     * Остаток FBS present (Ozon, сумма по SKU).
     */
    private Integer stockFbs;

    /**
     * Заказано единиц за период синхронизации аналитики (Ozon).
     */
    private Integer orderedUnits;

    /**
     * Выручка за период синхронизации аналитики (Ozon), руб.
     */
    private BigDecimal revenue;
}

