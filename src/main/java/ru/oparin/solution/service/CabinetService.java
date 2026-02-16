package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.cabinet.CabinetDto;
import ru.oparin.solution.dto.cabinet.CreateCabinetRequest;
import ru.oparin.solution.dto.cabinet.UpdateCabinetRequest;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.CabinetRepository;
import ru.oparin.solution.repository.UserRepository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Сервис CRUD для кабинетов продавца.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CabinetService {

    private final CabinetRepository cabinetRepository;
    private final UserRepository userRepository;
    private final WbApiKeyService wbApiKeyService;
    private final CabinetDeletionService cabinetDeletionService;

    /**
     * Список кабинетов пользователя (продавца), отсортированный по дате создания (новые первые).
     */
    @Transactional(readOnly = true)
    public List<CabinetDto> listByUserId(Long userId) {
        List<Cabinet> cabinets = cabinetRepository.findByUser_IdOrderByCreatedAtDesc(userId);
        return cabinets.stream().map(this::toDto).collect(Collectors.toList());
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
            throw new UserException("Только продавец может создавать кабинеты", HttpStatus.FORBIDDEN);
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
            wbApiKeyService.resetValidationAndSetApiKey(cabinet, request.getApiKey());
        }

        cabinet = cabinetRepository.save(cabinet);
        return toDto(cabinet);
    }

    /**
     * Запуск валидации API ключа кабинета.
     */
    @Transactional
    public void validateApiKey(Long cabinetId, Long userId) {
        Cabinet cabinet = findCabinetByIdAndUserId(cabinetId, userId);
        wbApiKeyService.validateApiKeyByCabinet(cabinet);
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
        cabinetDeletionService.deleteStepCabinetRecord(cabinetId);

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
        Cabinet cabinet = cabinetRepository.findByIdWithUser(cabinetId)
                .orElseThrow(() -> new UserException("Кабинет не найден", HttpStatus.NOT_FOUND));
        User seller = cabinet.getUser();
        if (seller.getRole() != Role.SELLER) {
            throw new UserException("Кабинет не принадлежит селлеру", HttpStatus.FORBIDDEN);
        }
        if (currentUser.getRole() == Role.ADMIN) {
            return;
        }
        if (currentUser.getRole() == Role.MANAGER) {
            if (seller.getOwner() != null && seller.getOwner().getId().equals(currentUser.getId())) {
                return;
            }
        }
        throw new UserException("Нет доступа к данному кабинету", HttpStatus.FORBIDDEN);
    }

    /**
     * Возвращает кабинет по ID, если он принадлежит пользователю.
     */
    public Cabinet findCabinetByIdAndUserId(Long cabinetId, Long userId) {
        if (!cabinetRepository.existsByIdAndUser_Id(cabinetId, userId)) {
            throw new UserException("Кабинет не найден или доступ запрещён", HttpStatus.NOT_FOUND);
        }
        return cabinetRepository.findById(cabinetId)
                .orElseThrow(() -> new UserException("Кабинет не найден", HttpStatus.NOT_FOUND));
    }

    private CabinetDto toDto(Cabinet c) {
        CabinetDto.ApiKeyInfo apiKeyInfo = null;
        if (c.getApiKey() != null || c.getIsValid() != null) {
            apiKeyInfo = CabinetDto.ApiKeyInfo.builder()
                    .apiKey(c.getApiKey())
                    .isValid(c.getIsValid())
                    .lastValidatedAt(c.getLastValidatedAt())
                    .validationError(c.getValidationError())
                    .lastDataUpdateAt(c.getLastDataUpdateAt())
                    .lastDataUpdateRequestedAt(c.getLastDataUpdateRequestedAt())
                    .build();
        }
        return CabinetDto.builder()
                .id(c.getId())
                .name(c.getName())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .lastDataUpdateAt(c.getLastDataUpdateAt())
                .lastDataUpdateRequestedAt(c.getLastDataUpdateRequestedAt())
                .apiKey(apiKeyInfo)
                .build();
    }
}
