package ru.oparin.solution.dto.analytics;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAdCampaignGoalRequest {

    /** Текст цели; null или пустая строка допустимы */
    @Size(max = 10000, message = "Текст не длиннее 10000 символов")
    private String goal;
}
