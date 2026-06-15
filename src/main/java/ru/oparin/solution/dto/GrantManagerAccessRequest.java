package ru.oparin.solution.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Value;

/**
 * Запрос на выдачу доступа менеджеру по email.
 */
@Value
public class GrantManagerAccessRequest {

    @NotBlank
    @Email
    String managerEmail;
}
