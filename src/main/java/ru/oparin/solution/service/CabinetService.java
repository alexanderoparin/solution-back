package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import ru.oparin.solution.dto.ManagedCabinetSortField;
import ru.oparin.solution.dto.cabinet.*;
import ru.oparin.solution.dto.wb.SellerInfoResponse;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.CabinetRepository;
import ru.oparin.solution.repository.UserRepository;
import ru.oparin.solution.repository.spec.CabinetManagedSpecifications;
import ru.oparin.solution.service.wb.Wb429RateLimitHeadersLogger;
import ru.oparin.solution.service.wb.WbCommonApiClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Сервис CRUD для кабинетов продавца.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CabinetService {

    private static final String CABINET_NOT_FOUND = "Кабинет не найден";
    private static final String CABINET_ACCESS_DENIED = "Нет доступа к данному кабинету";
    private static final String CABINET_NOT_SELLER_OWNED = "Кабинет не принадлежит селлеру";
    private static final String SELLER_ONLY_CREATE = "Только продавец может создавать кабинеты";
    private static final String SUBSCRIPTION_REQUIRED = "Оформите подписку для создания кабинета";
    private static final String WB_SELLER_INFO_ERROR = "Не удалось получить данные о продавце WB. Проверьте API ключ.";
    private static final String NAME_REQUIRED_WITHOUT_KEY = "Укажите название кабинета, если не задаёте API ключ WB.";
    /** Подсказка при лимите WB: создание кабинета без ключа по названию. */
    private static final String CABINET_CREATE_WITHOUT_KEY_HINT =
            " Можно создать кабинет, указав только название (не заполняя ключ WB).";
    private static final int MAX_CABINET_NAME_LENGTH = 255;

    private final CabinetRepository cabinetRepository;
    private final UserRepository userRepository;
    private final CabinetDeletionService cabinetDeletionService;
    private final SubscriptionAccessService subscriptionAccessService;
    private final CabinetScopeStatusService cabinetScopeStatusService;
    private final WbCommonApiClient wbCommonApiClient;

    /**
     * Список кабинетов пользователя (продавца), отсортированный по дате создания (новые первые).
     */
    @Transactional(readOnly = true)
    public List<CabinetDto> listByUserId(Long userId) {
        return findCabinetsByUserId(userId).stream().map(this::toDto).toList();
    }

    /**
     * Сортировка для {@link #pageManagedCabinets(User, Pageable, String)}.
     */
    public static Sort sortForManagedList(ManagedCabinetSortField field, Sort.Direction direction) {
        return switch (field) {
            case CABINET_ID -> Sort.by(new Order(direction, "id"));
            case CABINET_NAME -> Sort.by(new Order(direction, "name").ignoreCase());
            case SELLER_EMAIL -> Sort.by(new Order(direction, "user.email").ignoreCase());
            case LAST_DATA_UPDATE_AT -> Sort.by(
                    direction == Sort.Direction.ASC
                            ? Order.asc("lastDataUpdateAt").nullsLast()
                            : Order.desc("lastDataUpdateAt").nullsLast());
            case LAST_STOCKS_UPDATE_AT -> Sort.by(
                    direction == Sort.Direction.ASC
                            ? Order.asc("lastStocksUpdateAt").nullsLast()
                            : Order.desc("lastStocksUpdateAt").nullsLast());
        };
    }

    /**
     * Постраничный плоский список кабинетов (ADMIN / MANAGER) с поиском и сортировкой.
     */
    @Transactional(readOnly = true)
    public Page<ManagedCabinetRowDto> pageManagedCabinets(User currentUser, Pageable pageable, String search) {
        if (currentUser.getRole() != Role.ADMIN && currentUser.getRole() != Role.MANAGER) {
            throw new UserException(CABINET_ACCESS_DENIED, HttpStatus.FORBIDDEN);
        }
        var spec = CabinetManagedSpecifications.managedList(currentUser, search);
        return cabinetRepository.findAll(spec, pageable)
                .map(c -> ManagedCabinetRowDto.builder()
                        .sellerId(c.getUser().getId())
                        .sellerEmail(c.getUser().getEmail())
                        .cabinet(toDto(c))
                        .build());
    }

    /**
     * Кабинеты с API-ключом в зоне видимости ADMIN/MANAGER, по алфавиту названия (без учёта регистра).
     * Сортировка в памяти: при {@code SELECT DISTINCT} PostgreSQL не принимает {@code ORDER BY lower(name)},
     * если в списке выборки только {@code name} (генерация Hibernate для {@link Sort.Order#ignoreCase()}).
     */
    @Transactional(readOnly = true)
    public List<WorkContextCabinetDto> listWorkContextCabinets(User currentUser) {
        if (currentUser.getRole() != Role.ADMIN && currentUser.getRole() != Role.MANAGER) {
            throw new UserException(CABINET_ACCESS_DENIED, HttpStatus.FORBIDDEN);
        }
        List<Cabinet> list = cabinetRepository.findAll(
                CabinetManagedSpecifications.managedListWithApiKey(currentUser));
        return list.stream()
                .sorted(Comparator.comparing(Cabinet::getName, String.CASE_INSENSITIVE_ORDER))
                .map(c -> WorkContextCabinetDto.builder()
                        .cabinetId(c.getId())
                        .sellerId(c.getUser().getId())
                        .cabinetName(c.getName())
                        .sellerEmail(c.getUser().getEmail())
                        .lastDataUpdateAt(c.getLastDataUpdateAt())
                        .lastDataUpdateRequestedAt(c.getLastDataUpdateRequestedAt())
                        .build())
                .toList();
    }

    /**
     * Список сущностей кабинетов пользователя (для внутреннего использования в других сервисах).
     */
    @Transactional(readOnly = true)
    public List<Cabinet> findCabinetsByUserId(Long userId) {
        return cabinetRepository.findByUser_IdOrderByCreatedAtDesc(userId);
    }

    /**
     * Находит кабинет по ID с подгруженным пользователем.
     *
     * @param cabinetId ID кабинета
     * @return кабинет
     * @throws UserException 404 если кабинет не найден
     */
    @Transactional(readOnly = true)
    public Cabinet findByIdWithUserOrThrow(Long cabinetId) {
        return cabinetRepository.findByIdWithUser(cabinetId)
                .orElseThrow(() -> new UserException(CABINET_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    /**
     * Один кабинет по ID с проверкой, что он принадлежит пользователю.
     */
    @Transactional(readOnly = true)
    public CabinetDto getByIdAndUserId(Long cabinetId, Long userId) {
        Cabinet cabinet = findCabinetByIdAndUserId(cabinetId, userId);
        return toDto(cabinet);
    }

    /**
     * Создание кабинета. Доступно только для SELLER (создаётся для себя).
     */
    @Transactional
    public CabinetDto create(Long userId, CreateCabinetRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException("Пользователь не найден", HttpStatus.NOT_FOUND));
        if (user.getRole() != Role.SELLER) {
            throw new UserException(SELLER_ONLY_CREATE, HttpStatus.FORBIDDEN);
        }
        if (Boolean.FALSE.equals(user.getIsAgencyClient()) && !subscriptionAccessService.hasAccess(user)) {
            throw new UserException(SUBSCRIPTION_REQUIRED, HttpStatus.FORBIDDEN);
        }

        String trimmedApiKey = request.getApiKey() != null ? request.getApiKey().trim() : null;
        boolean hasApiKey = trimmedApiKey != null && !trimmedApiKey.isBlank();
        String trimmedName = request.getName() != null ? request.getName().trim() : null;
        boolean hasName = trimmedName != null && !trimmedName.isBlank();

        if (!hasApiKey && !hasName) {
            throw new UserException(NAME_REQUIRED_WITHOUT_KEY, HttpStatus.BAD_REQUEST);
        }

        final String cabinetName;
        if (!hasApiKey) {
            cabinetName = normalizeName(trimmedName);
            if (cabinetName == null) {
                throw new UserException(NAME_REQUIRED_WITHOUT_KEY, HttpStatus.BAD_REQUEST);
            }
        } else if (hasName) {
            assertSellerInfoOrThrow(trimmedApiKey);
            cabinetName = normalizeName(trimmedName);
        } else {
            cabinetName = resolveCabinetNameFromWb(trimmedApiKey);
        }

        Cabinet cabinet = Cabinet.builder()
                .user(user)
                .name(cabinetName)
                .apiKey(hasApiKey ? trimmedApiKey : null)
                .build();
        cabinet = cabinetRepository.save(cabinet);
        return toDto(cabinet);
    }

    /**
     * Обновление кабинета (имя и/или API ключ). Проверка принадлежности пользователю.
     */
    @Transactional
    public CabinetDto update(Long cabinetId, Long userId, UpdateCabinetRequest request) {
        Cabinet cabinet = findCabinetByIdAndUserId(cabinetId, userId);

        if (request.getName() != null && !request.getName().isBlank()) {
            cabinet.setName(request.getName().trim());
        }
        if (request.getApiKey() != null) {
            resetValidationAndSetApiKey(cabinet, request.getApiKey());
        }

        cabinet = cabinetRepository.save(cabinet);
        return toDto(cabinet);
    }

    /**
     * Обновление только API ключа кабинета (для админа/менеджера при редактировании кабинета селлера).
     * Проверка доступа выполняется в контроллере.
     */
    @Transactional
    public CabinetDto updateApiKey(Long cabinetId, String apiKey) {
        Cabinet cabinet = findByIdWithUserOrThrow(cabinetId);
        resetValidationAndSetApiKey(cabinet, apiKey);
        cabinet = cabinetRepository.save(cabinet);
        return toDto(cabinet);
    }

    /**
     * Сбрасывает статус валидации и устанавливает новый API ключ кабинета.
     */
    public void resetValidationAndSetApiKey(Cabinet cabinet, String apiKey) {
        cabinet.setIsValid(null);
        cabinet.setValidationError(null);
        cabinet.setLastValidatedAt(null);
        cabinet.setApiKey(apiKey != null ? apiKey.trim() : null);
    }

    /**
     * Удаление кабинета и всех связанных данных.
     * Каждый шаг выполняется в своей транзакции (REQUIRES_NEW), чтобы не держать одну большую транзакцию.
     */
    public void delete(Long cabinetId, Long userId) {
        Cabinet cabinet = findCabinetByIdAndUserId(cabinetId, userId);
        log.info("[Удаление кабинета] Начало: «{}» (cabinetId={})", cabinet.getName(), cabinetId);

        cabinetDeletionService.deleteStepStatisticsAndArticles(cabinetId);
        cabinetDeletionService.deleteStepCampaigns(cabinetId);
        cabinetDeletionService.deleteStepPriceHistory(cabinetId);
        cabinetDeletionService.deleteStepStocks(cabinetId);
        cabinetDeletionService.deleteStepBarcodes(cabinetId);
        cabinetDeletionService.deleteStepCardAnalytics(cabinetId);
        cabinetDeletionService.deleteStepProductCards(cabinetId);
        cabinetDeletionService.deleteStepArticleNotes(cabinetId);
        cabinetDeletionService.deleteStepCampaignNoteFiles(cabinetId);
        cabinetDeletionService.deleteStepCampaignNotes(cabinetId);
        log.info("[Удаление кабинета]   Запись кабинета");
        deleteCabinet(cabinet);

        log.info("[Удаление кабинета] Готово: «{}» (cabinetId={})", cabinet.getName(), cabinetId);
    }

    /**
     * Проверяет, что текущий пользователь (ADMIN или MANAGER) имеет право запускать обновление данных для кабинета.
     * Кабинет должен принадлежать селлеру; для MANAGER — селлер должен быть в подчинении (owner = currentUser).
     *
     * @param cabinetId ID кабинета
     * @param currentUser текущий пользователь (ADMIN или MANAGER)
     * @throws UserException 404 если кабинет не найден, 403 если нет доступа
     */
    @Transactional(readOnly = true)
    public void validateCabinetAccessForUpdate(Long cabinetId, User currentUser) {
        Cabinet cabinet = findByIdWithUserOrThrow(cabinetId);
        validateSellerCabinetAccess(cabinet.getUser(), currentUser, false);
    }

    /**
     * Проверяет право запуска обновления остатков по кабинету.
     * Разрешено: владелец кабинета (SELLER), ADMIN или MANAGER (с доступом к селлеру).
     */
    @Transactional(readOnly = true)
    public void validateCabinetAccessForStocksUpdate(Long cabinetId, User currentUser) {
        Cabinet cabinet = findByIdWithUserOrThrow(cabinetId);
        validateSellerCabinetAccess(cabinet.getUser(), currentUser, true);
    }

    /**
     * Кабинет по умолчанию для пользователя (последний созданный).
     */
    @Transactional(readOnly = true)
    public Optional<Cabinet> findDefaultByUserId(Long userId) {
        return cabinetRepository.findDefaultByUserId(userId);
    }

    /**
     * Кабинет по умолчанию для пользователя или исключение.
     *
     * @throws IllegalStateException если кабинет не найден
     */
    @Transactional(readOnly = true)
    public Cabinet findDefaultByUserIdOrThrow(Long userId) {
        return cabinetRepository.findDefaultByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("У продавца нет кабинета по умолчанию"));
    }

    /**
     * Все кабинеты с API-ключом и активным продавцем указанной роли (для планировщиков и синхронизации).
     */
    @Transactional(readOnly = true)
    public List<Cabinet> findCabinetsWithApiKeyAndUser(Role role) {
        return cabinetRepository.findCabinetsWithApiKeyAndUser(role);
    }

    /**
     * Все кабинеты с API-ключом и активным SELLER, принадлежащим указанному владельцу (MANAGER).
     */
    @Transactional(readOnly = true)
    public List<Cabinet> findCabinetsWithApiKeyAndUserAndOwnerId(Role role, Long ownerId) {
        return cabinetRepository.findCabinetsWithApiKeyAndUserAndOwnerId(role, ownerId);
    }

    /**
     * Сохраняет кабинет (создание или обновление).
     */
    @Transactional
    public Cabinet save(Cabinet cabinet) {
        return cabinetRepository.save(cabinet);
    }

    /**
     * Находит кабинет по ID (без проверки владельца).
     */
    @Transactional(readOnly = true)
    public Optional<Cabinet> findById(Long cabinetId) {
        return cabinetRepository.findById(cabinetId);
    }

    /**
     * Проверяет, что кабинет принадлежит пользователю.
     */
    @Transactional(readOnly = true)
    public boolean existsByIdAndUser_Id(Long cabinetId, Long userId) {
        return cabinetRepository.existsByIdAndUser_Id(cabinetId, userId);
    }

    /**
     * Удаляет запись кабинета из БД (вызывается после очистки связанных данных).
     */
    @Transactional
    public void deleteCabinet(Cabinet cabinet) {
        cabinetRepository.delete(cabinet);
    }

    /**
     * Возвращает кабинет по ID, если он принадлежит пользователю.
     */
    public Cabinet findCabinetByIdAndUserId(Long cabinetId, Long userId) {
        if (!cabinetRepository.existsByIdAndUser_Id(cabinetId, userId)) {
            throw new UserException("Кабинет не найден или доступ запрещён", HttpStatus.NOT_FOUND);
        }
        return cabinetRepository.findById(cabinetId)
                .orElseThrow(() -> new UserException(CABINET_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    private void validateSellerCabinetAccess(User seller, User currentUser, boolean allowSellerSelf) {
        if (seller.getRole() != Role.SELLER) {
            throw new UserException(CABINET_NOT_SELLER_OWNED, HttpStatus.FORBIDDEN);
        }

        if (currentUser.getRole() == Role.ADMIN) {
            return;
        }

        if (currentUser.getRole() == Role.MANAGER && isSellerOwnedByManager(seller, currentUser.getId())) {
            return;
        }

        if (allowSellerSelf && currentUser.getRole() == Role.SELLER && seller.getId().equals(currentUser.getId())) {
            return;
        }

        if (allowSellerSelf && currentUser.getRole() == Role.WORKER && isWorkerOwnedBySeller(currentUser, seller.getId())) {
            return;
        }

        throw new UserException(CABINET_ACCESS_DENIED, HttpStatus.FORBIDDEN);
    }

    private boolean isSellerOwnedByManager(User seller, Long managerId) {
        return seller.getOwner() != null && seller.getOwner().getId().equals(managerId);
    }

    private boolean isWorkerOwnedBySeller(User worker, Long sellerId) {
        return worker.getOwner() != null
                && worker.getOwner().getRole() == Role.SELLER
                && worker.getOwner().getId().equals(sellerId);
    }

    private void assertSellerInfoOrThrow(String apiKey) {
        try {
            wbCommonApiClient.getSellerInfo(apiKey);
        } catch (HttpClientErrorException e) {
            throw sellerInfoHttpException(e);
        } catch (RestClientException e) {
            throw new UserException(WB_SELLER_INFO_ERROR, HttpStatus.BAD_REQUEST);
        }
    }

    private String resolveCabinetNameFromWb(String apiKey) {
        try {
            SellerInfoResponse sellerInfo = wbCommonApiClient.getSellerInfo(apiKey);
            String nameFromWb = normalizeName(firstNotBlank(sellerInfo.getName(), sellerInfo.getTradeMark()));
            if (nameFromWb != null) {
                return nameFromWb;
            }
            throw new UserException(
                    "WB не вернул название продавца. Укажите название кабинета вручную.",
                    HttpStatus.BAD_REQUEST);
        } catch (HttpClientErrorException e) {
            throw sellerInfoHttpException(e);
        } catch (UserException e) {
            throw e;
        } catch (RestClientException e) {
            throw new UserException(WB_SELLER_INFO_ERROR, HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            throw new UserException(WB_SELLER_INFO_ERROR, HttpStatus.BAD_REQUEST);
        }
    }

    private UserException sellerInfoHttpException(HttpClientErrorException e) {
        int status = e.getStatusCode().value();
        if (status == HttpStatus.TOO_MANY_REQUESTS.value()) {
            Integer retry = Wb429RateLimitHeadersLogger.parseRetryAfterSeconds(e);
            if (retry != null && retry > 0) {
                return new UserException(
                        "Превышен лимит запросов к WB API. Повторите попытку примерно через: "
                                + formatSecondsAsHoursMinutesSeconds(retry) + "."
                                + CABINET_CREATE_WITHOUT_KEY_HINT,
                        HttpStatus.TOO_MANY_REQUESTS,
                        retry);
            }
            return new UserException(
                    "Превышен лимит запросов к WB API. Повторите попытку позже." + CABINET_CREATE_WITHOUT_KEY_HINT,
                    HttpStatus.TOO_MANY_REQUESTS);
        }
        if (status == HttpStatus.UNAUTHORIZED.value()) {
            return new UserException(
                    "API ключ WB невалиден или истёк. Проверьте ключ.",
                    HttpStatus.BAD_REQUEST);
        }
        return new UserException(WB_SELLER_INFO_ERROR + " (HTTP " + status + ")", HttpStatus.BAD_REQUEST);
    }

    /**
     * Человекочитаемый интервал из секунд (заголовок WB X-Ratelimit-Retry): часы, минуты, секунды — только ненулевые части.
     */
    private static String formatSecondsAsHoursMinutesSeconds(int totalSeconds) {
        if (totalSeconds <= 0) {
            return "0 с";
        }
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        List<String> parts = new ArrayList<>(3);
        if (hours > 0) {
            parts.add(hours + " ч");
        }
        if (minutes > 0) {
            parts.add(minutes + " мин");
        }
        if (seconds > 0 || parts.isEmpty()) {
            parts.add(seconds + " с");
        }
        return String.join(" ", parts);
    }

    private String normalizeName(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String trimmedValue = rawValue.trim();
        if (trimmedValue.isEmpty()) {
            return null;
        }
        return trimmedValue.length() <= MAX_CABINET_NAME_LENGTH
                ? trimmedValue
                : trimmedValue.substring(0, MAX_CABINET_NAME_LENGTH);
    }

    private String firstNotBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private CabinetDto toDto(Cabinet cabinet) {
        CabinetDto.ApiKeyInfo apiKeyInfo = toApiKeyInfo(cabinet);
        List<CabinetDto.ScopeStatusDto> scopeStatuses = cabinetScopeStatusService.getStatusesByCabinetId(cabinet.getId())
                .stream()
                .map(s -> CabinetDto.ScopeStatusDto.builder()
                        .category(s.category())
                        .categoryDisplayName(s.categoryDisplayName())
                        .lastCheckedAt(s.lastCheckedAt())
                        .success(s.success())
                        .errorMessage(s.errorMessage())
                        .build())
                .toList();
        return CabinetDto.builder()
                .id(cabinet.getId())
                .name(cabinet.getName())
                .createdAt(cabinet.getCreatedAt())
                .updatedAt(cabinet.getUpdatedAt())
                .lastDataUpdateAt(cabinet.getLastDataUpdateAt())
                .lastDataUpdateRequestedAt(cabinet.getLastDataUpdateRequestedAt())
                .lastStocksUpdateAt(cabinet.getLastStocksUpdateAt())
                .apiKey(apiKeyInfo)
                .scopeStatuses(scopeStatuses)
                .build();
    }

    private CabinetDto.ApiKeyInfo toApiKeyInfo(Cabinet cabinet) {
        if (cabinet.getApiKey() == null && cabinet.getIsValid() == null) {
            return null;
        }
        return CabinetDto.ApiKeyInfo.builder()
                .apiKey(cabinet.getApiKey())
                .isValid(cabinet.getIsValid())
                .lastValidatedAt(cabinet.getLastValidatedAt())
                .validationError(cabinet.getValidationError())
                .lastDataUpdateAt(cabinet.getLastDataUpdateAt())
                .lastDataUpdateRequestedAt(cabinet.getLastDataUpdateRequestedAt())
                .lastStocksUpdateAt(cabinet.getLastStocksUpdateAt())
                .build();
    }
}
