package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Заявка пользователя на удаление аккаунта.
 */
@Entity
@Table(name = "account_deletion_requests", schema = "solution")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountDeletionRequest {

    /**
     * Уникальный идентификатор заявки.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Пользователь, подавший заявку. После удаления аккаунта становится {@code null}
     * (история сохраняется через {@link #userEmail}/{@link #userName}).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /**
     * Email на момент заявки — остаётся после удаления пользователя.
     */
    @Column(name = "user_email", nullable = false, length = 255)
    private String userEmail;

    /**
     * Имя на момент заявки — остаётся после удаления пользователя.
     */
    @Column(name = "user_name", length = 255)
    private String userName;

    /**
     * Причина удаления аккаунта (из формы пользователя).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 50)
    private AccountDeletionReason reason;

    /**
     * Дополнительный комментарий пользователя.
     */
    @Column(name = "comment_text", columnDefinition = "TEXT")
    private String commentText;

    /**
     * Статус обработки заявки.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AccountDeletionRequestStatus status = AccountDeletionRequestStatus.PENDING;

    /**
     * Дата создания заявки.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Время обработки заявки администратором.
     */
    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    /**
     * Администратор, обработавший заявку.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by_user_id")
    private User processedByUser;

    /**
     * Email админа на момент обработки — остаётся после удаления связи с пользователем.
     */
    @Column(name = "processed_by_email", length = 255)
    private String processedByEmail;
}
