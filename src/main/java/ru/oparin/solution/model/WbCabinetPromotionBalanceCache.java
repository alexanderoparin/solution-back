package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Кэш баланса продвижения WB по кабинету (GET /adv/v1/balance).
 */
@Entity
@Table(name = "wb_cabinet_promotion_balance_cache", schema = "solution")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WbCabinetPromotionBalanceCache {

    /**
     * Идентификатор кабинета (PK).
     */
    @Id
    @Column(name = "cabinet_id", nullable = false)
    private Long cabinetId;

    /**
     * Баланс продвижения, руб.
     */
    @Column(name = "balance_rub")
    private Integer balanceRub;

    /**
     * Сетевой баланс (net), руб.
     */
    @Column(name = "net_rub")
    private Integer netRub;

    /**
     * Бонусы продвижения, руб.
     */
    @Column(name = "bonus_rub")
    private Integer bonusRub;

    /**
     * Сумма промо-бонусов ({@code cashbacks}) из ответа WB.
     */
    @Column(name = "cashback_rub")
    private Integer cashbackRub;

    /**
     * Процент от суммы пополнения, который можно оплатить промо-бонусами
     * ({@code cashbacks[].percent} из GET /adv/v1/balance).
     */
    @Column(name = "cashback_percent")
    private Integer cashbackPercent;

    /**
     * Время последнего успешного запроса к WB.
     */
    @Column(name = "fetched_at")
    private LocalDateTime fetchedAt;

    /**
     * Текст последней ошибки при запросе баланса.
     */
    @Column(name = "fetch_error", columnDefinition = "TEXT")
    private String fetchError;

    /**
     * Дата последнего обновления записи.
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
