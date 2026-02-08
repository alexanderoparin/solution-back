package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import ru.oparin.solution.converter.BidTypeConverter;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.converter.CampaignStatusConverter;
import ru.oparin.solution.converter.CampaignTypeConverter;

import java.time.LocalDateTime;

/**
 * Сущность рекламной кампании из WB API.
 */
@Entity
@Table(name = "promotion_campaigns", schema = "solution")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionCampaign {

    /**
     * ID кампании из WB API (advertId).
     */
    @Id
    @Column(name = "advert_id")
    private Long advertId;

    /**
     * Продавец, владелец кампании (оставляем для удобства).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    /**
     * Кабинет, которому принадлежит кампания.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cabinet_id", nullable = false)
    private Cabinet cabinet;

    /**
     * Название кампании.
     */
    @Column(name = "name", nullable = false, length = 500)
    private String name;

    /**
     * Тип кампании.
     * Хранится в БД как INTEGER, автоматически конвертируется в enum через CampaignTypeConverter.
     */
    @Column(name = "type", nullable = false)
    @Convert(converter = CampaignTypeConverter.class)
    private CampaignType type;

    /**
     * Статус кампании.
     * Хранится в БД как INTEGER, автоматически конвертируется в enum через CampaignStatusConverter.
     */
    @Column(name = "status", nullable = false)
    @Convert(converter = CampaignStatusConverter.class)
    private CampaignStatus status;

    /**
     * Тип ставки.
     * Хранится в БД как INTEGER, автоматически конвертируется в enum через BidTypeConverter.
     */
    @Column(name = "bid_type")
    @Convert(converter = BidTypeConverter.class)
    private BidType bidType;

    /**
     * Дата начала кампании.
     */
    @Column(name = "start_time")
    private LocalDateTime startTime;

    /**
     * Дата окончания кампании.
     */
    @Column(name = "end_time")
    private LocalDateTime endTime;

    /**
     * Дата создания кампании в WB.
     */
    @Column(name = "create_time")
    private LocalDateTime createTime;

    /**
     * Дата последнего изменения кампании в WB.
     */
    @Column(name = "change_time")
    private LocalDateTime changeTime;

    /**
     * Дата создания записи в БД.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Дата последнего обновления записи в БД.
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

