package ru.oparin.solution.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.oparin.solution.dto.BugReportRequestDto;
import ru.oparin.solution.dto.MessageResponse;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.User;
import ru.oparin.solution.service.EmailService;
import ru.oparin.solution.service.UserService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Обратная связь из сервиса: баги, замечания, предложения.
 */
@RestController
@RequestMapping("/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final UserService userService;
    private final EmailService emailService;

    private static final int MAX_IMAGES = 5;
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "image/bmp"
    );

    /**
     * Принимает сообщение пользователя и отправляет его на корпоративную почту.
     * Скриншоты приходят как {@code files} (необязательно).
     *
     * @param request        текст и адрес страницы
     * @param files          прикреплённые изображения
     * @param authentication текущий пользователь
     * @return подтверждение отправки
     */
    @PostMapping(value = "/bug-report", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MessageResponse> submitBugReport(
            @ModelAttribute BugReportRequestDto request,
            @RequestParam(value = "files", required = false) MultipartFile[] files,
            Authentication authentication
    ) {
        User user = userService.findByEmail(authentication.getName());
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new UserException("Аккаунт деактивирован", HttpStatus.UNAUTHORIZED);
        }
        validateMessage(request);
        List<MultipartFile> images = collectImages(files);
        emailService.sendBugReportEmail(user, request.getMessage().trim(), request.getPageUrl(), images);
        return ResponseEntity.ok(MessageResponse.builder()
                .message("Сообщение отправлено. Спасибо!")
                .build());
    }

    /**
     * Проверяет текст обратной связи.
     *
     * @param request поля формы
     */
    private void validateMessage(BugReportRequestDto request) {
        String message = request.getMessage() == null ? "" : request.getMessage().trim();
        if (message.isEmpty()) {
            throw new UserException("Напишите сообщение", HttpStatus.BAD_REQUEST);
        }
        if (message.length() > 4000) {
            throw new UserException("Сообщение слишком длинное", HttpStatus.BAD_REQUEST);
        }
        String pageUrl = request.getPageUrl();
        if (pageUrl != null && pageUrl.length() > 500) {
            throw new UserException("Слишком длинный адрес страницы", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Отбирает непустые изображения и проверяет тип, размер и количество.
     *
     * @param files загруженные части запроса
     * @return список вложений для письма
     */
    private List<MultipartFile> collectImages(MultipartFile[] files) {
        List<MultipartFile> images = new ArrayList<>();
        if (files == null) {
            return images;
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            validateImage(file);
            images.add(file);
        }
        if (images.size() > MAX_IMAGES) {
            throw new UserException("Можно прикрепить не больше 5 изображений", HttpStatus.BAD_REQUEST);
        }
        return images;
    }

    /**
     * Проверяет, что файл — изображение допустимого типа и размера.
     *
     * @param file загруженный файл
     */
    private void validateImage(MultipartFile file) {
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new UserException("Изображение больше 5 МБ", HttpStatus.BAD_REQUEST);
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        boolean typeAllowed = ALLOWED_IMAGE_TYPES.contains(contentType);
        boolean extensionAllowed = name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".png")
                || name.endsWith(".gif")
                || name.endsWith(".webp")
                || name.endsWith(".bmp");
        if (!typeAllowed && !extensionAllowed) {
            throw new UserException("Можно прикрепить только изображения (JPG, PNG, GIF, WebP, BMP)", HttpStatus.BAD_REQUEST);
        }
    }
}
