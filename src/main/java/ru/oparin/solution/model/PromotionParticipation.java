package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Участие товара (nmId) кабинета в акции календаря WB.
 * Обновляется при синхронизации календаря акций по кабинету.
 */
@Entity
@Table(
    name = "promotion_participations",
    schema = "solution",
    uniqueConstraints = @UniqueConstraint(columnNames = { "cabinet_id", "nm_id", "wb_promotion_id" })
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionParticipation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cabinet_id", nullable = false)
    private Cabinet cabinet;

    @Column(name = "nm_id", nullable = false)
    private Long nmId;

    @Column(name = "wb_promotion_id", nullable = false)
    private Long wbPromotionId;

    @Column(name = "wb_promotion_name", length = 500)
    private String wbPromotionName;
}
