package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.config.SubscriptionProperties;
import ru.oparin.solution.dto.*;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.model.Subscription;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.SubscriptionRepository;
import ru.oparin.solution.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Сервис для работы с пользователями.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private static final String ADMIN_ONLY = "Только администратор";

    private final UserRepository userRepository;
    private final CabinetService cabinetService;
    private final PasswordEncoder passwordEncoder;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionProperties subscriptionProperties;
    private final UserManagementQueryService userManagementQueryService;
    private final ProfileSubscriptionService profileSubscriptionService;
    private final AccountTypeService accountTypeService;
    private final CabinetAccessService cabinetAccessService;

    @Lazy
    @Autowired
    private UserService self;

    /**
     * Регистрация нового пользователя.
     */
    @Transactional
    public User registerUser(RegisterRequest request) {
        validateEmailNotExists(request.getEmail());
        if (request.getAccountTypes() == null || request.getAccountTypes().isEmpty()) {
            throw new UserException("Укажите тип аккаунта", HttpStatus.BAD_REQUEST);
        }
        String encodedPassword = encodePassword(request.getPassword());
        boolean marketingConsent = Boolean.TRUE.equals(request.getMarketingConsent());
        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(encodedPassword)
                .role(Role.USER)
                .isActive(true)
                .emailConfirmed(false)
                .marketingConsent(marketingConsent)
                .agencyManaged(false)
                .build();
        user = userRepository.save(user);
        accountTypeService.replaceAccountTypes(user.getId(), request.getAccountTypes());
        profileSubscriptionService.createFreeAnalyticsSubscription(user);
        return user;
    }

    /** @deprecated используйте {@link #registerUser(RegisterRequest)} */
    @Transactional
    public User registerSeller(RegisterRequest request) {
        return registerUser(request);
    }

    /**
     * Поиск пользователя по email.
     *
     * @param email email пользователя
     * @return найденный пользователь
     * @throws UserException если пользователь не найден
     */
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserException(
                        "Пользователь не найден: " + email,
                        HttpStatus.NOT_FOUND
                ));
    }

    /**
     * Обновление WB API ключа пользователя.
     * Создает новую запись, если ключ еще не существует, или обновляет существующую.
     *
     * @param userId ID пользователя (SELLER)
     * @param newApiKey новый API ключ со всеми правами
     */
    @Transactional
    public void updateApiKey(Long userId, String newApiKey) {
        Cabinet cabinet = cabinetService.findDefaultByUserId(userId)
                .orElseGet(() -> createNewCabinet(userId));

        resetApiKeyValidation(cabinet);
        cabinet.setApiKey(newApiKey);
        cabinet = cabinetService.save(cabinet);
        cabinetService.clearPromotionWriteBlockIfPersisted(cabinet);
    }

    /**
     * Смена пароля пользователя.
     *
     * @param userId ID пользователя
     * @param currentPassword текущий пароль
     * @param newPassword новый пароль
     * @throws UserException если текущий пароль неверен или пользователь не найден
     */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = findById(userId);
        validateCurrentPassword(user, currentPassword);
        validateNewPasswordDifferent(user, newPassword);
        
        updateUserPassword(user, newPassword);
        userRepository.save(user);
        log.info("Пароль изменён: {} (userId={})", user.getEmail(), userId);
    }

    /**
     * Проверяет, что пользователь с таким email не существует.
     */
    private void validateEmailNotExists(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new UserException(
                    "Пользователь с таким email уже существует: " + email, 
                    HttpStatus.CONFLICT
            );
        }
    }

    /**
     * Кодирует пароль.
     */
    private String encodePassword(String password) {
        return passwordEncoder.encode(password);
    }

    /**
     * Создает нового пользователя-продавца.
     */
    private User createSellerUser(String email, String encodedPassword, boolean marketingConsent) {
        return User.builder()
                .email(email)
                .password(encodedPassword)
                .role(Role.USER)
                .isActive(true)
                .emailConfirmed(false)
                .marketingConsent(marketingConsent)
                .agencyManaged(false)
                .build();
    }

    /**
     * Создаёт подписку для нового самостоятельного селлера после подтверждения почты.
     * При выключенной оплате — бессрочная активная подписка (до 100 лет), чтобы при включении оплаты доступ сохранился.
     * При включённой оплате — триал на trialDays дней.
     */
    void createTrialSubscriptionForUser(User user) {
        LocalDateTime now = LocalDateTime.now();
        List<String> activeStatuses = List.of("active", "trial");
        boolean hasActive = subscriptionRepository
                .findFirstByUser_IdAndStatusInAndExpiresAtAfterOrderByExpiresAtDesc(user.getId(), activeStatuses, now)
                .isPresent();
        if (hasActive) {
            return;
        }

        String status;
        LocalDateTime expiresAt;
        if (!subscriptionProperties.isBillingEnabled()) {
            status = "active";
            expiresAt = now.plusYears(100);
        } else {
            status = "trial";
            expiresAt = now.plusDays(subscriptionProperties.getTrialDays());
        }

        Subscription subscription = Subscription.builder()
                .user(user)
                .plan(null)
                .status(status)
                .startedAt(now)
                .expiresAt(expiresAt)
                .build();

        subscriptionRepository.save(subscription);
    }

    /**
     * Находит пользователя по ID.
     */
    public User findById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserException(
                        "Пользователь не найден с ID: " + userId,
                        HttpStatus.NOT_FOUND
                ));
    }


    /**
     * Создаёт новый кабинет для пользователя (по умолчанию «Основной кабинет»).
     */
    private Cabinet createNewCabinet(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException("Пользователь не найден: " + userId, HttpStatus.NOT_FOUND));
        return Cabinet.builder()
                .user(user)
                .name("Основной кабинет")
                .build();
    }

    /**
     * Сбрасывает статус валидации API ключа кабинета.
     */
    private void resetApiKeyValidation(Cabinet cabinet) {
        cabinet.setIsValid(null);
        cabinet.setValidationError(null);
        cabinet.setLastValidatedAt(null);
    }

    /**
     * Проверяет текущий пароль пользователя.
     */
    private void validateCurrentPassword(User user, String currentPassword) {
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new UserException("Неверный текущий пароль", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Проверяет, что новый пароль отличается от текущего.
     */
    private void validateNewPasswordDifferent(User user, String newPassword) {
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new UserException("Новый пароль должен отличаться от текущего", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Обновляет пароль пользователя.
     */
    private void updateUserPassword(User user, String newPassword) {
        user.setPassword(encodePassword(newPassword));
    }

    /**
     * Устанавливает новый пароль пользователю по ID (используется при сбросе пароля по токену).
     *
     * @param userId      ID пользователя
     * @param newPassword новый пароль (в открытом виде)
     */
    @Transactional
    public void setPassword(Long userId, String newPassword) {
        User user = findById(userId);
        updateUserPassword(user, newPassword);
        userRepository.save(user);
    }

    /**
     * Создание нового пользователя с проверкой прав доступа.
     *
     * @param request данные для создания пользователя
     * @param currentUser текущий пользователь (создатель)
     * @return созданный пользователь
     * @throws UserException если нет прав или пользователь с таким email уже существует
     */
    @Transactional
    public User createUser(CreateUserRequest request, User currentUser) {
        validateCanCreateUser(currentUser, request.getRole());
        validateEmailNotExists(request.getEmail());
        Role role = request.getRole() == Role.ADMIN ? Role.ADMIN : Role.USER;
        User newUser = User.builder()
                .email(request.getEmail())
                .password(encodePassword(request.getPassword()))
                .role(role)
                .isActive(true)
                .emailConfirmed(false)
                .agencyManaged(Boolean.TRUE.equals(request.getAgencyManaged()))
                .build();
        newUser = userRepository.save(newUser);
        profileSubscriptionService.createFreeAnalyticsSubscription(newUser);
        return newUser;
    }

    @Transactional
    public User updateUser(Long userId, UpdateUserRequest request, User currentUser) {
        User userToUpdate = findById(userId);
        validateCanManageUser(currentUser, userToUpdate);
        if (!userToUpdate.getEmail().equals(request.getEmail())) {
            validateEmailNotExists(request.getEmail());
            userToUpdate.setEmail(request.getEmail());
        }
        if (request.getIsActive() != null) {
            userToUpdate.setIsActive(request.getIsActive());
        }
        if (currentUser.getRole() == Role.ADMIN && request.getAgencyManaged() != null) {
            userToUpdate.setAgencyManaged(request.getAgencyManaged());
        }
        return userRepository.save(userToUpdate);
    }

    @Transactional
    public User updateProfile(User user, UpdateProfileRequest request) {
        if (request.getName() != null) {
            user.setName(request.getName().trim());
        }
        if (user.getRole() != Role.ADMIN
                && request.getAccountTypes() != null
                && !request.getAccountTypes().isEmpty()) {
            accountTypeService.replaceAccountTypes(user.getId(), request.getAccountTypes());
        }
        return userRepository.save(user);
    }

    /**
     * Переключение активности пользователя.
     *
     * @param userId ID пользователя
     * @param currentUser текущий пользователь
     * @return обновленный пользователь
     * @throws UserException если нет прав или пользователь не найден
     */
    @Transactional
    public User toggleUserActive(Long userId, User currentUser) {
        User userToUpdate = findById(userId);
        validateCanManageUser(currentUser, userToUpdate);

        userToUpdate.setIsActive(!userToUpdate.getIsActive());
        return userRepository.save(userToUpdate);
    }

    /**
     * Запуск удаления пользователя (только для ADMIN).
     * Проверяет права и сразу возвращает управление; само удаление выполняется асинхронно в фоне.
     * Каждый кабинет и запись пользователя удаляются в отдельной транзакции.
     *
     * @param userId ID пользователя для удаления
     * @param currentUser текущий пользователь (должен быть ADMIN)
     * @throws UserException если нет прав, попытка удалить себя или другого админа
     */
    public void deleteUser(Long userId, User currentUser) {
        if (currentUser.getRole() != Role.ADMIN) {
            throw new UserException("Только администратор может удалять пользователей", HttpStatus.FORBIDDEN);
        }
        if (currentUser.getId().equals(userId)) {
            throw new UserException("Нельзя удалить самого себя", HttpStatus.BAD_REQUEST);
        }
        User userToDelete = findById(userId);
        if (userToDelete.getRole() == Role.ADMIN) {
            throw new UserException("Нельзя удалить другого администратора", HttpStatus.FORBIDDEN);
        }
        log.info("[Удаление пользователя] Запрос принят, запуск фонового удаления: {} (userId={})", userToDelete.getEmail(), userId);
        self.runDeletionAsync(userId);
    }

    /**
     * Фоновое удаление пользователя: подчинённые (рекурсивно), кабинеты (каждый в своей транзакции), затем запись пользователя (в своей транзакции).
     */
    @Async("userDeletionExecutor")
    public void runDeletionAsync(Long userId) {
        User userToDelete = userRepository.findById(userId).orElse(null);
        if (userToDelete == null) {
            log.warn("[Удаление пользователя] Пользователь userId={} уже удалён или не найден", userId);
            return;
        }
        log.info("[Удаление пользователя] Начало фонового удаления: {} (userId={})", userToDelete.getEmail(), userId);

        List<User> subordinates = List.of();
        if (!subordinates.isEmpty()) {
            log.info("[Удаление пользователя]   → Подчинённых: {} шт.", subordinates.size());
        }
        for (User sub : subordinates) {
            log.info("[Удаление пользователя]   → Удаляю подчинённого: {} (userId={})", sub.getEmail(), sub.getId());
            self.runDeletionAsync(sub.getId());
        }

        List<Cabinet> cabinets = cabinetService.findCabinetsByUserId(userId);
        if (!cabinets.isEmpty()) {
            log.info("[Удаление пользователя]   → Кабинетов: {} шт.", cabinets.size());
        }
        for (Cabinet cabinet : cabinets) {
            log.info("[Удаление пользователя]   → Кабинет «{}» (cabinetId={})", cabinet.getName(), cabinet.getId());
            cabinetService.delete(cabinet.getId(), userId);
        }

        self.deleteUserRecord(userId);
        log.info("[Удаление пользователя] Готово: {} (userId={}) удалён", userToDelete.getEmail(), userId);
    }

    /**
     * Удаление записи пользователя в БД в отдельной транзакции.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteUserRecord(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }
        log.info("[Удаление пользователя]   → Удаляю запись пользователя в БД: {} (userId={})", user.getEmail(), userId);
        userRepository.delete(user);
    }

    public Page<UserListItemDto> getManagedUsersPageDto(User currentUser,
                                                        Pageable pageable,
                                                        String email,
                                                        boolean onlySellers,
                                                        UserSortField sortBy,
                                                        Sort.Direction sortDir) {
        Page<User> page = userManagementQueryService.findManagedUsers(
                currentUser,
                pageable,
                email,
                onlySellers,
                sortBy,
                sortDir
        );
        return page.map(user -> mapToUserListItemDto(user));
    }

    public List<UserListItemDto> getActiveSellers(User currentUser) {
        List<User> sellers = getVisibleActiveSellers(currentUser);
        return sellers.stream()
                .filter(seller -> cabinetService.findDefaultByUserId(seller.getId()).isPresent())
                .map(this::mapToUserListItemDto)
                .toList();
    }

    private void validateCanCreateUser(User currentUser, Role newUserRole) {
        if (currentUser.getRole() != Role.ADMIN) {
            throw new UserException("Только администратор может создавать пользователей", HttpStatus.FORBIDDEN);
        }
        if (newUserRole != null && newUserRole != Role.USER && newUserRole != Role.ADMIN) {
            throw new UserException("Можно создать только USER или ADMIN", HttpStatus.FORBIDDEN);
        }
    }

    private void validateCanManageUser(User currentUser, User userToManage) {
        if (currentUser.getRole() != Role.ADMIN) {
            throw new UserException("Только администратор может управлять пользователями", HttpStatus.FORBIDDEN);
        }
        if (userToManage.getRole() == Role.ADMIN && !currentUser.getId().equals(userToManage.getId())) {
            throw new UserException("ADMIN не может управлять другими администраторами", HttpStatus.FORBIDDEN);
        }
    }

    public UserListItemDto toUserListItemDto(User user) {
        return mapToUserListItemDto(user);
    }

    private UserListItemDto mapToUserListItemDto(User user) {
        SellerUpdateDates dates = getSellerUpdateDates(user.getId());
        return UserListItemDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .isActive(user.getIsActive())
                .agencyManaged(user.getAgencyManaged())
                .createdAt(user.getCreatedAt())
                .ownerEmail(null)
                .managerEmails(null)
                .lastDataUpdateAt(dates.lastDataUpdateAt())
                .lastDataUpdateRequestedAt(dates.lastDataUpdateRequestedAt())
                .build();
    }

    private List<User> getVisibleActiveSellers(User currentUser) {
        if (currentUser.getRole() == Role.ADMIN) {
            return userRepository.findByRoleAndIsActive(Role.USER, true);
        }
        return List.of();
    }

    private SellerUpdateDates getSellerUpdateDates(Long sellerId) {
        List<Cabinet> cabinets = cabinetService.findCabinetsByUserId(sellerId);
        LocalDateTime lastDataUpdateAt = cabinets.stream()
                .map(Cabinet::getLastDataUpdateAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        LocalDateTime lastDataUpdateRequestedAt = cabinets.stream()
                .map(Cabinet::getLastDataUpdateRequestedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        return new SellerUpdateDates(lastDataUpdateAt, lastDataUpdateRequestedAt);
    }

    private record SellerUpdateDates(LocalDateTime lastDataUpdateAt, LocalDateTime lastDataUpdateRequestedAt) {}
}
