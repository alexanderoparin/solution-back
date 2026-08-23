package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Метки синхронизации кабинета. Phase 5 dual-write с колонками {@link Cabinet}.
 */
@Entity
@Table(name = "cabinet_sync_state", schema = "solution")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CabinetSyncState {

    @Id
    @Column(name = "cabinet_id")
    private Long cabinetId;

    @Column(name = "last_data_update_at")
    private LocalDateTime lastDataUpdateAt;

    @Column(name = "last_data_update_requested_at")
    private LocalDateTime lastDataUpdateRequestedAt;

    @Column(name = "last_stocks_update_at")
    private LocalDateTime lastStocksUpdateAt;

    @Column(name = "last_stocks_update_requested_at")
    private LocalDateTime lastStocksUpdateRequestedAt;

    @Column(name = "last_ozon_campaigns_sync_at")
    private LocalDateTime lastOzonCampaignsSyncAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
