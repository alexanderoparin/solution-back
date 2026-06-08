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
@Table(name = "cabinet_promotion_balance_cache", schema = "solution")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CabinetPromotionBalanceCache {

    @Id
    @Column(name = "cabinet_id", nullable = false)
    private Long cabinetId;

    @Column(name = "balance_rub")
    private Integer balanceRub;

    @Column(name = "net_rub")
    private Integer netRub;

    @Column(name = "bonus_rub")
    private Integer bonusRub;

    @Column(name = "fetched_at")
    private LocalDateTime fetchedAt;

    @Column(name = "fetch_error", columnDefinition = "TEXT")
    private String fetchError;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
