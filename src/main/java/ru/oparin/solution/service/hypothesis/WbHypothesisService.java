package ru.oparin.solution.service.hypothesis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.analytics.manage.CampaignCabinetResolveDto;
import ru.oparin.solution.dto.hypothesis.*;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.UserRepository;
import ru.oparin.solution.repository.WbHypothesisRepository;
import ru.oparin.solution.repository.WbProductCardRepository;
import ru.oparin.solution.service.EntityCabinetResolveSupport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CRUD и расчёт результатов гипотез.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WbHypothesisService {

    private final WbHypothesisRepository hypothesisRepository;
    private final WbProductCardRepository productCardRepository;
    private final UserRepository userRepository;
    private final WbHypothesisMetricsService metricsService;
    private final EntityCabinetResolveSupport entityCabinetResolveSupport;
    private final ObjectMapper objectMapper;

    /**
     * Кабинет гипотезы для deep-link.
     */
    @Transactional(readOnly = true)
    public Optional<CampaignCabinetResolveDto> resolveAccessibleCabinet(Long id, User currentUser) {
        if (id == null || currentUser == null) {
            return Optional.empty();
        }
        WbHypothesis hypothesis = hypothesisRepository.findById(id).orElse(null);
        if (hypothesis == null) {
            return Optional.empty();
        }
        return entityCabinetResolveSupport.resolveIfAccessible(
                hypothesis.getCabinetId(),
                currentUser,
                CabinetAccessSection.PRODUCTS
        );
    }

    /**
     * Список гипотез с фильтрами.
     */
    @Transactional
    public List<WbHypothesisDto> list(
            Long cabinetId,
            String search,
            String displayStatus,
            Long nmId,
            String criterion,
            LocalDate periodFrom,
            LocalDate periodTo
    ) {
        Specification<WbHypothesis> spec = buildListSpec(cabinetId, search, nmId, criterion, periodFrom, periodTo);
        List<WbHypothesis> rows = hypothesisRepository.findAll(spec);
        rows.sort(Comparator.comparing(WbHypothesis::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));

        Map<Long, WbProductCard> cards = loadCards(cabinetId, rows);
        Map<Long, String> authorNames = loadAuthorNames(rows);

        List<WbHypothesisDto> result = new ArrayList<>();
        for (WbHypothesis row : rows) {
            WbHypothesisDto dto = toDto(row, cards.get(row.getNmId()), authorNames.get(row.getCreatedByUserId()), true);
            maybePersistAutoVerdict(row, dto);
            if (displayStatus != null && !displayStatus.isBlank()
                    && !displayStatus.equalsIgnoreCase(dto.getDisplayStatus())) {
                continue;
            }
            result.add(dto);
        }
        return result;
    }

    /**
     * Деталка гипотезы.
     */
    @Transactional
    public WbHypothesisDto get(Long cabinetId, Long id) {
        WbHypothesis row = requireHypothesis(cabinetId, id);
        WbProductCard card = productCardRepository.findByNmIdAndCabinet_Id(row.getNmId(), cabinetId).orElse(null);
        String author = row.getCreatedByUserId() == null
                ? null
                : userRepository.findById(row.getCreatedByUserId()).map(this::formatUserName).orElse(null);
        WbHypothesisDto dto = toDto(row, card, author, true);
        dto.setDynamics(buildDynamics(row, readCriteria(row.getCriteriaJson()), dto.getBaselineFrom()));
        maybePersistAutoVerdict(row, dto);
        return dto;
    }

    private List<WbHypothesisDailyPointDto> buildDynamics(
            WbHypothesis row,
            List<WbHypothesisCriterion> criteria,
            LocalDate baselineFrom
    ) {
        List<WbHypothesisDailyPointDto> points = new ArrayList<>();
        for (WbHypothesisMetricsService.WbHypothesisDailyPoint point : metricsService.dailySeries(
                row.getCabinetId(), row.getNmId(), baselineFrom, row.getCheckTo(), criteria)) {
            String period = point.date().isBefore(row.getCheckFrom()) ? "BASELINE" : "CHECK";
            Map<String, BigDecimal> values = new LinkedHashMap<>();
            for (WbHypothesisCriterion criterion : criteria) {
                BigDecimal value = point.values().get(criterion);
                if (value != null) {
                    values.put(criterion.getKey(), value);
                }
            }
            points.add(WbHypothesisDailyPointDto.builder()
                    .date(point.date())
                    .period(period)
                    .values(values)
                    .build());
        }
        return points;
    }

    /**
     * Создание гипотезы.
     */
    @Transactional
    public WbHypothesisDto create(Long cabinetId, Long userId, WbHypothesisUpsertRequest request) {
        validateRequest(cabinetId, request);
        WbHypothesis entity = WbHypothesis.builder()
                .cabinetId(cabinetId)
                .nmId(request.getNmId())
                .title(request.getTitle().trim())
                .description(trimToNull(request.getDescription()))
                .checkFrom(request.getCheckFrom())
                .checkTo(request.getCheckTo())
                .workflowStatus(parseWorkflow(request.getWorkflowStatus()))
                .criteriaJson(writeCriteria(parseCriteria(request.getCriteria())))
                .createdByUserId(userId)
                .verdictManual(false)
                .build();
        entity = hypothesisRepository.save(entity);
        return get(cabinetId, entity.getId());
    }

    /**
     * Обновление гипотезы (в т.ч. после запуска теста).
     */
    @Transactional
    public WbHypothesisDto update(Long cabinetId, Long id, WbHypothesisUpsertRequest request) {
        validateRequest(cabinetId, request);
        WbHypothesis entity = requireHypothesis(cabinetId, id);
        entity.setNmId(request.getNmId());
        entity.setTitle(request.getTitle().trim());
        entity.setDescription(trimToNull(request.getDescription()));
        entity.setCheckFrom(request.getCheckFrom());
        entity.setCheckTo(request.getCheckTo());
        entity.setWorkflowStatus(parseWorkflow(request.getWorkflowStatus()));
        entity.setCriteriaJson(writeCriteria(parseCriteria(request.getCriteria())));
        if (!entity.isVerdictManual()) {
            entity.setVerdict(null);
        }
        hypothesisRepository.save(entity);
        return get(cabinetId, id);
    }

    /**
     * Ручная установка итога Успешно/Неуспешно.
     */
    @Transactional
    public WbHypothesisDto setVerdict(Long cabinetId, Long id, WbHypothesisVerdictRequest request) {
        WbHypothesis entity = requireHypothesis(cabinetId, id);
        WbHypothesisVerdict verdict = parseVerdict(request.getVerdict());
        entity.setVerdict(verdict);
        entity.setVerdictManual(true);
        hypothesisRepository.save(entity);
        return get(cabinetId, id);
    }

    /**
     * Удаление одной гипотезы.
     */
    @Transactional
    public void delete(Long cabinetId, Long id) {
        WbHypothesis entity = requireHypothesis(cabinetId, id);
        hypothesisRepository.delete(entity);
    }

    /**
     * Массовое удаление.
     */
    @Transactional
    public long bulkDelete(Long cabinetId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        List<WbHypothesis> found = hypothesisRepository.findByCabinetIdAndIdIn(cabinetId, ids);
        hypothesisRepository.deleteAll(found);
        return found.size();
    }

    private void maybePersistAutoVerdict(WbHypothesis row, WbHypothesisDto dto) {
        if (row.isVerdictManual() || row.getVerdict() != null) {
            return;
        }
        if (dto.getDisplayStatus() == null) {
            return;
        }
        if (WbHypothesisDisplayStatus.SUCCESS.name().equals(dto.getDisplayStatus())) {
            row.setVerdict(WbHypothesisVerdict.SUCCESS);
            hypothesisRepository.save(row);
            dto.setVerdict(WbHypothesisVerdict.SUCCESS.name());
        } else if (WbHypothesisDisplayStatus.FAILURE.name().equals(dto.getDisplayStatus())) {
            row.setVerdict(WbHypothesisVerdict.FAILURE);
            hypothesisRepository.save(row);
            dto.setVerdict(WbHypothesisVerdict.FAILURE.name());
        }
    }

    private WbHypothesisDto toDto(
            WbHypothesis row,
            WbProductCard card,
            String authorName,
            boolean withMetrics
    ) {
        List<WbHypothesisCriterion> criteria = readCriteria(row.getCriteriaJson());
        LocalDate baselineTo = row.getCheckFrom().minusDays(1);
        long days = ChronoUnit.DAYS.between(row.getCheckFrom(), row.getCheckTo()) + 1;
        LocalDate baselineFrom = baselineTo.minusDays(days - 1);

        List<WbHypothesisCriterionValueDto> criterionResults = List.of();
        String resultSummary = null;
        if (withMetrics) {
            criterionResults = metricsService.compareCriteria(
                    row.getCabinetId(),
                    row.getNmId(),
                    baselineFrom,
                    baselineTo,
                    row.getCheckFrom(),
                    row.getCheckTo(),
                    criteria
            );
            resultSummary = buildResultSummary(criterionResults);
        }

        WbHypothesisDisplayStatus displayStatus = resolveDisplayStatus(row, criterionResults);

        return WbHypothesisDto.builder()
                .id(row.getId())
                .cabinetId(row.getCabinetId())
                .nmId(row.getNmId())
                .nmName(card != null ? card.getTitle() : null)
                .nmPhotoUrl(card != null ? firstNonBlank(card.getPhotoC246x328(), card.getPhotoTm()) : null)
                .vendorCode(card != null ? card.getVendorCode() : null)
                .categoryName(card != null ? card.getSubjectName() : null)
                .title(row.getTitle())
                .description(row.getDescription())
                .checkFrom(row.getCheckFrom())
                .checkTo(row.getCheckTo())
                .baselineFrom(baselineFrom)
                .baselineTo(baselineTo)
                .workflowStatus(row.getWorkflowStatus().name())
                .displayStatus(displayStatus.name())
                .verdict(row.getVerdict() != null ? row.getVerdict().name() : null)
                .verdictManual(row.isVerdictManual())
                .criteria(criteria.stream().map(WbHypothesisCriterion::getKey).toList())
                .criterionResults(criterionResults)
                .resultSummary(resultSummary)
                .createdByUserId(row.getCreatedByUserId())
                .createdByName(authorName)
                .createdAt(row.getCreatedAt())
                .updatedAt(row.getUpdatedAt())
                .build();
    }

    private WbHypothesisDisplayStatus resolveDisplayStatus(
            WbHypothesis row,
            List<WbHypothesisCriterionValueDto> criterionResults
    ) {
        if (row.isVerdictManual() && row.getVerdict() != null) {
            return row.getVerdict() == WbHypothesisVerdict.SUCCESS
                    ? WbHypothesisDisplayStatus.SUCCESS
                    : WbHypothesisDisplayStatus.FAILURE;
        }
        if (row.getVerdict() != null) {
            return row.getVerdict() == WbHypothesisVerdict.SUCCESS
                    ? WbHypothesisDisplayStatus.SUCCESS
                    : WbHypothesisDisplayStatus.FAILURE;
        }

        LocalDate today = LocalDate.now();
        if (row.getWorkflowStatus() == WbHypothesisWorkflowStatus.PREPARATION && today.isBefore(row.getCheckFrom())) {
            return WbHypothesisDisplayStatus.PREPARATION;
        }
        if (row.getWorkflowStatus() == WbHypothesisWorkflowStatus.PREPARATION) {
            return WbHypothesisDisplayStatus.PREPARATION;
        }
        if (today.isBefore(row.getCheckFrom())) {
            return WbHypothesisDisplayStatus.PREPARATION;
        }
        if (!today.isAfter(row.getCheckTo())) {
            return WbHypothesisDisplayStatus.IN_PROGRESS;
        }

        long comparable = criterionResults.stream().filter(r -> r.getImproved() != null).count();
        if (comparable == 0) {
            return WbHypothesisDisplayStatus.WAITING_DATA;
        }
        long improved = criterionResults.stream().filter(r -> Boolean.TRUE.equals(r.getImproved())).count();
        long worsened = criterionResults.stream().filter(r -> Boolean.FALSE.equals(r.getImproved())).count();
        if (improved > worsened) {
            return WbHypothesisDisplayStatus.SUCCESS;
        }
        return WbHypothesisDisplayStatus.FAILURE;
    }

    private String buildResultSummary(List<WbHypothesisCriterionValueDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (WbHypothesisCriterionValueDto row : rows) {
            if (row.getCheck() == null || row.getChange() == null) {
                continue;
            }
            String arrow = Boolean.TRUE.equals(row.getImproved())
                    ? "↑"
                    : Boolean.FALSE.equals(row.getImproved()) ? "↓" : "→";
            parts.add(row.getLabel() + " " + formatNumber(row.getCheck()) + " " + arrow
                    + " " + formatSigned(row.getChange()));
        }
        if (parts.isEmpty()) {
            return null;
        }
        return String.join("; ", parts);
    }

    private static String formatNumber(BigDecimal value) {
        if (value == null) {
            return "—";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private static String formatSigned(BigDecimal value) {
        if (value == null) {
            return "—";
        }
        String plain = value.stripTrailingZeros().toPlainString();
        if (value.compareTo(BigDecimal.ZERO) > 0) {
            return "+" + plain;
        }
        return plain;
    }

    private Specification<WbHypothesis> buildListSpec(
            Long cabinetId,
            String search,
            Long nmId,
            String criterion,
            LocalDate periodFrom,
            LocalDate periodTo
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("cabinetId"), cabinetId));
            if (nmId != null) {
                predicates.add(cb.equal(root.get("nmId"), nmId));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("description"), "")), pattern),
                        cb.like(root.get("nmId").as(String.class), "%" + search.trim() + "%")
                ));
            }
            if (criterion != null && !criterion.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("criteriaJson")),
                        "%" + criterion.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (periodFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("checkTo"), periodFrom));
            }
            if (periodTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("checkFrom"), periodTo));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void validateRequest(Long cabinetId, WbHypothesisUpsertRequest request) {
        if (request.getCheckTo().isBefore(request.getCheckFrom())) {
            throw new IllegalArgumentException("Дата окончания периода проверки раньше начала");
        }
        productCardRepository.findByNmIdAndCabinet_Id(request.getNmId(), cabinetId)
                .orElseThrow(() -> new IllegalArgumentException("Артикул не найден в кабинете"));
        parseWorkflow(request.getWorkflowStatus());
        parseCriteria(request.getCriteria());
    }

    private WbHypothesis requireHypothesis(Long cabinetId, Long id) {
        return hypothesisRepository.findByIdAndCabinetId(id, cabinetId)
                .orElseThrow(() -> new IllegalArgumentException("Гипотеза не найдена"));
    }

    private Map<Long, WbProductCard> loadCards(Long cabinetId, List<WbHypothesis> rows) {
        Set<Long> nmIds = rows.stream().map(WbHypothesis::getNmId).collect(Collectors.toSet());
        if (nmIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, WbProductCard> map = new HashMap<>();
        for (Long nmId : nmIds) {
            productCardRepository.findByNmIdAndCabinet_Id(nmId, cabinetId).ifPresent(c -> map.put(nmId, c));
        }
        return map;
    }

    private Map<Long, String> loadAuthorNames(List<WbHypothesis> rows) {
        Set<Long> userIds = rows.stream()
                .map(WbHypothesis::getCreatedByUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> map = new HashMap<>();
        for (User user : userRepository.findAllById(userIds)) {
            map.put(user.getId(), formatUserName(user));
        }
        return map;
    }

    private String formatUserName(User user) {
        if (user.getName() != null && !user.getName().isBlank()) {
            return user.getName();
        }
        return user.getEmail();
    }

    private List<WbHypothesisCriterion> parseCriteria(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("Выберите хотя бы один критерий");
        }
        LinkedHashSet<WbHypothesisCriterion> set = new LinkedHashSet<>();
        for (String key : raw) {
            WbHypothesisCriterion criterion = WbHypothesisCriterion.fromKey(key);
            if (criterion == null) {
                throw new IllegalArgumentException("Неизвестный критерий: " + key);
            }
            set.add(criterion);
        }
        return List.copyOf(set);
    }

    private String writeCriteria(List<WbHypothesisCriterion> criteria) {
        try {
            return objectMapper.writeValueAsString(
                    criteria.stream().map(WbHypothesisCriterion::getKey).toList());
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось сериализовать критерии", e);
        }
    }

    private List<WbHypothesisCriterion> readCriteria(String json) {
        try {
            List<String> keys = objectMapper.readValue(json, new TypeReference<>() {
            });
            return parseCriteria(keys);
        } catch (Exception e) {
            log.warn("Не удалось прочитать критерии гипотезы: {}", e.getMessage());
            return List.of();
        }
    }

    private static WbHypothesisWorkflowStatus parseWorkflow(String raw) {
        try {
            return WbHypothesisWorkflowStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new IllegalArgumentException("Некорректный статус: " + raw);
        }
    }

    private static WbHypothesisVerdict parseVerdict(String raw) {
        try {
            return WbHypothesisVerdict.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new IllegalArgumentException("Некорректный итог: " + raw);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }
}
