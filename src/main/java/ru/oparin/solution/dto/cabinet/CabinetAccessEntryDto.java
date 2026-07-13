package ru.oparin.solution.dto.cabinet;

import lombok.Builder;
import ru.oparin.solution.model.CabinetAccessInvitationStatus;
import ru.oparin.solution.model.CabinetAccessSection;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record CabinetAccessEntryDto(
        Long id,
        String kind,
        String userName,
        String userEmail,
        List<CabinetAccessSection> sections,
        LocalDateTime accessFrom,
        LocalDateTime accessUntil,
        String grantedByLabel,
        LocalDateTime grantedAt,
        CabinetAccessInvitationStatus invitationStatus,
        String statusLabel
) {
}
