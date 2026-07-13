package ru.oparin.solution.dto.cabinet;

import lombok.Builder;
import ru.oparin.solution.model.CabinetAccessSection;

import java.util.List;

@Builder
public record CabinetInvitationPreviewDto(
        String cabinetName,
        String inviterName,
        String inviterEmail,
        List<CabinetAccessSection> sections,
        boolean expired,
        boolean alreadyAccepted,
        String email
) {
}
