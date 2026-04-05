package ru.oparin.solution.dto.notes;

import lombok.*;

import java.time.LocalDateTime;

/**
 * DTO файла заметки РК.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignNoteFileDto {
    private Long id;
    private String fileName;
    private Long fileSize;
    private String mimeType;
    private LocalDateTime uploadedAt;
}
