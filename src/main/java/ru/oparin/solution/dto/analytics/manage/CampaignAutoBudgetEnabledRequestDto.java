package ru.oparin.solution.dto.analytics.manage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Включение или выключение автопополнения бюджета без разблокировки остальных настроек.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CampaignAutoBudgetEnabledRequestDto {

    private boolean enabled;
}
