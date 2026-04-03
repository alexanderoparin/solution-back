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
import ru.oparin.solution.dto.ManagedCabinetSortField;
import ru.oparin.solution.dto.cabinet.CabinetDto;
import ru.oparin.solution.dto.cabinet.CreateCabinetRequest;
import ru.oparin.solution.dto.cabinet.ManagedCabinetRowDto;
import ru.oparin.solution.dto.cabinet.UpdateCabinetRequest;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.CabinetRepository;
import ru.oparin.solution.repository.UserRepository;
import ru.oparin.solution.repository.spec.CabinetManagedSpecifications;

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

    private final CabinetRepository cabinetRepository;
    private final UserRepository userRepository;
    private final CabinetDeletionService cabinetDeletionService;
    private final SubscriptionAccessService subscriptionAccessService;
    private final CabinetScopeStatusService cabinetScopeStatusService;

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

        Cabinet cabinet = Cabinet.builder()
                .user(user)
                .name(request.getName().trim())
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

        throw new UserException(CABINET_ACCESS_DENIED, HttpStatus.FORBIDDEN);
    }

    private boolean isSellerOwnedByManager(User seller, Long managerId) {
        return seller.getOwner() != null && seller.getOwner().getId().equals(managerId);
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
