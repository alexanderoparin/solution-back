package ru.oparin.solution.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.oparin.solution.dto.MessageResponse;
import ru.oparin.solution.dto.cabinet.*;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.model.User;
import ru.oparin.solution.service.CabinetAccessService;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.UserService;
import ru.oparin.solution.service.WbApiKeyService;

import java.util.List;

/**
 * API кабинетов: список, CRUD, обзор, управление доступами.
 */
@RestController
@RequestMapping("/cabinets")
@RequiredArgsConstructor
public class CabinetController {

    private final CabinetService cabinetService;
    private final WbApiKeyService wbApiKeyService;
    private final UserService userService;
    private final CabinetAccessService cabinetAccessService;

    @GetMapping("/overview")
    public ResponseEntity<CabinetsOverviewDto> overview(
            @RequestParam(required = false) String search,
            Authentication authentication
    ) {
        User user = userService.findByEmail(authentication.getName());
        return ResponseEntity.ok(cabinetAccessService.getOverview(user, search));
    }

    @GetMapping("/{id}/access")
    public ResponseEntity<List<CabinetAccessEntryDto>> listAccess(
            @PathVariable Long id,
            Authentication authentication
    ) {
        User user = userService.findByEmail(authentication.getName());
        return ResponseEntity.ok(cabinetAccessService.listAccessEntries(user, id));
    }

    @PostMapping("/{id}/access")
    public ResponseEntity<MessageResponse> grantAccess(
            @PathVariable Long id,
            @Valid @RequestBody GrantCabinetAccessRequest request,
            Authentication authentication
    ) {
        User user = userService.findByEmail(authentication.getName());
        cabinetAccessService.grantAccess(user, id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(MessageResponse.builder().message("Приглашение отправлено").build());
    }

    @DeleteMapping("/{id}/access/grants/{grantId}")
    public ResponseEntity<MessageResponse> revokeGrant(
            @PathVariable Long id,
            @PathVariable Long grantId,
            Authentication authentication
    ) {
        User user = userService.findByEmail(authentication.getName());
        cabinetAccessService.revokeGrant(user, id, grantId);
        return ResponseEntity.ok(MessageResponse.builder().message("Доступ отозван").build());
    }

    @PatchMapping("/{id}/access/grants/{grantId}")
    public ResponseEntity<MessageResponse> updateGrantValidUntil(
            @PathVariable Long id,
            @PathVariable Long grantId,
            @RequestBody UpdateCabinetAccessValidUntilRequest request,
            Authentication authentication
    ) {
        User user = userService.findByEmail(authentication.getName());
        cabinetAccessService.updateGrantValidUntil(user, id, grantId, request.validUntil());
        return ResponseEntity.ok(MessageResponse.builder().message("Срок доступа обновлён").build());
    }

    @PatchMapping("/{id}/access/invitations/{invitationId}")
    public ResponseEntity<MessageResponse> updateInvitationValidUntil(
            @PathVariable Long id,
            @PathVariable Long invitationId,
            @RequestBody UpdateCabinetAccessValidUntilRequest request,
            Authentication authentication
    ) {
        User user = userService.findByEmail(authentication.getName());
        cabinetAccessService.updateInvitationValidUntil(user, id, invitationId, request.validUntil());
        return ResponseEntity.ok(MessageResponse.builder().message("Срок доступа обновлён").build());
    }

    /**
     * Обновление разделов активного доступа.
     */
    @PatchMapping("/{id}/access/grants/{grantId}/sections")
    public ResponseEntity<MessageResponse> updateGrantSections(
            @PathVariable Long id,
            @PathVariable Long grantId,
            @Valid @RequestBody UpdateCabinetAccessSectionsRequest request,
            Authentication authentication
    ) {
        User user = userService.findByEmail(authentication.getName());
        cabinetAccessService.updateGrantSections(user, id, grantId, request.sections());
        return ResponseEntity.ok(MessageResponse.builder().message("Разделы доступа обновлены").build());
    }

    /**
     * Обновление разделов ожидающего приглашения.
     */
    @PatchMapping("/{id}/access/invitations/{invitationId}/sections")
    public ResponseEntity<MessageResponse> updateInvitationSections(
            @PathVariable Long id,
            @PathVariable Long invitationId,
            @Valid @RequestBody UpdateCabinetAccessSectionsRequest request,
            Authentication authentication
    ) {
        User user = userService.findByEmail(authentication.getName());
        cabinetAccessService.updateInvitationSections(user, id, invitationId, request.sections());
        return ResponseEntity.ok(MessageResponse.builder().message("Разделы приглашения обновлены").build());
    }

    @DeleteMapping("/{id}/access/invitations/{invitationId}")
    public ResponseEntity<MessageResponse> revokeInvitation(
            @PathVariable Long id,
            @PathVariable Long invitationId,
            Authentication authentication
    ) {
        User user = userService.findByEmail(authentication.getName());
        cabinetAccessService.revokeInvitation(user, id, invitationId);
        return ResponseEntity.ok(MessageResponse.builder().message("Приглашение отозвано").build());
    }

    /**
     * Повторная отправка приглашения (для отозванных / отклонённых / истёкших).
     */
    @PostMapping("/{id}/access/invitations/{invitationId}/resend")
    public ResponseEntity<MessageResponse> resendInvitation(
            @PathVariable Long id,
            @PathVariable Long invitationId,
            Authentication authentication
    ) {
        User user = userService.findByEmail(authentication.getName());
        cabinetAccessService.resendInvitation(user, id, invitationId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(MessageResponse.builder().message("Приглашение отправлено повторно").build());
    }

    /**
     * Повторная выдача доступа по отозванному grant.
     */
    @PostMapping("/{id}/access/grants/{grantId}/reinvite")
    public ResponseEntity<MessageResponse> reinviteFromGrant(
            @PathVariable Long id,
            @PathVariable Long grantId,
            Authentication authentication
    ) {
        User user = userService.findByEmail(authentication.getName());
        cabinetAccessService.reinviteFromGrant(user, id, grantId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(MessageResponse.builder().message("Приглашение отправлено повторно").build());
    }

    @GetMapping
    public ResponseEntity<List<CabinetDto>> list(Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        if (user.getRole() == Role.ADMIN) {
            return ResponseEntity.ok(List.of());
        }
        List<CabinetDto> cabinets = cabinetService.listAccessibleForUser(user);
        return ResponseEntity.ok(cabinets);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CabinetDto> getById(
            @PathVariable Long id,
            Authentication authentication
    ) {
        User user = userService.findByEmail(authentication.getName());
        if (!cabinetAccessService.isCabinetOwner(user, id)) {
            throw new UserException("Нет доступа к кабинету", HttpStatus.FORBIDDEN);
        }
        Long ownerId = cabinetService.findById(id).orElseThrow().getUser().getId();
        CabinetDto dto = cabinetService.getByIdAndUserId(id, ownerId, false);
        return ResponseEntity.ok(dto);
    }

    @PostMapping
    public ResponseEntity<CabinetDto> create(
            @Valid @RequestBody CreateCabinetRequest request,
            Authentication authentication
    ) {
        User user = userService.findByEmail(authentication.getName());
        validateCanCreateCabinet(user);
        CabinetDto created = cabinetService.create(user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CabinetDto> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCabinetRequest request,
            Authentication authentication
    ) {
        User user = userService.findByEmail(authentication.getName());
        if (!cabinetAccessService.isCabinetOwner(user, id)) {
            throw new UserException("Только владелец может редактировать кабинет", HttpStatus.FORBIDDEN);
        }
        CabinetDto updated = cabinetService.update(id, user.getId(), request);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/api-key/validate")
    public ResponseEntity<MessageResponse> validateApiKey(
            @PathVariable Long id,
            Authentication authentication
    ) {
        User user = userService.findByEmail(authentication.getName());
        if (!cabinetAccessService.isCabinetOwner(user, id) && user.getRole() != Role.ADMIN) {
            throw new UserException("Нет доступа", HttpStatus.FORBIDDEN);
        }
        Long ownerId = cabinetService.findById(id).orElseThrow().getUser().getId();
        wbApiKeyService.validateApiKey(id, ownerId);
        Cabinet cabinet = cabinetService.findCabinetByIdAndUserId(id, ownerId);
        boolean valid = Boolean.TRUE.equals(cabinet.getIsValid());
        MessageResponse body = MessageResponse.builder()
                .message(valid ? "API ключ валиден"
                        : (cabinet.getValidationError() != null ? cabinet.getValidationError() : "API ключ не прошёл проверку"))
                .build();
        return valid ? ResponseEntity.ok(body) : ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            Authentication authentication
    ) {
        User user = userService.findByEmail(authentication.getName());
        if (!cabinetAccessService.isCabinetOwner(user, id)) {
            throw new UserException("Только владелец может удалить кабинет", HttpStatus.FORBIDDEN);
        }
        cabinetService.delete(id, user.getId());
        return ResponseEntity.noContent().build();
    }

    private void validateCanCreateCabinet(User user) {
        if (user.getRole() == Role.ADMIN || user.getRole() == Role.USER) {
            return;
        }
        throw new UserException("Создание кабинета недоступно", HttpStatus.FORBIDDEN);
    }
}
