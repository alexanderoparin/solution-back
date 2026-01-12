package ru.oparin.solution.dto.notes;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO для обновления заметки.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateNoteRequest {
    /**
     * Текст заметки.
     */
    @NotBlank(message = "Текст заметки не может быть пустым")
    private String content;
}

