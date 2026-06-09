package ru.oparin.solution.service.campaign;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.analytics.manage.BalanceRefreshResponseDto;
import ru.oparin.solution.dto.analytics.manage.BalanceSourceOptionDto;
import ru.oparin.solution.dto.analytics.manage.BalanceSourcesResponseDto;
import ru.oparin.solution.dto.wb.PromotionBalanceResponse;
import ru.oparin.solution.exception.WbRateLimitDeferException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.CabinetPromotionBalanceCache;
import ru.oparin.solution.model.CabinetTokenType;
import ru.oparin.solution.model.WbApiEventType;
import ru.oparin.solution.repository.CabinetPromotionBalanceCacheRepository;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.events.WbEventRateLimitService;
import ru.oparin.solution.service.wb.WbPromotionApiClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Кэш баланса продвижения WB по кабинету с соблюдением лимитов API.
 */
@Service
@RequiredArgsConstructor
public class CabinetPromotionBalanceCacheService {

    private final CabinetPromotionBalanceCacheRepository cacheRepository;
    private final CabinetService cabinetService;
    private final WbPromotionApiClient promotionApiClient;
    private final WbEventRateLimitService rateLimitService;

    /**
     * Без readOnly: при пустом кэше идёт запрос в WB и результат сохраняется в БД.
     */
    @Transactional
    public BalanceSourcesResponseDto getBalanceSources(Long cabinetId, boolean tryRefreshIfMissing) {
        BalanceRefreshResponseDto result = resolveSources(cabinetId, tryRefreshIfMissing, false);
        return result.getSources();
    }

    @Transactional
    public BalanceRefreshResponseDto refreshBalance(Long cabinetId) {
        return resolveSources(cabinetId, true, true);
    }

    private BalanceRefreshResponseDto resolveSources(Long cabinetId, boolean tryRefreshIfMissing, boolean forceRefresh) {
        Cabinet cabinet = cabinetService.findById(cabinetId)
                .orElseThrow(() -> new IllegalArgumentException("Кабинет не найден"));
        if (cabinet.getApiKey() == null || cabinet.getApiKey().isBlank()) {
            return BalanceRefreshResponseDto.builder()
                    .sources(emptySources(null, false))
                    .refreshed(false)
                    .stale(true)
                    .message("API-ключ кабинета не задан")
                    .build();
        }

        Optional<CabinetPromotionBalanceCache> cached = cacheRepository.findById(cabinetId);
        CabinetTokenType tokenType = cabinet.getTokenType() != null ? cabinet.getTokenType() : CabinetTokenType.BASIC;

        if (!forceRefresh && cached.isPresent()) {
            boolean stale = !isCacheFresh(cached.get(), tokenType);
            return toRefreshResponse(cached.get(), false, stale, null, null);
        }
        if (!forceRefresh && !tryRefreshIfMissing) {
            return BalanceRefreshResponseDto.builder()
                    .sources(emptySources(null, true))
                    .refreshed(false)
                    .stale(true)
                    .message("Баланс ещё не загружался")
                    .build();
        }

        LocalDateTime deferUntil = rateLimitService.peekDeferUntil(cabinetId, WbApiEventType.PROMOTION_BALANCE, tokenType);
        if (deferUntil != null) {
            long sec = Math.max(1, Duration.between(LocalDateTime.now(), deferUntil).getSeconds());
            if (cached.isPresent()) {
                return toRefreshResponse(cached.get(), false, true, sec,
                        "Следующее обновление баланса будет доступно через " + formatSeconds(sec));
            }
            return BalanceRefreshResponseDto.builder()
                    .sources(emptySources(null, true))
                    .refreshed(false)
                    .stale(true)
                    .nextAvailableInSeconds(sec)
                    .message("Лимит WB: повторите через " + formatSeconds(sec))
                    .build();
        }

        try {
            PromotionBalanceResponse balance = promotionApiClient.getBalance(cabinet.getApiKey());
            CabinetPromotionBalanceCache saved = saveCache(cabinetId, balance, null);
            return toRefreshResponse(saved, true, false, null, null);
        } catch (WbRateLimitDeferException e) {
            long sec = Math.max(1, Duration.between(LocalDateTime.now(), e.getDeferUntil()).getSeconds());
            if (cached.isPresent()) {
                return toRefreshResponse(cached.get(), false, true, sec,
                        "Лимит WB: повторите через " + formatSeconds(sec));
            }
            return BalanceRefreshResponseDto.builder()
                    .sources(emptySources(null, true))
                    .refreshed(false)
                    .stale(true)
                    .nextAvailableInSeconds(sec)
                    .message("Лимит WB: повторите через " + formatSeconds(sec))
                    .build();
        } catch (Exception e) {
            if (cached.isPresent()) {
                return toRefreshResponse(cached.get(), false, true, null, e.getMessage());
            }
            return BalanceRefreshResponseDto.builder()
                    .sources(emptySources(null, true))
                    .refreshed(false)
                    .stale(true)
                    .message(e.getMessage())
                    .build();
        }
    }

    private boolean isCacheFresh(CabinetPromotionBalanceCache cache, CabinetTokenType tokenType) {
        if (cache.getFetchedAt() == null) {
            return false;
        }
        long delayMs = WbApiEventType.PROMOTION_BALANCE.getRequestDelayMs(tokenType);
        return cache.getFetchedAt().plusNanos(delayMs * 1_000_000L).isAfter(LocalDateTime.now());
    }

    /** Сохраняет ответ WB в {@code cabinet_promotion_balance_cache}. */
    private CabinetPromotionBalanceCache saveCache(Long cabinetId, PromotionBalanceResponse balance, String error) {
        CabinetPromotionBalanceCache entity = cacheRepository.findById(cabinetId)
                .orElseGet(() -> CabinetPromotionBalanceCache.builder().cabinetId(cabinetId).build());
        if (balance != null) {
            entity.setBalanceRub(balance.getBalance());
            entity.setNetRub(balance.getNet());
            entity.setBonusRub(balance.getBonus());
            entity.setFetchedAt(LocalDateTime.now());
            entity.setFetchError(null);
        } else {
            entity.setFetchError(error);
        }
        return cacheRepository.save(entity);
    }

    private BalanceRefreshResponseDto toRefreshResponse(
            CabinetPromotionBalanceCache cache,
            boolean refreshed,
            boolean stale,
            Long nextAvailableInSeconds,
            String message
    ) {
        return BalanceRefreshResponseDto.builder()
                .sources(mapSources(cache, stale))
                .refreshed(refreshed)
                .stale(stale)
                .fetchedAt(cache.getFetchedAt())
                .nextAvailableInSeconds(nextAvailableInSeconds)
                .message(message)
                .build();
    }

    private BalanceSourcesResponseDto mapSources(CabinetPromotionBalanceCache cache, boolean stale) {
        List<BalanceSourceOptionDto> sources = new ArrayList<>();
        sources.add(BalanceSourceOptionDto.builder().type(0).label("Счёт").availableRub(cache.getBalanceRub()).build());
        sources.add(BalanceSourceOptionDto.builder().type(1).label("Баланс").availableRub(cache.getNetRub()).build());
        sources.add(BalanceSourceOptionDto.builder().type(3).label("Бонусы").availableRub(cache.getBonusRub()).build());
        return BalanceSourcesResponseDto.builder()
                .sources(sources)
                .fetchedAt(cache.getFetchedAt())
                .stale(stale)
                .build();
    }

    private BalanceSourcesResponseDto emptySources(LocalDateTime fetchedAt, boolean stale) {
        return BalanceSourcesResponseDto.builder()
                .sources(List.of())
                .fetchedAt(fetchedAt)
                .stale(stale)
                .build();
    }

    private static String formatSeconds(long seconds) {
        if (seconds >= 3600) {
            return (seconds / 3600) + " ч";
        }
        if (seconds >= 60) {
            return (seconds / 60) + " мин";
        }
        return seconds + " с";
    }
}
