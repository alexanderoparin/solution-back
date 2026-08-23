package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Выданный доступ пользователя к кабинету по разделам.
 */
@Entity
@Table(name = "cabinet_access_grants", schema = "solution")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CabinetAccessGrant {

    /**
     * Уникальный идентификатор записи.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Кабинет, к которому выдан доступ.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cabinet_id", nullable = false)
    private Cabinet cabinet;

    /**
     * Пользователь с доступом к кабинету.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Разделы сервиса, доступные пользователю.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sections", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<CabinetAccessSection> sections = new ArrayList<>();

    /**
     * Статус выданного доступа.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private CabinetAccessGrantStatus status = CabinetAccessGrantStatus.ACTIVE;

    /**
     * Комментарий при выдаче доступа.
     */
    @Column(name = "comment_text", length = 500)
    private String commentText;

    /**
     * Начало действия доступа.
     */
    @Column(name = "valid_from", nullable = false)
    private LocalDateTime validFrom;

    /**
     * Окончание действия доступа ({@code null} — бессрочно).
     */
    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    /**
     * Пользователь, выдавший доступ.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by_user_id")
    private User grantedByUser;

    /**
     * Приглашение, по которому был выдан доступ (если есть).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invitation_id")
    private CabinetAccessInvitation invitation;

    /**
     * Время отзыва доступа.
     */
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    /**
     * Дата создания записи.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Дата последнего обновления записи.
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
