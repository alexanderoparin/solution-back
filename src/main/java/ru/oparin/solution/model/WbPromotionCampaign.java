package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import ru.oparin.solution.converter.WbBidTypeConverter;
import ru.oparin.solution.converter.WbCampaignStatusConverter;
import ru.oparin.solution.converter.WbCampaignTypeConverter;

import java.time.LocalDateTime;

/**
 * Сущность рекламной кампании из WB API.
 */
@Entity
@Table(name = "wb_promotion_campaigns", schema = "solution")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WbPromotionCampaign {

    /**
     * ID кампании из WB API (advertId).
     */
    @Id
    @Column(name = "advert_id")
    private Long advertId;

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
     * Хранится в БД как INTEGER, автоматически конвертируется в enum через WbCampaignTypeConverter.
     */
    @Column(name = "type", nullable = false)
    @Convert(converter = WbCampaignTypeConverter.class)
    private WbCampaignType type;

    /**
     * Статус кампании.
     * Хранится в БД как INTEGER, автоматически конвертируется в enum через WbCampaignStatusConverter.
     */
    @Column(name = "status", nullable = false)
    @Convert(converter = WbCampaignStatusConverter.class)
    private WbCampaignStatus status;

    /**
     * Тип ставки.
     * Хранится в БД как INTEGER, автоматически конвертируется в enum через WbBidTypeConverter.
     */
    @Column(name = "bid_type")
    @Convert(converter = WbBidTypeConverter.class)
    private WbBidType bidType;

    /**
     * Модель оплаты из {@code settings.payment_type} WB API.
     * CPC имеет приоритет при формировании отображаемого типа кампании.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", length = 10)
    private WbCampaignPaymentType paymentType;

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

    /**
     * Возвращает актуальное название типа кампании для интерфейса.
     *
     * @return CPC для оплаты за клики, иначе тип ставки WB
     */
    public String getDisplayType() {
        if (paymentType == WbCampaignPaymentType.CPC) {
            return "CPC";
        }
        if (bidType != null) {
            return bidType.getDescription();
        }
        return type != null ? type.getDescription() : null;
    }
}

