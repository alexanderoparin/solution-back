package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.CabinetScopeStatus;
import ru.oparin.solution.repository.CabinetRepository;
import ru.oparin.solution.repository.CabinetScopeStatusRepository;
import ru.oparin.solution.service.wb.WbApiCategory;

import java.time.LocalDateTime;
import java.util.List;
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

    private final CabinetScopeStatusRepository repository;
    private final CabinetRepository cabinetRepository;

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

    /**
     * Записать неуспех (401 или иная ошибка доступа) по категории для кабинета.
     */
    @Transactional
    public void recordFailure(Long cabinetId, WbApiCategory category, String errorMessage) {
        LocalDateTime now = LocalDateTime.now();
        CabinetScopeStatus status = findOrCreate(cabinetId, category);
        status.setLastCheckedAt(now);
        status.setSuccess(false);
        status.setErrorMessage(errorMessage != null && errorMessage.length() > 500
                ? errorMessage.substring(0, 500) + "…" : errorMessage);
        repository.save(status);
        log.debug("Кабинет {}: категория {} — неуспех: {}", cabinetId, category.getDisplayName(), errorMessage);
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
                        null)))
                .collect(Collectors.toList());
    }

    private static CabinetScopeStatusDto toDto(CabinetScopeStatus s) {
        return new CabinetScopeStatusDto(
                s.getCategory().name(),
                s.getCategory().getDisplayName(),
                s.getLastCheckedAt(),
                s.getSuccess(),
                s.getErrorMessage());
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
            String errorMessage
    ) {}
}
