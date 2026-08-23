package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Участие товара (nmId) кабинета в акции календаря WB.
 * Обновляется при синхронизации календаря акций по кабинету.
 */
@Entity
@Table(
    name = "wb_promotion_participations",
    schema = "solution",
    uniqueConstraints = @UniqueConstraint(columnNames = { "cabinet_id", "nm_id", "wb_promotion_id" })
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WbPromotionParticipation {

    /**
     * Уникальный идентификатор записи.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Кабинет продавца.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cabinet_id", nullable = false)
    private Cabinet cabinet;

    /**
     * Артикул WB (nmID), участвующий в акции.
     */
    @Column(name = "nm_id", nullable = false)
    private Long nmId;

    /**
     * ID акции в календаре WB.
     */
    @Column(name = "wb_promotion_id", nullable = false)
    private Long wbPromotionId;

    /**
     * Название акции из ответа WB.
     */
    @Column(name = "wb_promotion_name", length = 500)
    private String wbPromotionName;

    /** Тип акции из ответа WB: "regular", "auto" и т.д. */
    @Column(name = "wb_promotion_type", length = 50)
    private String wbPromotionType;
}
