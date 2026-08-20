package ru.oparin.solution.dto;

import lombok.*;

/**
 * Квота А/Б тестов кабинета.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WbAbTestQuotaDto {

    private Integer remaining;
    private Integer usedStarts;
    private Integer includedFree;
    /** Безлимит (PRO / agency). */
    private Boolean unlimited;
    /**
     * Услуга А/Б явно подключена (FREE активирован или куплен пакет).
     * Пока false — счётчик в UI скрыт, создание открывает paywall.
     */
    private Boolean activated;
}
