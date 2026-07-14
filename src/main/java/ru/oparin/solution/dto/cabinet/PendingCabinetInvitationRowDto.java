package ru.oparin.solution.dto.cabinet;

import lombok.Builder;
import ru.oparin.solution.model.CabinetAccessSection;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Ожидающее принятия приглашение в кабинет (для профиля приглашённого).
 */
@Builder
public record PendingCabinetInvitationRowDto(
        String token,
        Long cabinetId,
        String cabinetName,
        String inviterName,
        String inviterEmail,
        List<CabinetAccessSection> sections,
        LocalDateTime accessUntil,
        LocalDateTime expiresAt,
        LocalDateTime createdAt
) {
}
