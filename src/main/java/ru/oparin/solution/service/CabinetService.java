package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
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
public class CabinetService {

    private final CabinetRepository cabinetRepository;
    private final UserRepository userRepository;
    private final WbApiKeyService wbApiKeyService;

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
                    .build();
        }
        return CabinetDto.builder()
                .id(c.getId())
                .name(c.getName())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .apiKey(apiKeyInfo)
                .build();
    }
}
