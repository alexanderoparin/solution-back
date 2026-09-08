package ru.oparin.solution.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.oparin.solution.dto.BugReportRequestDto;
import ru.oparin.solution.dto.MessageResponse;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.User;
import ru.oparin.solution.service.EmailService;
import ru.oparin.solution.service.UserService;

/**
 * Обратная связь из сервиса: баги, замечания, предложения.
 */
@RestController
@RequestMapping("/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final UserService userService;
    private final EmailService emailService;

    /**
     * Принимает сообщение пользователя и отправляет его на корпоративную почту.
     *
     * @param request        текст и адрес страницы
     * @param authentication текущий пользователь
     * @return подтверждение отправки
     */
    @PostMapping("/bug-report")
    public ResponseEntity<MessageResponse> submitBugReport(
            @Valid @RequestBody BugReportRequestDto request,
            Authentication authentication
    ) {
        User user = userService.findByEmail(authentication.getName());
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new UserException("Аккаунт деактивирован", HttpStatus.UNAUTHORIZED);
        }
        emailService.sendBugReportEmail(user, request.getMessage(), request.getPageUrl());
        return ResponseEntity.ok(MessageResponse.builder()
                .message("Сообщение отправлено. Спасибо!")
                .build());
    }
}
