package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Квота запусков А/Б тестов на пользователя (пакеты без срока).
 */
@Entity
@Table(name = "user_ab_test_quota", schema = "solution")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAbTestQuota {

    /**
     * Пользователь-владелец квоты.
     */
    @Id
    @Column(name = "user_id")
    private Long userId;

    /**
     * Пользователь (связь).
     */
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    /**
     * Оставшиеся запуски.
     */
    @Column(name = "remaining", nullable = false)
    @Builder.Default
    private Integer remaining = 0;

    /**
     * Всего списанных запусков.
     */
    @Column(name = "used_starts", nullable = false)
    @Builder.Default
    private Integer usedStarts = 0;

    /**
     * Дата создания.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Дата обновления.
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
