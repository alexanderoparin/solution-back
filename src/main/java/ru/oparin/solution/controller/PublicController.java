package ru.oparin.solution.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.oparin.solution.dto.LandingContactRequestDto;
import ru.oparin.solution.dto.MessageResponse;
import ru.oparin.solution.service.EmailService;

/**
 * Публичные эндпоинты лендинга (без авторизации).
 */
@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
public class PublicController {

    private final EmailService emailService;

    /**
     * Принимает заявку на аудит рекламного кабинета и отправляет её на почту оператора.
     */
    @PostMapping("/cabinet-audit-request")
    public ResponseEntity<MessageResponse> submitCabinetAuditRequest(
            @Valid @RequestBody LandingContactRequestDto request
    ) {
        emailService.sendCabinetAuditRequestEmail(
                request.getName(),
                request.getTelegram(),
                request.getAdditionalInfo()
        );
        return ResponseEntity.ok(new MessageResponse("Запрос отправлен. Мы свяжемся с вами в Telegram."));
    }

    /**
     * Принимает заявку на консультацию по ведению рекламных кабинетов.
     */
    @PostMapping("/agency-consultation-request")
    public ResponseEntity<MessageResponse> submitAgencyConsultationRequest(
            @Valid @RequestBody LandingContactRequestDto request
    ) {
        emailService.sendAgencyConsultationRequestEmail(
                request.getName(),
                request.getTelegram(),
                request.getAdditionalInfo()
        );
        return ResponseEntity.ok(new MessageResponse("Запрос отправлен. Мы свяжемся с вами в Telegram."));
    }
}
