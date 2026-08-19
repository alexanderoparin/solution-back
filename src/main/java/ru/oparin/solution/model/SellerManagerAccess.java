package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Делегирование доступа менеджера к аккаунту селлера.
 */
@Entity
@Table(name = "seller_manager_access", schema = "solution")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerManagerAccess {

    /**
     * Идентификатор записи доступа.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Селлер, выдавший доступ.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    /**
     * Менеджер, которому выдан доступ.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manager_id", nullable = false)
    private User manager;

    /**
     * Текущий статус доступа.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SellerManagerAccessStatus status;

    /**
     * Когда доступ был выдан или восстановлен.
     */
    @Column(name = "granted_at", nullable = false)
    private LocalDateTime grantedAt;

    /**
     * Когда доступ был отозван.
     */
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;
}
