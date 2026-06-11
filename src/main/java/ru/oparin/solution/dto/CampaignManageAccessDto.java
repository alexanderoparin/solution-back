package ru.oparin.solution.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Статус доступа к разделу «Управление РК».
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignManageAccessDto {

    /** Включён ли продукт «Управление РК» в конфигурации. */
    private boolean enabled;

    /** Есть ли доступ к действиям в разделе. */
    private boolean hasAccess;

    /** NONE — не подключали; ACTIVE — активна; EXPIRED — истекла. */
    private String status;

    private LocalDateTime expiresAt;

    private Integer daysRemaining;

    private Integer daysExpiredAgo;

    /** Можно ли активировать бесплатный план (один раз на аккаунт). */
    private boolean canActivateFree;
}
