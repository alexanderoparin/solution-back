package ru.oparin.solution.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Запрос на выдачу доступа менеджеру по email.
 */
@Getter
@Setter
public class GrantManagerAccessRequest {

    @NotBlank
    @Email
    private String managerEmail;
}
