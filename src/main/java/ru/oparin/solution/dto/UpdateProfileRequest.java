package ru.oparin.solution.dto;

import lombok.Getter;
import lombok.Setter;
import ru.oparin.solution.model.AccountType;

import java.util.List;

/**
 * Обновление профиля (имя и типы аккаунта).
 */
@Getter
@Setter
public class UpdateProfileRequest {
    private String name;
    private List<AccountType> accountTypes;
}
