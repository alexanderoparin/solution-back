package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import ru.oparin.solution.dto.analytics.PromotionControlCapabilitiesDto;
import ru.oparin.solution.model.Cabinet;

import java.util.Locale;
import java.util.Optional;

/**
 * Управление доступностью start/pause РК с учётом read-only токена WB (данные в {@code cabinet_scope_status}).
 */
@Service
@RequiredArgsConstructor
public class PromotionCampaignControlWriteService {

    public static final String READ_ONLY_USER_MESSAGE = CabinetScopeStatusService.PROMOTION_READ_ONLY_WRITE_MESSAGE;

    private final CabinetScopeStatusService cabinetScopeStatusService;

    @Transactional
    public PromotionControlCapabilitiesDto getCapabilities(Cabinet cabinet) {
        if (cabinet == null || cabinet.getId() == null) {
            return PromotionControlCapabilitiesDto.allowed();
        }
        return cabinetScopeStatusService.getActivePromotionWriteBlock(cabinet.getId())
                .map(block -> new PromotionControlCapabilitiesDto(
                        false,
                        block.message(),
                        block.secondsRemaining(),
                        block.blockedUntil()))
                .orElseGet(PromotionControlCapabilitiesDto::allowed);
    }

    @Transactional
    public void ensureControlAllowed(Cabinet cabinet) {
        if (cabinet == null || cabinet.getId() == null) {
            return;
        }
        Optional<CabinetScopeStatusService.PromotionWriteBlock> block =
                cabinetScopeStatusService.getActivePromotionWriteBlock(cabinet.getId());
        if (block.isEmpty()) {
            return;
        }
        CabinetScopeStatusService.PromotionWriteBlock active = block.get();
        throw new CampaignControlWriteBlockedException(
                active.message(), active.secondsRemaining(), active.blockedUntil());
    }

    @Transactional
    public void recordReadOnlyTokenBlock(Long cabinetId) {
        cabinetScopeStatusService.recordPromotionWriteReadOnlyBlock(cabinetId);
    }

    @Transactional
    public void clearBlock(Long cabinetId) {
        cabinetScopeStatusService.clearPromotionWriteBlock(cabinetId);
    }

    public static boolean isReadOnlyTokenError(Throwable throwable) {
        if (throwable == null) {
            return false;
        }
        if (isReadOnlyTokenMessage(throwable.getMessage())) {
            return true;
        }
        Throwable cause = throwable.getCause();
        if (cause != null && cause != throwable) {
            return isReadOnlyTokenError(cause);
        }
        return false;
    }

    public static boolean isReadOnlyTokenError(HttpClientErrorException e) {
        if (e == null || e.getStatusCode().value() != 401) {
            return false;
        }
        return isReadOnlyTokenMessage(e.getResponseBodyAsString()) || isReadOnlyTokenMessage(e.getMessage());
    }

    private static boolean isReadOnlyTokenMessage(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("read-only") || lower.contains("readonly");
    }

    public static class CampaignControlWriteBlockedException extends RuntimeException {
        private final long nextAvailableInSeconds;
        private final java.time.LocalDateTime blockedUntil;

        public CampaignControlWriteBlockedException(
                String message,
                long nextAvailableInSeconds,
                java.time.LocalDateTime blockedUntil
        ) {
            super(message);
            this.nextAvailableInSeconds = nextAvailableInSeconds;
            this.blockedUntil = blockedUntil;
        }

        public long getNextAvailableInSeconds() {
            return nextAvailableInSeconds;
        }

        public java.time.LocalDateTime getBlockedUntil() {
            return blockedUntil;
        }
    }
}
