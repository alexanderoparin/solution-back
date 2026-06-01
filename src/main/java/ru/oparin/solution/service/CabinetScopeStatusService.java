package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.CabinetScopeStatus;
import ru.oparin.solution.repository.CabinetRepository;
import ru.oparin.solution.repository.CabinetScopeStatusRepository;
import ru.oparin.solution.service.wb.WbApiCategory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

/**
 * Фиксация результата доступа к категориям WB API по кабинету.
 * Вызывается после каждого блока обновлений (success) или при 401 (failure).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CabinetScopeStatusService {

    /** Длительность блокировки start/pause РК после read-only токена WB. */
    public static final Duration PROMOTION_WRITE_BLOCK_DURATION = Duration.ofHours(1);

    public static final String PROMOTION_READ_ONLY_WRITE_MESSAGE =
            "API-ключ кабинета только для чтения. Для запуска и паузы РК создайте в ЛК WB токен "
                    + "с правом изменения в категории «Продвижение» и обновите ключ в профиле.";

    private final CabinetScopeStatusRepository repository;
    private final CabinetRepository cabinetRepository;
    private final ObjectProvider<WbApiKeyService> wbApiKeyServiceProvider;

    /**
     * Записать успешное завершение блока обновлений по категории для кабинета.
     */
    @Transactional
    public void recordSuccess(Long cabinetId, WbApiCategory category) {
        LocalDateTime now = LocalDateTime.now();
        CabinetScopeStatus status = findOrCreate(cabinetId, category);
        status.setLastCheckedAt(now);
        status.setSuccess(true);
        status.setErrorMessage(null);
        repository.save(status);
        log.debug("Кабинет {}: категория {} — успех", cabinetId, category.getDisplayName());
    }

    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;

    /**
     * Записать неуспех (401 или иная ошибка доступа) по категории для кабинета.
     * Для «Маркетплейс» при 401 сразу обновляет статус через /ping (без записи сырого ответа синка).
     */
    @Transactional
    public void recordFailure(Long cabinetId, WbApiCategory category, String errorMessage) {
        if (category == WbApiCategory.MARKETPLACE && isUnauthorizedScopeError(errorMessage)) {
            log.debug("Кабинет {}: категория {} — 401, обновляем статус через /ping",
                    cabinetId, category.getDisplayName());
            wbApiKeyServiceProvider.getObject().pingCategoryForCabinet(cabinetId, WbApiCategory.MARKETPLACE);
            return;
        }
        saveFailure(cabinetId, category, errorMessage);
    }

    /**
     * Записать неуспех без повторного /ping (используется самим {@link WbApiKeyService} после /ping).
     */
    @Transactional
    public void recordFailureFromPing(Long cabinetId, WbApiCategory category, String errorMessage) {
        saveFailure(cabinetId, category, errorMessage);
    }

    private void saveFailure(Long cabinetId, WbApiCategory category, String errorMessage) {
        LocalDateTime now = LocalDateTime.now();
        CabinetScopeStatus status = findOrCreate(cabinetId, category);
        status.setLastCheckedAt(now);
        status.setSuccess(false);
        status.setErrorMessage(sanitizeErrorMessage(errorMessage));
        repository.save(status);
        log.debug("Кабинет {}: категория {} — неуспех: {}", cabinetId, category.getDisplayName(), errorMessage);
    }

    private static boolean isUnauthorizedScopeError(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return false;
        }
        String lower = errorMessage.toLowerCase(Locale.ROOT);
        return lower.contains("401")
                || lower.contains("unauthorized")
                || lower.contains("token scope not allowed");
    }

    /**
     * Убирает &lt;EOL&gt; из сообщения, при возможности извлекает "detail" из JSON ответа WB, обрезает длину.
     */
    private static String sanitizeErrorMessage(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String withoutEol = raw.replace("<EOL>", " ").replace("\n", " ");
        String detail = extractJsonDetail(withoutEol);
        String toStore = (detail != null ? detail : withoutEol).trim();
        if (toStore.length() > MAX_ERROR_MESSAGE_LENGTH) {
            toStore = toStore.substring(0, MAX_ERROR_MESSAGE_LENGTH) + "…";
        }
        return toStore.isEmpty() ? null : toStore;
    }

    /** Извлекает значение поля "detail" из JSON-подобной строки (первое вхождение "detail": "значение"). */
    private static String extractJsonDetail(String s) {
        int idx = s.indexOf("\"detail\"");
        if (idx < 0) {
            return null;
        }
        int colon = s.indexOf(':', idx);
        if (colon < 0) {
            return null;
        }
        int start = s.indexOf('"', colon + 1);
        if (start < 0) {
            return null;
        }
        int end = s.indexOf('"', start + 1);
        if (end < 0) {
            return null;
        }
        return s.substring(start + 1, end).trim();
    }

    /**
     * Категории WB API, по которым показываем статус в профиле (те, что используем в синхронизации).
     */
    private static final List<WbApiCategory> DISPLAYED_CATEGORIES = List.of(
            WbApiCategory.CONTENT,
            WbApiCategory.ANALYTICS,
            WbApiCategory.PRICES_AND_DISCOUNTS,
            WbApiCategory.STATISTICS,
            WbApiCategory.PROMOTION,
            WbApiCategory.FEEDBACKS_AND_QUESTIONS,
            WbApiCategory.MARKETPLACE
    );

    /**
     * Статусы по всем отображаемым категориям для кабинета (для DTO).
     * Для категорий, по которым ещё не было проверки, возвращаются null в lastCheckedAt и success.
     */
    @Transactional(readOnly = true)
    public List<CabinetScopeStatusDto> getStatusesByCabinetId(Long cabinetId) {
        Map<WbApiCategory, CabinetScopeStatusDto> fromDb = repository.findByCabinetIdOrderByCategory(cabinetId)
                .stream()
                .collect(toMap(CabinetScopeStatus::getCategory, s -> toDto(s)));
        return DISPLAYED_CATEGORIES.stream()
                .map(cat -> fromDb.getOrDefault(cat, new CabinetScopeStatusDto(
                        cat.name(),
                        cat.getDisplayName(),
                        null,
                        null,
                        null,
                        null,
                        false)))
                .collect(Collectors.toList());
    }

    private static CabinetScopeStatusDto toDto(CabinetScopeStatus s) {
        LocalDateTime until = s.getWriteBlockedUntil();
        boolean writeReadOnly = until != null && LocalDateTime.now().isBefore(until);
        return new CabinetScopeStatusDto(
                s.getCategory().name(),
                s.getCategory().getDisplayName(),
                s.getLastCheckedAt(),
                s.getSuccess(),
                s.getErrorMessage(),
                until,
                writeReadOnly);
    }

    /**
     * Активная блокировка записи по категории «Продвижение» (start/pause РК).
     */
    @Transactional
    public Optional<PromotionWriteBlock> getActivePromotionWriteBlock(Long cabinetId) {
        return getActiveWriteBlock(cabinetId, WbApiCategory.PROMOTION, true);
    }

    /**
     * Блокирует start/pause РК на {@link #PROMOTION_WRITE_BLOCK_DURATION} после read-only токена.
     */
    @Transactional
    public void recordPromotionWriteReadOnlyBlock(Long cabinetId) {
        CabinetScopeStatus status = findOrCreate(cabinetId, WbApiCategory.PROMOTION);
        LocalDateTime now = LocalDateTime.now();
        status.setLastCheckedAt(now);
        status.setWriteBlockedUntil(now.plus(PROMOTION_WRITE_BLOCK_DURATION));
        status.setErrorMessage(PROMOTION_READ_ONLY_WRITE_MESSAGE);
        repository.save(status);
        log.debug("Кабинет {}: блокировка записи PROMOTION до {}", cabinetId, status.getWriteBlockedUntil());
    }

    /**
     * Снимает блокировку записи по «Продвижению» (успешный start/pause или смена API-ключа).
     */
    @Transactional
    public void clearPromotionWriteBlock(Long cabinetId) {
        repository.findByCabinetIdAndCategory(cabinetId, WbApiCategory.PROMOTION).ifPresent(status -> {
            status.setWriteBlockedUntil(null);
            if (isPromotionReadOnlyErrorMessage(status.getErrorMessage())) {
                status.setErrorMessage(null);
            }
            repository.save(status);
        });
    }

    private static boolean isPromotionReadOnlyErrorMessage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("read-only")
                || lower.contains("readonly")
                || lower.contains("только для чтения");
    }

    private Optional<PromotionWriteBlock> getActiveWriteBlock(
            Long cabinetId,
            WbApiCategory category,
            boolean clearIfExpired
    ) {
        Optional<CabinetScopeStatus> row = repository.findByCabinetIdAndCategory(cabinetId, category);
        if (row.isEmpty()) {
            return Optional.empty();
        }
        CabinetScopeStatus status = row.get();
        LocalDateTime until = status.getWriteBlockedUntil();
        if (until == null) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now();
        if (!now.isBefore(until)) {
            if (clearIfExpired) {
                status.setWriteBlockedUntil(null);
                if (isPromotionReadOnlyErrorMessage(status.getErrorMessage())) {
                    status.setErrorMessage(null);
                }
                repository.save(status);
            }
            return Optional.empty();
        }
        long seconds = ChronoUnit.SECONDS.between(now, until);
        String message = status.getErrorMessage() != null ? status.getErrorMessage() : PROMOTION_READ_ONLY_WRITE_MESSAGE;
        return Optional.of(new PromotionWriteBlock(until, message, Math.max(1L, seconds)));
    }

    /**
     * Блокировка операций записи по категории WB API.
     */
    public record PromotionWriteBlock(LocalDateTime blockedUntil, String message, long secondsRemaining) {
    }

    private CabinetScopeStatus findOrCreate(Long cabinetId, WbApiCategory category) {
        Optional<CabinetScopeStatus> existing = repository.findByCabinetIdAndCategory(cabinetId, category);
        if (existing.isPresent()) {
            return existing.get();
        }
        Cabinet cabinet = cabinetRepository.findById(cabinetId)
                .orElseThrow(() -> new IllegalArgumentException("Кабинет не найден: " + cabinetId));
        CabinetScopeStatus status = CabinetScopeStatus.builder()
                .cabinet(cabinet)
                .category(category)
                .lastCheckedAt(LocalDateTime.now())
                .success(false)
                .build();
        return repository.save(status);
    }

    /**
     * DTO одной записи для ответа API.
     */
    public record CabinetScopeStatusDto(
            String category,
            String categoryDisplayName,
            LocalDateTime lastCheckedAt,
            Boolean success,
            String errorMessage,
            /** До этого времени запись по категории заблокирована (read-only токен). */
            LocalDateTime writeBlockedUntil,
            /** Активна блокировка записи (только чтение для start/pause РК). */
            boolean writeReadOnly
    ) {}
}
