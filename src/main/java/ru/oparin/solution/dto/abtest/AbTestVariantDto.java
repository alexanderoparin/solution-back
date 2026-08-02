package ru.oparin.solution.dto.abtest;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Вариант главного фото А/Б-теста.
 */
@Data
@Builder
public class AbTestVariantDto {

    private Long id;
    private Integer sortOrder;
    private boolean control;
    private String photoUrl;
    private String previewUrl;
    /**
     * Есть локальный файл в uploads — для UI брать картинку через API варианта, не CDN-слот карточки.
     * После media/save пути вида {@code images/big/2.webp} снова указывают на исходную галерею.
     */
    private boolean hasLocalImage;
    private long views;
    private long clicks;
    private long atbs;
    private long orders;
    private BigDecimal ctr;
    private BigDecimal cr1;
    private BigDecimal cr;
    /** Доля показов среди всех вариантов, 0–100. */
    private BigDecimal sharePercent;
    private boolean activeOnWb;
    /** Вариант на паузе — не участвует в ротации. */
    private boolean paused;
    /** Относительный отрыв CTR к лучшему (для списка), может быть null. */
    private BigDecimal ctrDeltaToBest;
    private boolean losing;
    /** Лидер CTR при наличии проигрывающих (insight HAS_LEADER). */
    private boolean winning;
}
