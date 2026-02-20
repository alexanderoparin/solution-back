package ru.oparin.solution.dto.analytics;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO детальной страницы рекламной кампании (комбо): название, статус, артикулы.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignDetailDto {

    private Long id;
    private String name;
    private Integer status;
    private String statusName;
    private Integer articlesCount;
    /** Артикулы в комбо: фото, название, nmId для отображения в блоке товаров. */
    private List<ArticleSummaryDto> articles;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
}
