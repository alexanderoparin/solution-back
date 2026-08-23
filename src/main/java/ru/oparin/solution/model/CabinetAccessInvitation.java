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
 * Приглашение пользователя в кабинет по email.
 */
@Entity
@Table(name = "cabinet_access_invitations", schema = "solution")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CabinetAccessInvitation {

    /**
     * Уникальный идентификатор записи.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Уникальный токен ссылки приглашения.
     */
    @Column(name = "token", nullable = false, unique = true, length = 64)
    private String token;

    /**
     * Email приглашённого пользователя.
     */
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    /**
     * Кабинет, в который приглашают пользователя.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cabinet_id", nullable = false)
    private Cabinet cabinet;

    /**
     * Пользователь, отправивший приглашение.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invited_by_user_id", nullable = false)
    private User invitedByUser;

    /**
     * Разделы сервиса, доступные после принятия.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sections", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<CabinetAccessSection> sections = new ArrayList<>();

    /**
     * Номинальный тип аккаунта для отображения.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    /**
     * Комментарий к приглашению.
     */
    @Column(name = "comment_text", length = 500)
    private String commentText;

    /**
     * Статус приглашения.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private CabinetAccessInvitationStatus status = CabinetAccessInvitationStatus.PENDING;

    /**
     * Окончание действия доступа после принятия ({@code null} — бессрочно).
     */
    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    /**
     * Срок действия ссылки приглашения.
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * Время принятия приглашения.
     */
    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    /**
     * Пользователь, принявший приглашение.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accepted_by_user_id")
    private User acceptedByUser;

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
