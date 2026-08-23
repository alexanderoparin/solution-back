package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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
import ru.oparin.solution.dto.wb.WbSellerInfoResponse;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.CabinetAccessGrantRepository;
import ru.oparin.solution.repository.CabinetIntegrationRepository;
import ru.oparin.solution.repository.CabinetRepository;
import ru.oparin.solution.repository.UserRepository;
import ru.oparin.solution.repository.spec.CabinetManagedSpecifications;
import ru.oparin.solution.service.ozon.OzonPerformanceApiClient;
import ru.oparin.solution.service.ozon.OzonSellerApiClient;
import ru.oparin.solution.service.wb.Wb429RateLimitHeadersLogger;
import ru.oparin.solution.service.wb.WbCommonApiClient;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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
    private static final String WB_SELLER_INFO_ERROR = "Не удалось получить данные о продавце WB. Проверьте API-токен.";
    private static final String OZON_SELLER_INFO_ERROR =
            "Не удалось проверить доступ к Ozon Seller API. Проверьте Client-Id и Api-Key.";
    private static final String API_KEY_ALREADY_USED =
            "Этот API-ключ Wildberries уже привязан к другому кабинету в системе. "
                    + "Откройте существующий кабинет или укажите другой токен.";
    private static final int MAX_CABINET_NAME_LENGTH = 255;

    private final CabinetRepository cabinetRepository;
    private final CabinetAccessGrantRepository grantRepository;
    private final UserRepository userRepository;
    private final CabinetAccessService cabinetAccessService;
    private final CabinetDeletionService cabinetDeletionService;
    private final CabinetScopeStatusService cabinetScopeStatusService;
    private final WbCommonApiClient wbCommonApiClient;
    private final OzonSellerApiClient ozonSellerApiClient;
    private final OzonPerformanceApiClient ozonPerformanceApiClient;
    private final CabinetBillingService cabinetBillingService;
    private final CabinetIntegrationMirrorService cabinetIntegrationMirrorService;
    private final CabinetIntegrationRepository cabinetIntegrationRepository;

    /**
     * Список кабинетов пользователя (продавца), отсортированный по дате создания (новые первые).
     */
    @Transactional(readOnly = true)
    public List<CabinetDto> listByUserId(Long userId) {
        return findCabinetsByUserId(userId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<CabinetDto> listByUserId(Long userId, boolean maskApiKey) {
        return findCabinetsByUserId(userId).stream().map(c -> toDto(c, maskApiKey)).toList();
    }

    /**
     * Собственные кабинеты и кабинеты с активным grant (для аналитики и выбора в шапке).
     */
    @Transactional(readOnly = true)
    public List<CabinetDto> listAccessibleForUser(User user) {
        List<CabinetDto> result = new ArrayList<>(listByUserId(user.getId(), false));
        Set<Long> ids = result.stream().map(CabinetDto::getId).collect(Collectors.toSet());
        LocalDateTime now = LocalDateTime.now();
        for (var grant : grantRepository.findActiveGrantedForUser(
                user.getId(), CabinetAccessGrantStatus.ACTIVE, now)) {
            Long cabinetId = grant.getCabinet().getId();
            if (!ids.contains(cabinetId)) {
                Cabinet grantedCabinet = grant.getCabinet();
                cabinetIntegrationMirrorService.overlayOntoCabinet(grantedCabinet);
                result.add(toDto(grantedCabinet, true));
                ids.add(cabinetId);
            }
        }
        return result;
    }

    /**
     * Сортировка для {@link #pageManagedCabinets(User, Pageable, String, boolean, ru.oparin.solution.model.MarketplaceType)}.
     */
    public static Sort sortForManagedList(ManagedCabinetSortField field, Sort.Direction direction) {
        return switch (field) {
            case CABINET_ID -> Sort.by(new Order(direction, "id"));
            case CABINET_NAME -> Sort.by(new Order(direction, "name").ignoreCase());
            case SELLER_EMAIL -> Sort.by(new Order(direction, "user.email").ignoreCase());
            case LAST_DATA_UPDATE_AT -> Sort.by(
                    direction == Sort.Direction.ASC
                            ? Order.asc("syncState.lastDataUpdateAt").nullsLast()
                            : Order.desc("syncState.lastDataUpdateAt").nullsLast());
            case LAST_STOCKS_UPDATE_AT -> Sort.by(
                    direction == Sort.Direction.ASC
                            ? Order.asc("syncState.lastStocksUpdateAt").nullsLast()
                            : Order.desc("syncState.lastStocksUpdateAt").nullsLast());
        };
    }

    /**
     * Постраничный плоский список кабинетов (ADMIN / MANAGER) с поиском и сортировкой.
     */
    @Transactional(readOnly = true)
    public Page<ManagedCabinetRowDto> pageManagedCabinets(
            User currentUser,
            Pageable pageable,
            String search,
            boolean onlyActiveUsers,
            MarketplaceType marketplaceType
    ) {
        if (currentUser.getRole() != Role.ADMIN) {
            throw new UserException(CABINET_ACCESS_DENIED, HttpStatus.FORBIDDEN);
        }
        var spec = CabinetManagedSpecifications.managedList(currentUser, search, onlyActiveUsers, marketplaceType);
        Page<Cabinet> cabinetPage = cabinetRepository.findAll(spec, pageable);
        List<Cabinet> cabinets = cabinetPage.getContent();
        cabinetIntegrationMirrorService.overlayOntoCabinets(cabinets);
        if (cabinets.isEmpty()) {
            return cabinetPage.map(c -> ManagedCabinetRowDto.builder()
                    .sellerId(c.getUser().getId())
                    .sellerEmail(c.getUser().getEmail())
                    .agencyManaged(Boolean.TRUE.equals(c.getUser().getAgencyManaged()))
                    .managerEmails(List.of())
                    .cabinet(toDto(c))
                    .build());
        }

        List<Long> cabinetIds = cabinets.stream()
                .map(Cabinet::getId)
                .toList();

        Map<Long, List<String>> managerEmailsByCabinetId = fetchActiveGrantEmailsByCabinetIds(cabinetIds);

        List<ManagedCabinetRowDto> rows = cabinets.stream()
                .map(c -> ManagedCabinetRowDto.builder()
                        .sellerId(c.getUser().getId())
                        .sellerEmail(c.getUser().getEmail())
                        .agencyManaged(Boolean.TRUE.equals(c.getUser().getAgencyManaged()))
                        .managerEmails(managerEmailsByCabinetId.getOrDefault(c.getId(), List.of()))
                        .cabinet(toDto(c))
                        .build())
                .toList();

        return new PageImpl<>(rows, pageable, cabinetPage.getTotalElements());
    }

    /**
     * Email пользователей с активным доступом к кабинетам ({@code cabinet_access_grants}).
     */
    private Map<Long, List<String>> fetchActiveGrantEmailsByCabinetIds(List<Long> cabinetIds) {
        if (cabinetIds == null || cabinetIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<String>> result = new HashMap<>();
        grantRepository.findActiveGranteeEmailsByCabinetIds(
                cabinetIds,
                CabinetAccessGrantStatus.ACTIVE,
                LocalDateTime.now()
        ).forEach(row -> {
            String email = row.getGranteeEmail();
            if (email == null || email.isBlank()) {
                return;
            }
            List<String> emails = result.computeIfAbsent(row.getCabinetId(), __ -> new ArrayList<>());
            if (!emails.contains(email)) {
                emails.add(email);
            }
        });
        return result;
    }

    /**
     * Кабинеты с API-ключом в зоне видимости ADMIN/MANAGER, по алфавиту названия (без учёта регистра).
     * Сортировка в памяти: при {@code SELECT DISTINCT} PostgreSQL не принимает {@code ORDER BY lower(name)},
     * если в списке выборки только {@code name} (генерация Hibernate для {@link Sort.Order#ignoreCase()}).
     */
    @Transactional(readOnly = true)
    public List<WorkContextCabinetDto> listWorkContextCabinets(User currentUser) {
        if (currentUser.getRole() != Role.ADMIN) {
            throw new UserException(CABINET_ACCESS_DENIED, HttpStatus.FORBIDDEN);
        }
        List<Cabinet> list = cabinetRepository.findAll(
                CabinetManagedSpecifications.managedListWithApiKey(currentUser));
        cabinetIntegrationMirrorService.overlayOntoCabinets(list);
        return list.stream()
                .sorted(Comparator.comparing(Cabinet::getName, String.CASE_INSENSITIVE_ORDER))
                .map(c -> WorkContextCabinetDto.builder()
                        .cabinetId(c.getId())
                        .sellerId(c.getUser().getId())
                        .cabinetName(c.getName())
                        .sellerEmail(c.getUser().getEmail())
                        .marketplaceType(c.getMarketplaceType() != null ? c.getMarketplaceType() : MarketplaceType.WB)
                        .lastDataUpdateAt(c.getLastDataUpdateAt())
                        .lastDataUpdateRequestedAt(c.getLastDataUpdateRequestedAt())
                        .tokenType(CabinetTokenType.effective(c.getTokenType()))
                        .build())
                .toList();
    }

    /**
     * Список сущностей кабинетов пользователя (для внутреннего использования в других сервисах).
     */
    @Transactional(readOnly = true)
    public List<Cabinet> findCabinetsByUserId(Long userId) {
        List<Cabinet> cabinets = cabinetRepository.findByUser_IdOrderByCreatedAtDesc(userId);
        cabinetIntegrationMirrorService.overlayOntoCabinets(cabinets);
        return cabinets;
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
        Cabinet cabinet = cabinetRepository.findByIdWithUser(cabinetId)
                .orElseThrow(() -> new UserException(CABINET_NOT_FOUND, HttpStatus.NOT_FOUND));
        cabinetIntegrationMirrorService.overlayOntoCabinet(cabinet);
        return cabinet;
    }

    /**
     * Кабинет по ID с JOIN FETCH user (для планировщиков / async без open-in-view).
     */
    @Transactional(readOnly = true)
    public Optional<Cabinet> findByIdWithUser(Long cabinetId) {
        Optional<Cabinet> cabinet = cabinetRepository.findByIdWithUser(cabinetId);
        cabinet.ifPresent(cabinetIntegrationMirrorService::overlayOntoCabinet);
        return cabinet;
    }

    /**
     * Один кабинет по ID с проверкой, что он принадлежит пользователю.
     */
    @Transactional(readOnly = true)
    public CabinetDto getByIdAndUserId(Long cabinetId, Long userId) {
        Cabinet cabinet = findCabinetByIdAndUserId(cabinetId, userId);
        return toDto(cabinet);
    }

    @Transactional(readOnly = true)
    public CabinetDto getByIdAndUserId(Long cabinetId, Long userId, boolean maskApiKey) {
        Cabinet cabinet = findCabinetByIdAndUserId(cabinetId, userId);
        return toDto(cabinet, maskApiKey);
    }

    /**
     * Создание кабинета. Доступно только для SELLER (создаётся для себя).
     */
    @Transactional
    public CabinetDto create(Long userId, CreateCabinetRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException("Пользователь не найден", HttpStatus.NOT_FOUND));
        if (user.getRole() != Role.USER && user.getRole() != Role.ADMIN) {
            throw new UserException(SELLER_ONLY_CREATE, HttpStatus.FORBIDDEN);
        }

        MarketplaceType marketplaceType = request.getMarketplaceType() != null
                ? request.getMarketplaceType()
                : MarketplaceType.WB;

        if (marketplaceType == MarketplaceType.OZON) {
            return createOzonCabinet(user, request);
        }
        return createWbCabinet(user, request);
    }

    private CabinetDto createWbCabinet(User user, CreateCabinetRequest request) {
        if (request.getApiKey() == null || request.getApiKey().isBlank()) {
            throw new UserException("Укажите API-токен WB", HttpStatus.BAD_REQUEST);
        }
        if (request.getTokenType() == null) {
            throw new UserException("Укажите тип токена", HttpStatus.BAD_REQUEST);
        }

        String trimmedApiKey = request.getApiKey().trim();
        String trimmedName = request.getName() != null ? request.getName().trim() : null;
        boolean hasName = trimmedName != null && !trimmedName.isBlank();

        final String cabinetName;
        if (hasName) {
            assertSellerInfoOrThrow(trimmedApiKey);
            cabinetName = normalizeName(trimmedName);
        } else {
            cabinetName = resolveCabinetNameFromWb(trimmedApiKey);
        }

        assertApiKeyNotUsedByAnotherCabinet(null, trimmedApiKey);

        Cabinet cabinet = Cabinet.builder()
                .user(user)
                .marketplaceType(MarketplaceType.WB)
                .name(cabinetName)
                .apiKey(trimmedApiKey)
                .tokenType(request.getTokenType())
                .build();
        cabinet = cabinetRepository.save(cabinet);
        cabinetBillingService.initializeCabinetBilling(cabinet);
        cabinetIntegrationMirrorService.mirrorFromCabinet(cabinet);
        return toDto(cabinet);
    }

    private CabinetDto createOzonCabinet(User user, CreateCabinetRequest request) {
        if (request.getOzonClientId() == null || request.getOzonClientId().isBlank()) {
            throw new UserException("Укажите Client-Id Ozon", HttpStatus.BAD_REQUEST);
        }
        if (request.getApiKey() == null || request.getApiKey().isBlank()) {
            throw new UserException("Укажите Api-Key Ozon", HttpStatus.BAD_REQUEST);
        }

        String clientId = request.getOzonClientId().trim();
        if (!clientId.matches("\\d+")) {
            throw new UserException("Client-Id Ozon должен быть положительным числом", HttpStatus.BAD_REQUEST);
        }
        String apiKey = request.getApiKey().trim();
        String trimmedName = request.getName() != null ? request.getName().trim() : null;
        if (trimmedName == null || trimmedName.isBlank()) {
            throw new UserException("Укажите название кабинета", HttpStatus.BAD_REQUEST);
        }
        String cabinetName = normalizeName(trimmedName);

        assertOzonSellerInfoOrThrow(clientId, apiKey);
        assertApiKeyNotUsedByAnotherCabinet(null, apiKey);

        Cabinet cabinet = Cabinet.builder()
                .user(user)
                .marketplaceType(MarketplaceType.OZON)
                .name(cabinetName)
                .apiKey(apiKey)
                .ozonClientId(clientId)
                .tokenType(CabinetTokenType.BASIC)
                .isValid(true)
                .lastValidatedAt(java.time.LocalDateTime.now())
                .build();
        cabinet = cabinetRepository.save(cabinet);
        cabinetBillingService.initializeCabinetBilling(cabinet);
        cabinetIntegrationMirrorService.mirrorFromCabinet(cabinet);
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
        if (request.getTokenType() != null) {
            cabinet.setTokenType(request.getTokenType());
        }
        if (request.getOzonPerformanceClientId() != null) {
            resetPerformanceValidationAndSetClientId(cabinet, request.getOzonPerformanceClientId());
        }
        if (request.getOzonPerformanceClientSecret() != null) {
            resetPerformanceValidationAndSetClientSecret(cabinet, request.getOzonPerformanceClientSecret());
        }

        cabinet = save(cabinet);
        return toDto(cabinet);
    }

    /**
     * Обновляет Performance credentials Ozon (для админа при редактировании кабинета).
     */
    @Transactional
    public CabinetDto updateOzonPerformanceCredentials(
            Long cabinetId,
            String clientId,
            String clientSecret
    ) {
        Cabinet cabinet = findByIdWithUserOrThrow(cabinetId);
        if (cabinet.getMarketplaceType() != MarketplaceType.OZON) {
            throw new UserException("Кабинет не является Ozon", HttpStatus.BAD_REQUEST);
        }
        if (clientId != null) {
            resetPerformanceValidationAndSetClientId(cabinet, clientId);
        }
        if (clientSecret != null) {
            resetPerformanceValidationAndSetClientSecret(cabinet, clientSecret);
        }
        cabinet = save(cabinet);
        return toDto(cabinet, false);
    }

    /**
     * Сбрасывает статус валидации Performance и устанавливает client_id.
     */
    public void resetPerformanceValidationAndSetClientId(Cabinet cabinet, String clientId) {
        String trimmed = clientId != null ? clientId.trim() : null;
        cabinet.setOzonPerformanceIsValid(null);
        cabinet.setOzonPerformanceValidationError(null);
        cabinet.setOzonPerformanceLastValidatedAt(null);
        cabinet.setOzonPerformanceClientId(trimmed != null && !trimmed.isBlank() ? trimmed : null);
        ozonPerformanceApiClient.invalidateTokenCache(cabinet.getId());
    }

    /**
     * Сбрасывает статус валидации Performance и устанавливает client_secret.
     */
    public void resetPerformanceValidationAndSetClientSecret(Cabinet cabinet, String clientSecret) {
        String trimmed = clientSecret != null ? clientSecret.trim() : null;
        cabinet.setOzonPerformanceIsValid(null);
        cabinet.setOzonPerformanceValidationError(null);
        cabinet.setOzonPerformanceLastValidatedAt(null);
        if (trimmed != null && !trimmed.isBlank()) {
            cabinet.setOzonPerformanceClientSecret(trimmed);
        }
        ozonPerformanceApiClient.invalidateTokenCache(cabinet.getId());
    }

    /**
     * Обновление только API ключа кабинета (для админа/менеджера при редактировании кабинета селлера).
     * Проверка доступа выполняется в контроллере.
     */
    @Transactional
    public CabinetDto updateApiKey(Long cabinetId, String apiKey, CabinetTokenType tokenType) {
        Cabinet cabinet = findByIdWithUserOrThrow(cabinetId);
        if (apiKey != null) {
            resetValidationAndSetApiKey(cabinet, apiKey);
        }
        if (tokenType != null) {
            cabinet.setTokenType(tokenType);
        }
        cabinet = save(cabinet);
        return toDto(cabinet);
    }

    /**
     * Сбрасывает статус валидации и устанавливает новый API ключ кабинета.
     */
    public void resetValidationAndSetApiKey(Cabinet cabinet, String apiKey) {
        String trimmed = apiKey != null ? apiKey.trim() : null;
        if (trimmed != null && !trimmed.isBlank()) {
            assertApiKeyNotUsedByAnotherCabinet(cabinet.getId(), trimmed);
        }
        cabinet.setIsValid(null);
        cabinet.setValidationError(null);
        cabinet.setLastValidatedAt(null);
        cabinet.setApiKey(trimmed != null && !trimmed.isBlank() ? trimmed : null);
        clearPromotionWriteBlockIfPersisted(cabinet);
    }

    /**
     * Один WB API-ключ может быть привязан только к одному кабинету.
     */
    private void assertApiKeyNotUsedByAnotherCabinet(Long cabinetId, String apiKey) {
        List<CabinetIntegrationType> sellerTypes = List.of(
                CabinetIntegrationType.WB_API,
                CabinetIntegrationType.OZON_SELLER
        );
        boolean duplicate = cabinetId == null
                ? cabinetIntegrationRepository.existsByCredentialPrimaryAndIntegrationTypeIn(apiKey, sellerTypes)
                : cabinetIntegrationRepository.existsByCredentialPrimaryAndIntegrationTypeInAndCabinetIdNot(
                        apiKey, sellerTypes, cabinetId);
        if (duplicate) {
            throw new UserException(API_KEY_ALREADY_USED, HttpStatus.CONFLICT);
        }
    }

    /**
     * Снимает временную блокировку start/pause РК после смены API-ключа.
     */
    public void clearPromotionWriteBlockIfPersisted(Cabinet cabinet) {
        if (cabinet != null && cabinet.getId() != null) {
            cabinetScopeStatusService.clearPromotionWriteBlock(cabinet.getId());
        }
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
        cabinetDeletionService.deleteStepFbsStocks(cabinetId);
        cabinetDeletionService.deleteStepWbSellerWarehouses(cabinetId);
        cabinetDeletionService.deleteStepBarcodes(cabinetId);
        cabinetDeletionService.deleteStepCardAnalytics(cabinetId);
        cabinetDeletionService.deleteStepProductCards(cabinetId);
        cabinetDeletionService.deleteStepArticleNoteFiles(cabinetId);
        cabinetDeletionService.deleteStepArticleNotes(cabinetId);
        cabinetDeletionService.deleteStepWbCampaignNoteFiles(cabinetId);
        cabinetDeletionService.deleteStepWbCampaignNotes(cabinetId);
        cabinetDeletionService.deleteStepOzonApiEvents(cabinetId);
        cabinetDeletionService.deleteStepOzonPriceHistory(cabinetId);
        cabinetDeletionService.deleteStepOzonStocks(cabinetId);
        cabinetDeletionService.deleteStepOzonProductAnalytics(cabinetId);
        cabinetDeletionService.deleteStepOzonProductCards(cabinetId);
        cabinetDeletionService.deleteStepOzonPromotionCampaigns(cabinetId);
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
        if (currentUser.getRole() != Role.ADMIN) {
            throw new UserException(CABINET_ACCESS_DENIED, HttpStatus.FORBIDDEN);
        }
        findByIdWithUserOrThrow(cabinetId);
    }

    /**
     * Проверяет право запуска обновления остатков по кабинету.
     * Разрешено: владелец кабинета, ADMIN или пользователь с grant на раздел «Товары».
     */
    @Transactional(readOnly = true)
    public void validateCabinetAccessForStocksUpdate(Long cabinetId, User currentUser) {
        if (currentUser.getRole() == Role.ADMIN) {
            return;
        }
        if (cabinetAccessService.hasSectionAccess(currentUser, cabinetId, ru.oparin.solution.model.CabinetAccessSection.PRODUCTS)) {
            return;
        }
        throw new UserException(CABINET_ACCESS_DENIED, HttpStatus.FORBIDDEN);
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
        Cabinet cabinet = cabinetRepository.findDefaultByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("У продавца нет кабинета по умолчанию"));
        cabinetIntegrationMirrorService.overlayOntoCabinet(cabinet);
        return cabinet;
    }

    /**
     * Все кабинеты с API-ключом и активным продавцем указанной роли (для планировщиков и синхронизации).
     */
    @Transactional(readOnly = true)
    public List<Cabinet> findCabinetsWithApiKeyAndUser(Role role) {
        List<Cabinet> cabinets = cabinetRepository.findCabinetsWithApiKeyAndUser(role);
        cabinetIntegrationMirrorService.overlayOntoCabinets(cabinets);
        return cabinets;
    }

    /**
     * Ozon-кабинеты с ключами для планировщика синхронизации.
     */
    public List<Cabinet> findOzonCabinetsWithApiKeyAndUser(Role role) {
        List<Cabinet> cabinets = cabinetRepository.findOzonCabinetsWithApiKeyAndUser(role);
        cabinetIntegrationMirrorService.overlayOntoCabinets(cabinets);
        return cabinets;
    }

    /**
     * Сохраняет кабинет (ядро) и credentials/sync в Phase 5 таблицы.
     */
    @Transactional
    public Cabinet save(Cabinet cabinet) {
        Cabinet saved = cabinetRepository.save(cabinet);
        cabinetIntegrationMirrorService.persistFromCabinet(saved);
        return saved;
    }

    /**
     * Находит кабинет по ID (без проверки владельца).
     */
    @Transactional(readOnly = true)
    public Optional<Cabinet> findById(Long cabinetId) {
        Optional<Cabinet> cabinet = cabinetRepository.findById(cabinetId);
        cabinet.ifPresent(cabinetIntegrationMirrorService::overlayOntoCabinet);
        return cabinet;
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
        Cabinet cabinet = cabinetRepository.findById(cabinetId)
                .orElseThrow(() -> new UserException(CABINET_NOT_FOUND, HttpStatus.NOT_FOUND));
        cabinetIntegrationMirrorService.overlayOntoCabinet(cabinet);
        return cabinet;
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

    /**
     * Проверяет Client-Id + Api-Key через Ozon Seller API до сохранения кабинета.
     */
    private void assertOzonSellerInfoOrThrow(String clientId, String apiKey) {
        try {
            ozonSellerApiClient.getSellerInfo(clientId, apiKey);
        } catch (HttpClientErrorException e) {
            throw ozonSellerInfoHttpException(e);
        } catch (RestClientException e) {
            throw new UserException(OZON_SELLER_INFO_ERROR, HttpStatus.BAD_REQUEST);
        }
    }

    private UserException ozonSellerInfoHttpException(HttpClientErrorException e) {
        int status = e.getStatusCode().value();
        // Ozon: невалидный Api-Key часто отдаёт 404; неверный Client-Id — 400.
        if (status == HttpStatus.UNAUTHORIZED.value()
                || status == HttpStatus.FORBIDDEN.value()
                || status == HttpStatus.NOT_FOUND.value()
                || status == HttpStatus.BAD_REQUEST.value()) {
            return new UserException(
                    "Client-Id или Api-Key Ozon невалидны. Проверьте данные в кабинете продавца.",
                    HttpStatus.BAD_REQUEST);
        }
        if (status == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return new UserException(
                    "Превышен лимит запросов к Ozon API. Повторите попытку позже.",
                    HttpStatus.TOO_MANY_REQUESTS);
        }
        return new UserException(OZON_SELLER_INFO_ERROR + " (HTTP " + status + ")", HttpStatus.BAD_REQUEST);
    }

    private String resolveCabinetNameFromWb(String apiKey) {
        try {
            WbSellerInfoResponse sellerInfo = wbCommonApiClient.getSellerInfo(apiKey);
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
                                + formatSecondsAsHoursMinutesSeconds(retry) + ".",
                        HttpStatus.TOO_MANY_REQUESTS,
                        retry);
            }
            return new UserException(
                    "Превышен лимит запросов к WB API. Повторите попытку позже.",
                    HttpStatus.TOO_MANY_REQUESTS);
        }
        if (status == HttpStatus.UNAUTHORIZED.value()) {
            return new UserException(
                    "API-токен WB невалиден или истёк. Проверьте токен.",
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
        return toDto(cabinet, false);
    }

    private CabinetDto toDto(Cabinet cabinet, boolean maskApiKey) {
        CabinetDto.ApiKeyInfo apiKeyInfo = toApiKeyInfo(cabinet, maskApiKey);
        List<CabinetDto.ScopeStatusDto> scopeStatuses = cabinetScopeStatusService.getStatusesByCabinetId(cabinet.getId())
                .stream()
                .map(s -> CabinetDto.ScopeStatusDto.builder()
                        .category(s.category())
                        .categoryDisplayName(s.categoryDisplayName())
                        .lastCheckedAt(s.lastCheckedAt())
                        .success(s.success())
                        .errorMessage(s.errorMessage())
                        .writeBlockedUntil(s.writeBlockedUntil())
                        .writeReadOnly(s.writeReadOnly())
                        .build())
                .toList();
        return CabinetDto.builder()
                .id(cabinet.getId())
                .name(cabinet.getName())
                .marketplaceType(cabinet.getMarketplaceType() != null ? cabinet.getMarketplaceType() : MarketplaceType.WB)
                .createdAt(cabinet.getCreatedAt())
                .updatedAt(cabinet.getUpdatedAt())
                .lastDataUpdateAt(cabinet.getLastDataUpdateAt())
                .lastDataUpdateRequestedAt(cabinet.getLastDataUpdateRequestedAt())
                .lastStocksUpdateAt(cabinet.getLastStocksUpdateAt())
                .lastOzonCampaignsSyncAt(cabinet.getLastOzonCampaignsSyncAt())
                .apiKey(apiKeyInfo)
                .scopeStatuses(scopeStatuses)
                .build();
    }

    private CabinetDto.ApiKeyInfo toApiKeyInfo(Cabinet cabinet, boolean maskApiKey) {
        boolean isOzon = cabinet.getMarketplaceType() == MarketplaceType.OZON;
        if (cabinet.getApiKey() == null && cabinet.getIsValid() == null
                && (!isOzon || cabinet.getOzonClientId() == null)) {
            return null;
        }
        return CabinetDto.ApiKeyInfo.builder()
                .apiKey(maskApiKey ? maskApiKey(cabinet.getApiKey()) : cabinet.getApiKey())
                .tokenType(isOzon ? null : cabinet.getTokenType())
                .ozonClientId(cabinet.getOzonClientId())
                .isValid(cabinet.getIsValid())
                .lastValidatedAt(cabinet.getLastValidatedAt())
                .validationError(cabinet.getValidationError())
                .lastDataUpdateAt(cabinet.getLastDataUpdateAt())
                .lastDataUpdateRequestedAt(cabinet.getLastDataUpdateRequestedAt())
                .lastStocksUpdateAt(cabinet.getLastStocksUpdateAt())
                .ozonPerformanceClientId(cabinet.getOzonPerformanceClientId())
                .ozonPerformanceConfigured(
                        cabinet.getOzonPerformanceClientSecret() != null
                                && !cabinet.getOzonPerformanceClientSecret().isBlank())
                .ozonPerformanceIsValid(cabinet.getOzonPerformanceIsValid())
                .ozonPerformanceLastValidatedAt(cabinet.getOzonPerformanceLastValidatedAt())
                .ozonPerformanceValidationError(cabinet.getOzonPerformanceValidationError())
                .build();
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) return apiKey;
        final int visible = 8;
        if (apiKey.length() <= visible * 2) return "********";
        return apiKey.substring(0, visible) + "..." + apiKey.substring(apiKey.length() - visible);
    }
}
