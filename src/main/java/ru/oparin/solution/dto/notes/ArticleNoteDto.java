package ru.oparin.solution.dto.notes;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO для заметки к артикулу.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleNoteDto {
    /**
     * ID заметки.
     */
    private Long id;

    /**
     * Артикул WB (nmId).
     */
    private Long nmId;

    /**
     * ID продавца.
     */
    private Long sellerId;

    /**
     * ID пользователя, создавшего заметку.
     */
    private Long userId;

    /**
     * Email пользователя, создавшего заметку.
     */
    private String userEmail;

    /**
     * Текст заметки.
     */
    private String content;

    /**
     * Файлы, прикрепленные к заметке.
     */
    private List<ArticleNoteFileDto> files;

    /**
     * Дата создания заметки.
     */
    private LocalDateTime createdAt;

    /**
     * Дата последнего обновления заметки.
     */
    private LocalDateTime updatedAt;
}

