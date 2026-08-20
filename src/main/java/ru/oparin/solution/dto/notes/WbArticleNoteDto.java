package ru.oparin.solution.dto.notes;

import lombok.*;

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
public class WbArticleNoteDto {
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
    private List<WbArticleNoteFileDto> files;

    /**
     * Дата создания заметки.
     */
    private LocalDateTime createdAt;

    /**
     * Дата последнего обновления заметки.
     */
    private LocalDateTime updatedAt;
}

