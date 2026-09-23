package ru.oparin.solution.dto.analytics.manage;

import lombok.*;

/**
 * Кабинет владельца РК, к которому у текущего пользователя есть доступ.
 * Используется для автопереключения контекста по прямой ссылке на кампанию.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignCabinetResolveDto {

    /** ID кабинета, которому принадлежит кампания. */
    private Long cabinetId;

    /** ID владельца кабинета (селлера). */
    private Long sellerId;

    /** Название кабинета (для подсказки в UI). */
    private String cabinetName;
}
