package ru.oparin.solution.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.oparin.solution.dto.MessageResponse;
import ru.oparin.solution.dto.cabinet.CabinetDto;
import ru.oparin.solution.dto.cabinet.CreateCabinetRequest;
import ru.oparin.solution.dto.cabinet.UpdateCabinetRequest;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.model.User;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.UserService;

import java.util.List;

/**
 * API кабинетов продавца: список, создание, обновление (имя, API ключ), валидация ключа.
 */
@RestController
@RequestMapping("/cabinets")
@RequiredArgsConstructor
public class CabinetController {

    private final CabinetService cabinetService;
    private final UserService userService;

    /**
     * Список кабинетов: для SELLER — свои, для WORKER — кабинеты владельца (продавца).
     * Сортировка по дате создания (новые первые); первый в списке — кабинет по умолчанию.
     */
    @GetMapping
    public ResponseEntity<List<CabinetDto>> list(Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        Long ownerId = getCabinetOwnerUserId(user);
        if (ownerId == null) {
            throw new UserException("Доступ к кабинетам запрещён", HttpStatus.FORBIDDEN);
        }
        List<CabinetDto> cabinets = cabinetService.listByUserId(ownerId);
        return ResponseEntity.ok(cabinets);
    }

    /**
     * Один кабинет по ID (проверка принадлежности текущему пользователю).
     */
    @GetMapping("/{id}")
    public ResponseEntity<CabinetDto> getById(
            @PathVariable Long id,
            Authentication authentication
    ) {
        User user = userService.findByEmail(authentication.getName());
        Long ownerId = getCabinetOwnerUserId(user);
        if (ownerId == null) {
            throw new UserException("Доступ к кабинетам запрещён", HttpStatus.FORBIDDEN);
        }
        CabinetDto dto = cabinetService.getByIdAndUserId(id, ownerId);
        return ResponseEntity.ok(dto);
    }

    /**
     * Создание кабинета (только SELLER).
     */
    @PostMapping
    public ResponseEntity<CabinetDto> create(
            @Valid @RequestBody CreateCabinetRequest request,
            Authentication authentication
    ) {
        User user = userService.findByEmail(authentication.getName());
        validateSellerRole(user);
        CabinetDto created = cabinetService.create(user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Обновление кабинета: имя и/или API ключ (опционально). Валидация ключа при первом использовании.
     */
    @PatchMapping("/{id}")
    public ResponseEntity<CabinetDto> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCabinetRequest request,
            Authentication authentication
    ) {
        User user = userService.findByEmail(authentication.getName());
        validateSellerRole(user);
        CabinetDto updated = cabinetService.update(id, user.getId(), request);
        return ResponseEntity.ok(updated);
    }

    /**
     * Запуск валидации API ключа кабинета.
     */
    @PostMapping("/{id}/api-key/validate")
    public ResponseEntity<MessageResponse> validateApiKey(
            @PathVariable Long id,
            Authentication authentication
    ) {
        User user = userService.findByEmail(authentication.getName());
        validateSellerRole(user);
        cabinetService.validateApiKey(id, user.getId());
        Cabinet cabinet = cabinetService.findCabinetByIdAndUserId(id, user.getId());
        String message = Boolean.TRUE.equals(cabinet.getIsValid())
                ? "API ключ валиден"
                : (cabinet.getValidationError() != null ? "API ключ невалиден: " + cabinet.getValidationError() : "API ключ невалиден");
        return ResponseEntity.ok(MessageResponse.builder().message(message).build());
    }

    /**
     * Удаление кабинета и всех связанных с ним данных.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            Authentication authentication
    ) {
        User user = userService.findByEmail(authentication.getName());
        validateSellerRole(user);
        cabinetService.delete(id, user.getId());
        return ResponseEntity.noContent().build();
    }

    private void validateSellerRole(User user) {
        if (user.getRole() != Role.SELLER) {
            throw new UserException("Только продавец может управлять кабинетами", HttpStatus.FORBIDDEN);
        }
    }

    private Long getCabinetOwnerUserId(User user) {
        if (user.getRole() == Role.SELLER) return user.getId();
        if (user.getRole() == Role.WORKER && user.getOwner() != null) return user.getOwner().getId();
        return null;
    }
}
