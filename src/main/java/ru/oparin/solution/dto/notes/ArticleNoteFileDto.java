package ru.oparin.solution.dto.notes;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * DTO для файла заметки.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleNoteFileDto {
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

