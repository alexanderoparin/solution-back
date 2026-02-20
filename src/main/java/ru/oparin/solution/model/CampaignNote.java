package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Заметка к рекламной кампании (РК).
 */
@Entity
@Table(name = "campaign_notes", schema = "solution")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * ID кампании (advert_id из WB).
     */
    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    /**
     * Кабинет, к которому привязана кампания.
     */
    @Column(name = "cabinet_id", nullable = false)
    private Long cabinetId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
