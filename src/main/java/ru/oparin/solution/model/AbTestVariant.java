package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Вариант главного фото в А/Б-тесте.
 */
@Entity
@Table(name = "ab_test_variant", schema = "solution")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbTestVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ab_test_id", nullable = false)
    private Long abTestId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "is_control", nullable = false)
    @Builder.Default
    private boolean control = false;

    @Column(name = "photo_url", length = 1000)
    private String photoUrl;

    @Column(name = "preview_url", length = 1000)
    private String previewUrl;

    @Column(name = "stored_file_name", length = 512)
    private String storedFileName;

    /**
     * Вариант успели загрузить в слот 2+ на старте (устаревший сценарий).
     * Нужен, чтобы откатить галерею через {@code media/save}.
     */
    @Column(name = "wb_uploaded", nullable = false)
    @Builder.Default
    private boolean wbUploaded = false;

    /**
     * Вариант на паузе — не участвует в ротации (можно отсечь явно проигрывающий).
     */
    @Column(name = "paused", nullable = false)
    @Builder.Default
    private boolean paused = false;

    @Column(name = "views", nullable = false)
    @Builder.Default
    private long views = 0;

    @Column(name = "clicks", nullable = false)
    @Builder.Default
    private long clicks = 0;

    @Column(name = "atbs", nullable = false)
    @Builder.Default
    private long atbs = 0;

    @Column(name = "orders", nullable = false)
    @Builder.Default
    private long orders = 0;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * CTR в процентах (клики / показы * 100).
     */
    public BigDecimal computeCtr() {
        if (views <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(clicks)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(views), 4, RoundingMode.HALF_UP);
    }

    /**
     * CR1: добавления в корзину / клики * 100.
     */
    public BigDecimal computeCr1() {
        if (clicks <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(atbs)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(clicks), 4, RoundingMode.HALF_UP);
    }

    /**
     * CR: заказы / клики * 100.
     */
    public BigDecimal computeCr() {
        if (clicks <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(orders)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(clicks), 4, RoundingMode.HALF_UP);
    }
}
