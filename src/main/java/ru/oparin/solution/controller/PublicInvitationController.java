package ru.oparin.solution.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.oparin.solution.dto.cabinet.CabinetInvitationPreviewDto;
import ru.oparin.solution.model.User;
import ru.oparin.solution.service.CabinetAccessService;
import ru.oparin.solution.service.UserService;

/**
 * Публичные и полу-публичные операции с приглашениями в кабинет.
 */
@RestController
@RequestMapping("/public/invitations")
@RequiredArgsConstructor
public class PublicInvitationController {

    private final CabinetAccessService cabinetAccessService;
    private final UserService userService;

    @GetMapping("/{token}")
    public ResponseEntity<CabinetInvitationPreviewDto> preview(@PathVariable String token) {
        return ResponseEntity.ok(cabinetAccessService.previewInvitation(token));
    }

    @PostMapping("/{token}/accept")
    public ResponseEntity<CabinetInvitationPreviewDto> accept(
            @PathVariable String token,
            Authentication authentication
    ) {
        User user = userService.findByEmail(authentication.getName());
        cabinetAccessService.acceptInvitation(user, token);
        return ResponseEntity.ok(cabinetAccessService.previewInvitation(token));
    }

    /**
     * Отклонение приглашения текущим пользователем (email должен совпадать с приглашением).
     */
    @PostMapping("/{token}/decline")
    public ResponseEntity<Void> decline(
            @PathVariable String token,
            Authentication authentication
    ) {
        User user = userService.findByEmail(authentication.getName());
        cabinetAccessService.declineInvitation(user, token);
        return ResponseEntity.noContent().build();
    }
}
