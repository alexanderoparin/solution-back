package ru.oparin.solution.dto.analytics;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO для запроса сводной аналитики.
 * Используется для получения агрегированных данных по всем артикулам за указанные периоды.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SummaryRequestDto {
    /**
     * Список периодов для анализа. Должен содержать от 1 до 10 периодов.
     */
    @Valid
    @NotEmpty(message = "Периоды не могут быть пустыми")
    @Size(min = 1, max = 10, message = "Количество периодов должно быть от 1 до 10")
    private List<PeriodDto> periods;
    
    /**
     * Список артикулов (nmId), которые нужно исключить из анализа.
     */
    @Builder.Default
    private List<Long> excludedNmIds = new ArrayList<>();

    /**
     * ID селлера для просмотра аналитики (опционально, только для ADMIN/MANAGER).
     * Если не указан, используется последний активный селлер.
     */
    private Long sellerId;

    /**
     * ID кабинета (опционально). Если указан и принадлежит селлеру — аналитика по этому кабинету.
     * Если не указан — кабинет по умолчанию (последний созданный).
     */
    private Long cabinetId;

    /**
     * Номер страницы для пагинации (0-based). Если null — возвращаются все артикулы (без пагинации).
     */
    private Integer page;

    /**
     * Размер страницы. Используется вместе с page. Если null — пагинация не применяется.
     */
    private Integer size;

    /**
     * Поиск по названию, артикулу WB (nmId) или артикулу продавца. При пагинации фильтрация на бэкенде.
     */
    private String search;

    /**
     * Если не пустой — при пагинации вернуть только артикулы с nmId из этого списка (фильтр по чипам).
     */
    @Builder.Default
    private List<Long> includedNmIds = new ArrayList<>();

    /**
     * Начало периода для метрик РК в блоке «Список РК» (опционально).
     * Если заданы оба — campaigns в ответе заполняются views, clicks, ctr, cpc, costs, cart, orders за этот период.
     */
    private LocalDate campaignDateFrom;

    /**
     * Окончание периода для метрик РК в блоке «Список РК» (опционально).
     */
    private LocalDate campaignDateTo;
}

