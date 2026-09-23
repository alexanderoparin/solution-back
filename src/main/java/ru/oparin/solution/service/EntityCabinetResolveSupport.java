package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.analytics.manage.CampaignCabinetResolveDto;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.CabinetAccessSection;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.model.User;

import java.util.Optional;

/**
 * Общая проверка доступа и сбор DTO кабинета для автопереключения по прямой ссылке.
 */
@Service
@RequiredArgsConstructor
public class EntityCabinetResolveSupport {

    private final CabinetAccessService cabinetAccessService;
    private final CabinetService cabinetService;

    /**
     * Если у пользователя есть доступ хотя бы к одному из разделов — возвращает кабинет владельца сущности.
     *
     * @param cabinet кабинет сущности
     * @param currentUser текущий пользователь
     * @param sections допустимые разделы (OWNER всегда проходит)
     * @return DTO или empty
     */
    @Transactional(readOnly = true)
    public Optional<CampaignCabinetResolveDto> resolveIfAccessible(
            Cabinet cabinet,
            User currentUser,
            CabinetAccessSection... sections
    ) {
        if (cabinet == null || currentUser == null || cabinet.getUser() == null) {
            return Optional.empty();
        }
        Long cabinetId = cabinet.getId();
        if (!hasAccess(currentUser, cabinetId, sections)) {
            return Optional.empty();
        }
        return Optional.of(CampaignCabinetResolveDto.builder()
                .cabinetId(cabinetId)
                .sellerId(cabinet.getUser().getId())
                .cabinetName(cabinet.getName())
                .build());
    }

    /**
     * То же по ID кабинета (для сущностей без связи ManyToOne, например А/Б-тест).
     */
    @Transactional(readOnly = true)
    public Optional<CampaignCabinetResolveDto> resolveIfAccessible(
            Long cabinetId,
            User currentUser,
            CabinetAccessSection... sections
    ) {
        if (cabinetId == null) {
            return Optional.empty();
        }
        return cabinetService.findById(cabinetId)
                .flatMap(cabinet -> resolveIfAccessible(cabinet, currentUser, sections));
    }

    private boolean hasAccess(User currentUser, Long cabinetId, CabinetAccessSection... sections) {
        if (currentUser.getRole() == Role.ADMIN) {
            return true;
        }
        if (cabinetAccessService.isCabinetOwner(currentUser, cabinetId)) {
            return true;
        }
        if (sections == null) {
            return false;
        }
        for (CabinetAccessSection section : sections) {
            if (section != null && cabinetAccessService.hasSectionAccess(currentUser, cabinetId, section)) {
                return true;
            }
        }
        return false;
    }
}
