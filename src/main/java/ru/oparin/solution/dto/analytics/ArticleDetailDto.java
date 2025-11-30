package ru.oparin.solution.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO для детальной информации об артикуле.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleDetailDto {
    private Long nmId;
    private String title;
    private String brand;
    private String subjectName;
    private String vendorCode;
    private BigDecimal rating;
    private Integer reviewsCount;
    private String productUrl;
}

