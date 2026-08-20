package ru.oparin.solution.service.campaign;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.analytics.manage.WbCampaignChangeLogEntryDto;
import ru.oparin.solution.model.User;
import ru.oparin.solution.model.WbCampaignChangeLog;
import ru.oparin.solution.repository.WbCampaignChangeLogRepository;

import java.util.List;
import java.util.Set;

/**
 * Журнал изменений настроек рекламной кампании.
 */
@Service
@RequiredArgsConstructor
public class WbCampaignChangeLogService {

    private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(10, 20, 50, 100);

    private final WbCampaignChangeLogRepository changeLogRepository;

    @Transactional
    public void log(Long campaignId, Long cabinetId, User user, String message) {
        changeLogRepository.save(WbCampaignChangeLog.builder()
                .campaignId(campaignId)
                .cabinetId(cabinetId)
                .user(user)
                .message(message)
                .build());
    }

    @Transactional(readOnly = true)
    public List<WbCampaignChangeLogEntryDto> recent(Long campaignId, Long cabinetId, int limit) {
        Page<WbCampaignChangeLog> page = changeLogRepository.findByCampaignIdAndCabinetIdOrderByCreatedAtDesc(
                campaignId, cabinetId, PageRequest.of(0, limit));
        return page.getContent().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Page<WbCampaignChangeLogEntryDto> page(Long campaignId, Long cabinetId, int page, int size) {
        int resolvedSize = ALLOWED_PAGE_SIZES.contains(size) ? size : 10;
        int resolvedPage = Math.max(0, page);
        return changeLogRepository.findByCampaignIdAndCabinetIdOrderByCreatedAtDesc(
                        campaignId, cabinetId, PageRequest.of(resolvedPage, resolvedSize))
                .map(this::toDto);
    }

    private WbCampaignChangeLogEntryDto toDto(WbCampaignChangeLog log) {
        String userDisplay = "Auto";
        if (log.getUser() != null) {
            userDisplay = log.getUser().getEmail() != null ? log.getUser().getEmail() : String.valueOf(log.getUser().getId());
        }
        return WbCampaignChangeLogEntryDto.builder()
                .createdAt(log.getCreatedAt())
                .userDisplay(userDisplay)
                .message(log.getMessage())
                .build();
    }
}
