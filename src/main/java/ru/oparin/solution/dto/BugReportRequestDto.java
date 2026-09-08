package ru.oparin.solution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Сообщение об ошибке или предложение из шапки сервиса.
 * Скриншоты передаются отдельной multipart-частью {@code files}.
 */
@Getter
@Setter
public class BugReportRequestDto {

    /** Текст замечания, бага или предложения. */
    @NotBlank(message = "Напишите сообщение")
    @Size(max = 4000, message = "Сообщение слишком длинное")
    private String message;

    /** Адрес страницы, с которой отправлена форма. */
    @Size(max = 500, message = "Слишком длинный адрес страницы")
    private String pageUrl;
}
