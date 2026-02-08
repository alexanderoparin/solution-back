package ru.oparin.solution.dto.cabinet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * DTO кабинета продавца (ответ API).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CabinetDto {

    private Long id;
    private String name;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Информация о WB API ключе кабинета.
     */
    private ApiKeyInfo apiKey;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiKeyInfo {
        private String apiKey;
        private Boolean isValid;
        private LocalDateTime lastValidatedAt;
        private String validationError;
        private LocalDateTime lastDataUpdateAt;
    }
}
