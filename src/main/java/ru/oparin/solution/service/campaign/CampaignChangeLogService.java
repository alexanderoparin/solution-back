package ru.oparin.solution.service.campaign;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.analytics.manage.CampaignChangeLogEntryDto;
import ru.oparin.solution.model.CampaignChangeLog;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.CampaignChangeLogRepository;

import java.util.List;

/**
 * Журнал изменений настроек рекламной кампании.
 */
@Service
@RequiredArgsConstructor
public class CampaignChangeLogService {

    private final CampaignChangeLogRepository changeLogRepository;

    @Transactional
    public void log(Long campaignId, Long cabinetId, User user, String message) {
        changeLogRepository.save(CampaignChangeLog.builder()
                .campaignId(campaignId)
                .cabinetId(cabinetId)
                .user(user)
                .message(message)
                .build());
    }

    @Transactional(readOnly = true)
    public List<CampaignChangeLogEntryDto> recent(Long campaignId, Long cabinetId, int limit) {
        Page<CampaignChangeLog> page = changeLogRepository.findByCampaignIdAndCabinetIdOrderByCreatedAtDesc(
                campaignId, cabinetId, PageRequest.of(0, limit));
        return page.getContent().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Page<CampaignChangeLogEntryDto> page(Long campaignId, Long cabinetId, int page, int size) {
        return changeLogRepository.findByCampaignIdAndCabinetIdOrderByCreatedAtDesc(
                        campaignId, cabinetId, PageRequest.of(page, size))
                .map(this::toDto);
    }

    private CampaignChangeLogEntryDto toDto(CampaignChangeLog log) {
        String userDisplay = "Auto";
        if (log.getUser() != null) {
            userDisplay = log.getUser().getEmail() != null ? log.getUser().getEmail() : String.valueOf(log.getUser().getId());
        }
        return CampaignChangeLogEntryDto.builder()
                .createdAt(log.getCreatedAt())
                .userDisplay(userDisplay)
                .message(log.getMessage())
                .build();
    }
}
