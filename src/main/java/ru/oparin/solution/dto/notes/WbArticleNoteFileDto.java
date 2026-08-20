package ru.oparin.solution.dto.notes;

import lombok.*;

import java.time.LocalDateTime;

/**
 * DTO для файла заметки.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WbArticleNoteFileDto {
    /**
     * ID файла.
     */
    private Long id;

    /**
     * Оригинальное имя файла.
     */
    private String fileName;

    /**
     * Размер файла в байтах.
     */
    private Long fileSize;

    /**
     * MIME-тип файла.
     */
    private String mimeType;

    /**
     * Дата загрузки файла.
     */
    private LocalDateTime uploadedAt;
}

