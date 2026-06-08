package ru.oparin.solution.dto.analytics.manage;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceRefreshResponseDto {
    private BalanceSourcesResponseDto sources;
    private boolean refreshed;
    private boolean stale;
    private Long nextAvailableInSeconds;
    private String message;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime fetchedAt;
}
