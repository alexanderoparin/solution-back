package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Квота запусков А/Б тестов кабинета (пакеты без срока годности).
 */
@Entity
@Table(name = "wb_cabinet_ab_test_quota", schema = "solution")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WbCabinetAbTestQuota {

    @Id
    @Column(name = "cabinet_id")
    private Long cabinetId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "cabinet_id")
    private Cabinet cabinet;

    /** Доступно запусков. */
    @Column(name = "remaining", nullable = false)
    @Builder.Default
    private Integer remaining = 3;

    /** Успешных стартов (списаний). */
    @Column(name = "used_starts", nullable = false)
    @Builder.Default
    private Integer usedStarts = 0;

    /** Стартовый бесплатный пакет. */
    @Column(name = "included_free", nullable = false)
    @Builder.Default
    private Integer includedFree = 3;

    /**
     * Услуга подключена явно (активация FREE или покупка пакета).
     * До этого создание тестов блокируется paywall.
     */
    @Column(name = "activated", nullable = false)
    @Builder.Default
    private Boolean activated = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
