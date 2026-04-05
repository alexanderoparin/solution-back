package ru.oparin.solution.dto.notes;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO для заметки к рекламной кампании (РК).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignNoteDto {
    private Long id;
    private Long campaignId;
    private Long userId;
    private String userEmail;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<CampaignNoteFileDto> files;
}
