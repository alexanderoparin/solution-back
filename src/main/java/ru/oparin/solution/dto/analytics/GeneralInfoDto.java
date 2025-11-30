package ru.oparin.solution.dto.analytics;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * DTO для общей информации на сводной странице.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneralInfoDto {
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startOfWork;
    
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate generalFunnelAvailableFrom;
    
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate advertisingFunnelAvailableFrom;
    
    private Integer articlesInWork;
}

