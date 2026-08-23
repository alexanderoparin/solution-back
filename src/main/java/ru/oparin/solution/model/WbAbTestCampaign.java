package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Связь А/Б-теста с рекламной кампанией, по которой собирается статистика.
 */
@Entity
@Table(name = "wb_ab_test_campaign", schema = "solution",
        uniqueConstraints = @UniqueConstraint(columnNames = {"ab_test_id", "advert_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WbAbTestCampaign {

    /**
     * Уникальный идентификатор связи.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * ID А/Б-теста.
     */
    @Column(name = "ab_test_id", nullable = false)
    private Long abTestId;

    /** ID кампании WB (advert_id). */
    @Column(name = "advert_id", nullable = false)
    private Long advertId;
}
