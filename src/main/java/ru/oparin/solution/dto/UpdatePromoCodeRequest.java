package ru.oparin.solution.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Запрос на редактирование промокода в админке (в окне 30 минут после создания).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePromoCodeRequest {

    /** Код промо (без учёта регистра, сохранится в верхнем регистре). */
    @NotBlank(message = "Укажите код промокода")
    private String code;

    /** Описание для админки. */
    private String description;

    /** Срок доступа в днях с момента активации. */
    @NotNull(message = "Укажите срок действия в днях")
    @Min(value = 1, message = "Срок действия должен быть не меньше 1 дня")
    private Integer durationDays;

    /** Доступен ли код для активации. */
    @NotNull(message = "Укажите статус активности")
    private Boolean active;

    /** Начало периода, когда код можно ввести. */
    private LocalDateTime validFrom;

    /** Конец периода, когда код можно ввести. */
    private LocalDateTime validTo;
}
