package ru.oparin.solution.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.oparin.solution.dto.abtest.*;
import ru.oparin.solution.model.CabinetAccessSection;
import ru.oparin.solution.service.SellerContextService;
import ru.oparin.solution.service.abtest.AbTestService;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * API А/Б-тестов главного фото карточки WB.
 */
@RestController
@RequestMapping("/advertising/ab-tests")
@RequiredArgsConstructor
public class AbTestController {

    private final SellerContextService sellerContextService;
    private final AbTestService abTestService;
    private final ObjectMapper objectMapper;

    /**
     * Список А/Б-тестов кабинета.
     *
     * @param activeOnly если true — только включённые
     */
    @GetMapping
    public ResponseEntity<List<AbTestDto>> list(
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly,
            Authentication authentication
    ) {
        SellerContextService.SellerContext ctx = sellerContextService.createContext(
                authentication, sellerId, cabinetId, CabinetAccessSection.AD_CAMPAIGNS);
        if (ctx.cabinet() == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(abTestService.list(ctx.cabinet().getId(), activeOnly));
    }

    /**
     * Деталка А/Б-теста.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AbTestDto> get(
            @PathVariable Long id,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            Authentication authentication
    ) {
        SellerContextService.SellerContext ctx = sellerContextService.createContext(
                authentication, sellerId, cabinetId, CabinetAccessSection.AD_CAMPAIGNS);
        if (ctx.cabinet() == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(abTestService.get(ctx.cabinet().getId(), id));
    }

    /**
     * Изменение настроек ротации / остановки / поведения по завершении.
     */
    @PatchMapping("/{id}/settings")
    public ResponseEntity<AbTestDto> updateSettings(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAbTestSettingsRequest body,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            Authentication authentication
    ) {
        SellerContextService.SellerContext ctx = sellerContextService.createContext(
                authentication, sellerId, cabinetId, CabinetAccessSection.AD_CAMPAIGNS);
        if (ctx.cabinet() == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(abTestService.updateSettings(ctx.cabinet().getId(), id, body));
    }

    /**
     * Локальный файл варианта (загруженный пользователем / скачанный control) — стабильное превью для UI.
     */
    @GetMapping("/{id}/variants/{variantId}/image")
    public ResponseEntity<Resource> variantImage(
            @PathVariable Long id,
            @PathVariable Long variantId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            Authentication authentication
    ) {
        SellerContextService.SellerContext ctx = sellerContextService.createContext(
                authentication, sellerId, cabinetId, CabinetAccessSection.AD_CAMPAIGNS);
        if (ctx.cabinet() == null) {
            return ResponseEntity.badRequest().build();
        }
        Path path = abTestService.resolveVariantUiPreviewPath(ctx.cabinet().getId(), id, variantId);
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        MediaType mediaType = MediaType.IMAGE_JPEG;
        if (name.endsWith(".png")) {
            mediaType = MediaType.IMAGE_PNG;
        } else if (name.endsWith(".webp")) {
            mediaType = MediaType.parseMediaType("image/webp");
        } else if (name.endsWith(".gif")) {
            mediaType = MediaType.IMAGE_GIF;
        } else if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".ui.jpg")) {
            mediaType = MediaType.IMAGE_JPEG;
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(org.springframework.http.CacheControl.maxAge(7, java.util.concurrent.TimeUnit.DAYS).cachePrivate())
                .body(new FileSystemResource(path));
    }

    /**
     * Пауза / снятие паузы варианта (исключение из ротации).
     */
    @PatchMapping("/{id}/variants/{variantId}/pause")
    public ResponseEntity<AbTestDto> setVariantPaused(
            @PathVariable Long id,
            @PathVariable Long variantId,
            @Valid @RequestBody AbTestVariantPauseRequest body,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            Authentication authentication
    ) {
        SellerContextService.SellerContext ctx = sellerContextService.createContext(
                authentication, sellerId, cabinetId, CabinetAccessSection.AD_CAMPAIGNS);
        if (ctx.cabinet() == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(
                abTestService.setVariantPaused(ctx.cabinet().getId(), id, variantId, Boolean.TRUE.equals(body.getPaused()))
        );
    }

    /**
     * Создание А/Б-теста: JSON в части {@code request} + файлы вариантов в {@code files}.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AbTestDto> create(
            @RequestPart("request") String requestJson,
            @RequestPart(value = "files", required = false) MultipartFile[] files,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            Authentication authentication
    ) throws Exception {
        SellerContextService.SellerContext ctx = sellerContextService.createContext(
                authentication, sellerId, cabinetId, CabinetAccessSection.AD_CAMPAIGNS);
        if (ctx.cabinet() == null) {
            return ResponseEntity.badRequest().build();
        }
        CreateAbTestRequest request = objectMapper.readValue(requestJson, CreateAbTestRequest.class);
        List<MultipartFile> fileList = files != null ? Arrays.asList(files) : List.of();
        AbTestDto created = abTestService.create(ctx.cabinet().getId(), request, fileList);
        return ResponseEntity.ok(created);
    }

    /**
     * Включение / отключение теста (отключение завершает тест).
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<AbTestDto> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody AbTestStatusUpdateRequest body,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            Authentication authentication
    ) {
        SellerContextService.SellerContext ctx = sellerContextService.createContext(
                authentication, sellerId, cabinetId, CabinetAccessSection.AD_CAMPAIGNS);
        if (ctx.cabinet() == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(abTestService.updateStatus(ctx.cabinet().getId(), id, body.getStatus()));
    }
}
