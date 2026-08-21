package ru.oparin.solution.service.abtest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.oparin.solution.dto.abtest.WbAbTestDto;
import ru.oparin.solution.dto.abtest.WbAbTestVariantDto;
import ru.oparin.solution.dto.abtest.WbCreateAbTestRequest;
import ru.oparin.solution.dto.abtest.WbUpdateAbTestSettingsRequest;
import ru.oparin.solution.dto.wb.WbCardDto;
import ru.oparin.solution.dto.wb.WbCardsListRequest;
import ru.oparin.solution.dto.wb.WbCardsListResponse;
import ru.oparin.solution.exception.WbApiUnauthorizedScopeException;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.*;
import ru.oparin.solution.service.WbAbTestQuotaService;
import ru.oparin.solution.service.events.WbApiEventService;
import ru.oparin.solution.service.events.payload.WbAbTestStartPayload;
import ru.oparin.solution.service.events.payload.WbAbTestStartStep;
import ru.oparin.solution.service.wb.WbContentApiClient;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CRUD и бизнес-операции А/Б-тестов главного фото.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WbAbTestService {

    private static final int MIN_INTERVAL_MINUTES = 30;
    private static final int MAX_INTERVAL_MINUTES = 24 * 60;
    private static final long MAX_UPLOAD_BYTES = 32L * 1024 * 1024;
    /** Длинная сторона UI-превью варианта (px). */
    private static final int UI_PREVIEW_MAX_SIDE = 720;
    /** Качество JPEG UI-превью (0..1). */
    private static final float UI_PREVIEW_JPEG_QUALITY = 0.82f;

    /**
     * Сообщение для UI: токен без права записи в Content (типичный 401 на media/file).
     */
    public static final String TOKEN_CONTENT_WRITE_REQUIRED =
            "Токен кабинета не подходит: нужен доступ на чтение и запись к категории «Контент» "
                    + "(без записи нельзя загружать фото для А/Б-теста). "
                    + "Создайте новый токен с правом изменения контента и обновите ключ в кабинете.";

    /**
     * {@code true}, если ошибка похожа на 401 / отказ токена WB.
     */
    public static boolean isWbUnauthorizedTokenError(Throwable error) {
        if (error instanceof WbApiUnauthorizedScopeException) {
            return true;
        }
        return isWbUnauthorizedTokenMessage(error != null ? error.getMessage() : null);
    }

    /**
     * {@code true}, если текст ошибки указывает на 401 Unauthorized.
     */
    public static boolean isWbUnauthorizedTokenMessage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("401") || lower.contains("unauthorized");
    }

    private final WbAbTestRepository abTestRepository;
    private final WbAbTestCampaignRepository abTestCampaignRepository;
    private final WbAbTestVariantRepository abTestVariantRepository;
    private final WbAbTestRotationLogRepository rotationLogRepository;
    private final WbAbTestStatsSnapshotRepository snapshotRepository;
    private final WbProductCardRepository productCardRepository;
    private final WbPromotionCampaignRepository promotionCampaignRepository;
    private final CabinetRepository cabinetRepository;
    private final WbContentApiClient contentApiClient;
    private final WbApiEventService wbApiEventService;
    private final WbAbTestQuotaService abTestQuotaService;
    private final ObjectMapper objectMapper;

    @Value("${app.uploads.directory}")
    private String uploadsDirectory;

    @Value("${app.ab-test.min-views-per-variant:1000}")
    private long minViewsPerVariant;

    @Value("${app.ab-test.leader-relative-lift:0.10}")
    private double leaderRelativeLift;

    /**
     * self-proxy: путь к файлу в короткой TX, сжатие JPEG — вне транзакции.
     */
    @Lazy
    @Autowired
    private WbAbTestService self;

    /**
     * Список тестов кабинета.
     *
     * @param activeOnly если true — только ENABLED
     */
    @Transactional(readOnly = true)
    public List<WbAbTestDto> list(Long cabinetId, boolean activeOnly) {
        List<WbAbTest> tests = activeOnly
                ? abTestRepository.findByCabinetIdOrderByCreatedAtDesc(cabinetId).stream()
                .filter(t -> t.getStatus() == WbAbTestStatus.ENABLED || t.getStatus() == WbAbTestStatus.PENDING_START)
                .collect(Collectors.toList())
                : abTestRepository.findByCabinetIdOrderByCreatedAtDesc(cabinetId);
        for (WbAbTest test : tests) {
            if (test.getStatus() == WbAbTestStatus.PENDING_START
                    && isWbUnauthorizedTokenMessage(test.getLastWbError())) {
                recoverUnauthorizedPendingStart(test.getId());
            }
        }
        // Перечитываем после возможного recover (статус мог стать DISABLED).
        tests = activeOnly
                ? abTestRepository.findByCabinetIdOrderByCreatedAtDesc(cabinetId).stream()
                .filter(t -> t.getStatus() == WbAbTestStatus.ENABLED || t.getStatus() == WbAbTestStatus.PENDING_START)
                .collect(Collectors.toList())
                : abTestRepository.findByCabinetIdOrderByCreatedAtDesc(cabinetId);
        return tests.stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * Деталка теста.
     */
    @Transactional(readOnly = true)
    public WbAbTestDto get(Long cabinetId, Long testId) {
        WbAbTest test = requireTest(cabinetId, testId);
        if (test.getStatus() == WbAbTestStatus.PENDING_START
                && isWbUnauthorizedTokenMessage(test.getLastWbError())) {
            recoverUnauthorizedPendingStart(testId);
            test = requireTest(cabinetId, testId);
        }
        return toDto(test);
    }

    /**
     * Создание теста: файлы на диск, запись в БД со статусом PENDING_START, WB — через очередь событий.
     */
    @Transactional
    public WbAbTestDto create(Long cabinetId, WbCreateAbTestRequest request, List<MultipartFile> variantFiles) {
        validateCreateRequest(request, variantFiles);
        if (abTestRepository.existsByCabinetIdAndNmIdAndStatusIn(
                cabinetId,
                request.getNmId(),
                List.of(WbAbTestStatus.ENABLED, WbAbTestStatus.PENDING_START))) {
            throw new IllegalArgumentException("Для этого артикула уже есть активный или запускающийся А/Б-тест");
        }

        WbProductCard card = productCardRepository.findByNmIdAndCabinet_Id(request.getNmId(), cabinetId)
                .orElseThrow(() -> new IllegalArgumentException("Артикул не найден в кабинете"));

        for (Long advertId : request.getAdvertIds()) {
            promotionCampaignRepository.findByAdvertIdAndCabinet_Id(advertId, cabinetId)
                    .orElseThrow(() -> new IllegalArgumentException("Кампания не найдена в кабинете: " + advertId));
        }

        Cabinet cabinet = cabinetRepository.findById(cabinetId)
                .orElseThrow(() -> new IllegalArgumentException("Кабинет не найден"));
        if (cabinet.getApiKey() == null || cabinet.getApiKey().isBlank()) {
            throw new IllegalArgumentException("У кабинета отсутствует API-ключ");
        }
        if (!abTestQuotaService.canStartWbAbTest(cabinet)) {
            throw new ru.oparin.solution.exception.UserException(
                    "Недостаточно квоты А/Б тестов. Купите пакет, чтобы создать тест.",
                    org.springframework.http.HttpStatus.PAYMENT_REQUIRED
            );
        }
        // Резервируем квоту при создании; при failStart вернём.
        abTestQuotaService.consumeStart(cabinet);
        validateTokenAllowsRotation(cabinet, request);

        // Без синхронного WB: берём фото из нашей БД; уточнение галереи — в AB_TEST_START.
        String mainUrl = firstNonBlank(card.getPhotoC246x328(), card.getPhotoTm());
        if (mainUrl == null || mainUrl.isBlank()) {
            throw new IllegalArgumentException("У карточки нет главного фото");
        }

        LocalDateTime now = LocalDateTime.now();
        WbAbTest test = WbAbTest.builder()
                .cabinetId(cabinetId)
                .nmId(request.getNmId())
                .status(WbAbTestStatus.PENDING_START)
                .rotationMode(request.getRotationMode())
                .rotationViewsThreshold(request.getRotationViewsThreshold())
                .rotationIntervalMinutes(request.getRotationIntervalMinutes())
                .stopMode(request.getStopMode())
                .durationDays(request.getDurationDays())
                .endsAt(request.getStopMode() == WbAbTestStopMode.BY_DURATION
                        ? now.plusDays(request.getDurationDays())
                        : null)
                .finishAction(request.getFinishAction())
                .originalMainPhotoUrl(mainUrl)
                .originalGalleryUrlsJson("[]")
                .startedAt(now)
                .lastRotatedAt(now)
                .insightCode(WbAbTestInsightCode.DATA_LOW)
                .lastWbError(null)
                .build();
        test = abTestRepository.save(test);

        for (Long advertId : request.getAdvertIds()) {
            abTestCampaignRepository.save(WbAbTestCampaign.builder()
                    .abTestId(test.getId())
                    .advertId(advertId)
                    .build());
        }

        WbAbTestVariant control = abTestVariantRepository.save(WbAbTestVariant.builder()
                .abTestId(test.getId())
                .sortOrder(1)
                .control(true)
                .photoUrl(mainUrl)
                .previewUrl(mainUrl)
                .build());

        int order = 2;
        int uploadedCount = 0;
        if (variantFiles != null) {
            for (MultipartFile file : variantFiles) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                String storedName = storeUpload(file);
                abTestVariantRepository.save(WbAbTestVariant.builder()
                        .abTestId(test.getId())
                        .sortOrder(order++)
                        .control(false)
                        .storedFileName(storedName)
                        .photoUrl(null)
                        .previewUrl(null)
                        .build());
                uploadedCount++;
            }
        }
        if (uploadedCount < 1) {
            throw new IllegalArgumentException("Загрузите хотя бы один дополнительный вариант фото");
        }

        test.setActiveVariantId(control.getId());
        test.setActiveSinceViews(0);
        abTestRepository.save(test);
        rotationLogRepository.save(WbAbTestRotationLog.builder()
                .abTestId(test.getId())
                .variantId(control.getId())
                .switchedAt(now)
                .reason("CREATE")
                .build());

        wbApiEventService.enqueueWbAbTestStart(cabinetId, test.getId(), "AB_TEST_CREATE");
        return toDto(test);
    }

    /**
     * Изменение настроек ротации / остановки / поведения по завершении.
     * Доступно для {@link WbAbTestStatus#ENABLED} и {@link WbAbTestStatus#PENDING_START}.
     */
    @Transactional
    public WbAbTestDto updateSettings(Long cabinetId, Long testId, WbUpdateAbTestSettingsRequest request) {
        WbAbTest test = requireTest(cabinetId, testId);
        if (test.getStatus() == WbAbTestStatus.DISABLED) {
            throw new IllegalArgumentException("Нельзя изменить настройки завершённого теста");
        }
        validateSettingsFields(
                request.getRotationMode(),
                request.getRotationViewsThreshold(),
                request.getRotationIntervalMinutes(),
                request.getStopMode(),
                request.getDurationDays()
        );
        Cabinet cabinet = cabinetRepository.findById(cabinetId)
                .orElseThrow(() -> new IllegalArgumentException("Кабинет не найден"));
        validateTokenAllowsInterval(cabinet, request.getRotationMode(), request.getRotationIntervalMinutes());

        applySettings(test, request);
        abTestRepository.save(test);
        return toDto(test);
    }

    /**
     * Применяет поля настроек к сущности и пересчитывает {@code endsAt}.
     * Срок BY_DURATION считается от {@code startedAt} (длительность теста с момента запуска).
     */
    private void applySettings(WbAbTest test, WbUpdateAbTestSettingsRequest request) {
        test.setRotationMode(request.getRotationMode());
        if (request.getRotationMode() == WbAbTestRotationMode.ROTATION_BY_VIEWS) {
            test.setRotationViewsThreshold(request.getRotationViewsThreshold());
            test.setRotationIntervalMinutes(null);
        } else {
            test.setRotationIntervalMinutes(request.getRotationIntervalMinutes());
            test.setRotationViewsThreshold(null);
        }

        test.setStopMode(request.getStopMode());
        if (request.getStopMode() == WbAbTestStopMode.BY_DURATION) {
            test.setDurationDays(request.getDurationDays());
            LocalDateTime anchor = test.getStartedAt() != null ? test.getStartedAt() : LocalDateTime.now();
            test.setEndsAt(anchor.plusDays(request.getDurationDays()));
        } else {
            test.setDurationDays(null);
            test.setEndsAt(null);
        }

        test.setFinishAction(request.getFinishAction());
    }

    /**
     * Включение или ручное отключение теста.
     * Перезапуск DISABLED возможен только если тест не дошёл до ENABLED ({@code failedAtStart}).
     */
    @Transactional
    public WbAbTestDto updateStatus(Long cabinetId, Long testId, WbAbTestStatus status) {
        WbAbTest test = requireTest(cabinetId, testId);
        if (status == WbAbTestStatus.DISABLED
                && (test.getStatus() == WbAbTestStatus.ENABLED || test.getStatus() == WbAbTestStatus.PENDING_START)) {
            if (test.getStatus() == WbAbTestStatus.PENDING_START) {
                test.setStatus(WbAbTestStatus.DISABLED);
                test.setFinishedAt(LocalDateTime.now());
                test.setFailedAtStart(true);
                abTestRepository.save(test);
                refundQuotaIfNeeded(cabinetId);
            } else {
                enqueueFinish(test, "MANUAL");
            }
        } else if (status == WbAbTestStatus.ENABLED && test.getStatus() == WbAbTestStatus.DISABLED) {
            if (!Boolean.TRUE.equals(test.getFailedAtStart())) {
                throw new IllegalArgumentException("Повторный запуск завершённого теста не поддерживается — создайте новый");
            }
            return restartFailedStart(cabinetId, test);
        }
        return toDto(test);
    }

    /**
     * Перезапуск теста, упавшего на старте (после смены токена и т.п.).
     */
    private WbAbTestDto restartFailedStart(Long cabinetId, WbAbTest test) {
        if (abTestRepository.existsByCabinetIdAndNmIdAndStatusIn(
                cabinetId,
                test.getNmId(),
                List.of(WbAbTestStatus.ENABLED, WbAbTestStatus.PENDING_START))) {
            throw new IllegalArgumentException("Для этого артикула уже есть активный или запускающийся А/Б-тест");
        }
        Cabinet cabinet = cabinetRepository.findById(cabinetId)
                .orElseThrow(() -> new IllegalArgumentException("Кабинет не найден"));
        if (cabinet.getApiKey() == null || cabinet.getApiKey().isBlank()) {
            throw new IllegalArgumentException("У кабинета отсутствует API-ключ");
        }
        if (!abTestQuotaService.canStartWbAbTest(cabinet)) {
            throw new ru.oparin.solution.exception.UserException(
                    "Недостаточно квоты А/Б тестов. Купите пакет, чтобы перезапустить тест.",
                    org.springframework.http.HttpStatus.PAYMENT_REQUIRED
            );
        }
        abTestQuotaService.consumeStart(cabinet);

        LocalDateTime now = LocalDateTime.now();
        test.setStatus(WbAbTestStatus.PENDING_START);
        test.setFailedAtStart(false);
        test.setLastWbError(null);
        test.setFinishedAt(null);
        test.setStartedAt(now);
        test.setLastRotatedAt(now);
        test.setInsightCode(WbAbTestInsightCode.DATA_LOW);
        if (test.getStopMode() == WbAbTestStopMode.BY_DURATION && test.getDurationDays() != null) {
            test.setEndsAt(now.plusDays(test.getDurationDays()));
        }
        abTestRepository.save(test);

        wbApiEventService.enqueueWbAbTestStart(cabinetId, test.getId(), "AB_TEST_RESTART");
        return toDto(test);
    }

    /**
     * Поставить в очередь завершение теста (смена фото + DISABLED).
     */
    @Transactional
    public void enqueueFinish(WbAbTest test, String reason) {
        if (test.getStatus() != WbAbTestStatus.ENABLED) {
            return;
        }
        List<WbAbTestVariant> variants = abTestVariantRepository.findByAbTestIdOrderBySortOrderAsc(test.getId());
        WbAbTestVariant target;
        if (test.getFinishAction() == WbAbTestFinishAction.KEEP_WINNER) {
            List<WbAbTestVariant> pool = variants.stream().filter(v -> !v.isPaused()).toList();
            if (pool.isEmpty()) {
                pool = variants;
            }
            target = pool.stream()
                    .max(Comparator.comparing(WbAbTestVariant::computeCtr).thenComparing(WbAbTestVariant::getViews))
                    .orElse(variants.get(0));
        } else {
            target = variants.stream().filter(WbAbTestVariant::isControl).findFirst()
                    .orElse(variants.get(0));
        }
        wbApiEventService.enqueueWbAbTestApplyPhoto(
                test.getCabinetId(),
                test.getId(),
                target.getId(),
                "FINISH:" + reason,
                true,
                "AB_TEST_FINISH"
        );
    }

    /**
     * @deprecated используйте {@link #enqueueFinish(WbAbTest, String)}
     */
    @Transactional
    public void finishTest(WbAbTest test, String reason) {
        enqueueFinish(test, reason);
    }

    /**
     * Поставить в очередь ротацию на следующий незапауженный вариант.
     */
    @Transactional
    public void enqueueRotateToNext(WbAbTest test, String reason) {
        List<WbAbTestVariant> variants = abTestVariantRepository.findByAbTestIdOrderBySortOrderAsc(test.getId());
        List<WbAbTestVariant> activePool = variants.stream().filter(v -> !v.isPaused()).toList();
        if (activePool.size() < 2) {
            return;
        }
        int currentIdx = 0;
        for (int i = 0; i < activePool.size(); i++) {
            if (activePool.get(i).getId().equals(test.getActiveVariantId())) {
                currentIdx = i;
                break;
            }
        }
        // Если текущий на паузе / не в пуле — берём первый активный; иначе следующий по кругу.
        boolean currentInPool = activePool.stream().anyMatch(v -> v.getId().equals(test.getActiveVariantId()));
        WbAbTestVariant next = currentInPool
                ? activePool.get((currentIdx + 1) % activePool.size())
                : activePool.get(0);
        if (next.getId().equals(test.getActiveVariantId())) {
            return;
        }
        wbApiEventService.enqueueWbAbTestApplyPhoto(
                test.getCabinetId(),
                test.getId(),
                next.getId(),
                reason,
                false,
                "AB_TEST_ROTATE"
        );
    }

    /**
     * Пауза / снятие паузы варианта во время работы теста.
     * На паузе вариант не участвует в ротации; если паузим активный на ВБ — сразу ротация на другой.
     */
    @Transactional
    public WbAbTestDto setVariantPaused(Long cabinetId, Long testId, Long variantId, boolean paused) {
        WbAbTest test = requireTest(cabinetId, testId);
        if (test.getStatus() != WbAbTestStatus.ENABLED) {
            throw new IllegalArgumentException("Пауза варианта доступна только для включённого теста");
        }
        WbAbTestVariant variant = abTestVariantRepository.findByIdAndAbTestId(variantId, testId)
                .orElseThrow(() -> new IllegalArgumentException("Вариант не найден"));
        if (variant.isPaused() == paused) {
            return toDto(test);
        }
        if (paused) {
            long remainingActive = abTestVariantRepository.findByAbTestIdOrderBySortOrderAsc(testId).stream()
                    .filter(v -> !v.isPaused() && !v.getId().equals(variantId))
                    .count();
            if (remainingActive < 1) {
                throw new IllegalArgumentException("Нельзя поставить на паузу последний активный вариант");
            }
        }
        variant.setPaused(paused);
        abTestVariantRepository.save(variant);

        if (paused && variant.getId().equals(test.getActiveVariantId())) {
            enqueueRotateToNext(test, "PAUSE_VARIANT");
        }
        return toDto(test);
    }

    /**
     * @deprecated используйте {@link #enqueueRotateToNext(WbAbTest, String)}
     */
    @Transactional
    public void rotateToNext(WbAbTest test, String reason) {
        enqueueRotateToNext(test, reason);
    }

    /**
     * Один шаг старта А/Б в отдельной транзакции: успех коммитится до следующего события очереди.
     * При rate-limit defer откатывается только текущий шаг, уже выполненные шаги сохраняются.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processStartStepInNewTransaction(Long cabinetId, WbAbTestStartPayload payload, String triggerSource) {
        Long abTestId = payload.abTestId();
        WbAbTestStartStep step = payload.resolvedStep();
        WbAbTest test = abTestRepository.findById(abTestId)
                .orElseThrow(() -> new IllegalArgumentException("А/Б-тест не найден: " + abTestId));
        if (!Objects.equals(test.getCabinetId(), cabinetId)) {
            throw new IllegalArgumentException("А/Б-тест не принадлежит кабинету события");
        }
        if (test.getStatus() != WbAbTestStatus.PENDING_START) {
            log.info("Пропуск шага А/Б-старта: testId={}, step={}, status={}", abTestId, step, test.getStatus());
            return;
        }
        Cabinet cabinet = cabinetRepository.findById(cabinetId)
                .orElseThrow(() -> new IllegalArgumentException("Кабинет не найден"));
        String source = triggerSource != null ? triggerSource : "AB_TEST_START";

        log.info("Шаг А/Б-старта: testId={}, step={}, variantId={}", abTestId, step, payload.variantId());
        switch (step) {
            case RESOLVE_CARD -> executeResolveCardStep(cabinet, test, source);
            case UPLOAD_VARIANT -> executeUploadVariantStep(cabinet, test, payload.variantId(), source);
            case REFRESH_URLS -> executeRefreshUrlsStep(cabinet, test, source);
            case RESTORE_GALLERY -> executeRestoreGalleryStep(cabinet, test, source);
            case APPLY_CONTROL -> executeApplyControlStep(cabinet, test, source);
            default -> throw new IllegalStateException("Неизвестный шаг А/Б-старта: " + step);
        }
    }

    private void executeResolveCardStep(Cabinet cabinet, WbAbTest test, String triggerSource) {
        List<WbAbTestVariant> variants = abTestVariantRepository.findByAbTestIdOrderBySortOrderAsc(test.getId());
        WbAbTestVariant control = variants.stream().filter(WbAbTestVariant::isControl).findFirst()
                .orElseThrow(() -> new IllegalStateException("Нет control-варианта"));

        boolean alreadyResolved = test.getOriginalMainPhotoUrl() != null
                && !test.getOriginalMainPhotoUrl().isBlank()
                && control.getStoredFileName() != null
                && !control.getStoredFileName().isBlank();
        if (!alreadyResolved) {
            CardPhotos photos = resolveCardPhotos(cabinet.getApiKey(), test.getNmId());
            if (photos.mainUrl() == null || photos.mainUrl().isBlank()) {
                throw new IllegalStateException("Не удалось получить главное фото карточки для контрольного варианта");
            }
            test.setOriginalMainPhotoUrl(photos.mainUrl());
            test.setOriginalGalleryUrlsJson(writeJson(photos.galleryUrls()));
            abTestRepository.save(test);

            String controlStoredName = downloadAndStoreFromUrl(photos.mainUrl());
            control.setStoredFileName(controlStoredName);
            control.setPhotoUrl(photos.mainUrl());
            control.setPreviewUrl(photos.previewUrl() != null ? photos.previewUrl() : photos.mainUrl());
            abTestVariantRepository.save(control);
        }

        continueStartWithoutOverwritingGallery(cabinet.getId(), test.getId(), triggerSource);
    }

    /**
     * Варианты больше не пишем в слоты 2+ карточки: это затирает галерею.
     * Если старт уже успел загрузить варианты (флаг {@code wbUploaded}) — откатываем галерею.
     */
    private void continueStartWithoutOverwritingGallery(Long cabinetId, Long abTestId, String triggerSource) {
        List<WbAbTestVariant> variants = abTestVariantRepository.findByAbTestIdOrderBySortOrderAsc(abTestId);
        boolean galleryTouched = variants.stream().anyMatch(v -> !v.isControl() && v.isWbUploaded());
        WbAbTestStartStep next = galleryTouched ? WbAbTestStartStep.RESTORE_GALLERY : WbAbTestStartStep.APPLY_CONTROL;
        wbApiEventService.enqueueNextWbAbTestStartStep(
                cabinetId,
                WbAbTestStartPayload.builder()
                        .abTestId(abTestId)
                        .step(next)
                        .build(),
                triggerSource
        );
    }

    private void executeUploadVariantStep(Cabinet cabinet, WbAbTest test, Long variantId, String triggerSource) {
        log.info("Пропуск загрузки варианта в слоты 2+: testId={}, variantId={}", test.getId(), variantId);
        continueStartWithoutOverwritingGallery(cabinet.getId(), test.getId(), triggerSource);
    }

    private void executeRefreshUrlsStep(Cabinet cabinet, WbAbTest test, String triggerSource) {
        continueStartWithoutOverwritingGallery(cabinet.getId(), test.getId(), triggerSource);
    }

    private void executeRestoreGalleryStep(Cabinet cabinet, WbAbTest test, String triggerSource) {
        restoreOriginalMediaSet(cabinet.getApiKey(), test);
        wbApiEventService.enqueueNextWbAbTestStartStep(
                cabinet.getId(),
                WbAbTestStartPayload.builder()
                        .abTestId(test.getId())
                        .step(WbAbTestStartStep.APPLY_CONTROL)
                        .build(),
                triggerSource
        );
    }

    private void executeApplyControlStep(Cabinet cabinet, WbAbTest test, String triggerSource) {
        List<WbAbTestVariant> variants = abTestVariantRepository.findByAbTestIdOrderBySortOrderAsc(test.getId());
        WbAbTestVariant control = variants.stream().filter(WbAbTestVariant::isControl).findFirst()
                .orElseThrow(() -> new IllegalStateException("Нет control-варианта"));
        applyMainPhoto(cabinet.getApiKey(), test, control);

        test.setActiveVariantId(control.getId());
        test.setActiveSinceViews(0);
        test.setStatus(WbAbTestStatus.ENABLED);
        test.setFailedAtStart(false);
        test.setLastWbError(null);
        test.setLastRotatedAt(LocalDateTime.now());
        abTestRepository.save(test);
        // квота уже списана при create
        wbApiEventService.enqueueWbAbTestStatsPoll(test.getCabinetId(), test.getId(), triggerSource);
    }

    /**
     * Выполнить смену главного фото (ротация или финиш) из event executor.
     */
    @Transactional
    public void executeApplyPhoto(Long abTestId, Long variantId, String reason, boolean finishAfterApply) {
        WbAbTest test = abTestRepository.findById(abTestId)
                .orElseThrow(() -> new IllegalArgumentException("А/Б-тест не найден: " + abTestId));
        if (finishAfterApply) {
            if (test.getStatus() != WbAbTestStatus.ENABLED) {
                return;
            }
        } else if (test.getStatus() != WbAbTestStatus.ENABLED) {
            return;
        }
        Cabinet cabinet = cabinetRepository.findById(test.getCabinetId())
                .orElseThrow(() -> new IllegalArgumentException("Кабинет не найден"));
        WbAbTestVariant variant = abTestVariantRepository.findByIdAndAbTestId(variantId, abTestId)
                .orElseThrow(() -> new IllegalArgumentException("Вариант не найден"));

        applyMainPhoto(cabinet.getApiKey(), test, variant);
        test.setActiveVariantId(variant.getId());
        test.setActiveSinceViews(variant.getViews());
        test.setLastRotatedAt(LocalDateTime.now());
        test.setLastWbError(null);

        if (finishAfterApply) {
            List<WbAbTestVariant> variants = abTestVariantRepository.findByAbTestIdOrderBySortOrderAsc(test.getId());
            test.setStatus(WbAbTestStatus.DISABLED);
            test.setFinishedAt(LocalDateTime.now());
            updateInsight(test, variants);
        }
        abTestRepository.save(test);
        rotationLogRepository.save(WbAbTestRotationLog.builder()
                .abTestId(test.getId())
                .variantId(variant.getId())
                .switchedAt(LocalDateTime.now())
                .reason(reason != null ? reason : "APPLY")
                .build());
    }

    /**
     * Зафиксировать ошибку WB по тесту (для UI).
     * При 401 / отказе токена сразу завершаем PENDING_START — ретраи бессмысленны.
     */
    @Transactional
    public void markWbError(Long abTestId, String error) {
        if (isWbUnauthorizedTokenMessage(error)) {
            failStart(abTestId, TOKEN_CONTENT_WRITE_REQUIRED);
            return;
        }
        abTestRepository.findById(abTestId).ifPresent(test -> {
            test.setLastWbError(error != null && error.length() > 2000 ? error.substring(0, 2000) : error);
            if (test.getStatus() == WbAbTestStatus.PENDING_START) {
                // оставляем PENDING_START — ретраи события; после финального фейла executor вызовет failStart
            }
            abTestRepository.save(test);
        });
    }

    /**
     * Если старт завис в PENDING_START после 401 (событие уже FINAL, failStart не вызвали) — закрываем тест.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recoverUnauthorizedPendingStart(Long abTestId) {
        abTestRepository.findById(abTestId).ifPresent(test -> {
            if (test.getStatus() == WbAbTestStatus.PENDING_START
                    && isWbUnauthorizedTokenMessage(test.getLastWbError())) {
                failStart(abTestId, TOKEN_CONTENT_WRITE_REQUIRED);
            }
        });
    }

    /**
     * Финальный провал старта: тест отключается с ошибкой, квота возвращается.
     */
    @Transactional
    public void failStart(Long abTestId, String error) {
        abTestRepository.findById(abTestId).ifPresent(test -> {
            if (test.getStatus() == WbAbTestStatus.PENDING_START) {
                test.setStatus(WbAbTestStatus.DISABLED);
                test.setFinishedAt(LocalDateTime.now());
                test.setFailedAtStart(true);
                test.setLastWbError(error);
                abTestRepository.save(test);
                refundQuotaIfNeeded(test.getCabinetId());
            }
        });
    }

    private void refundQuotaIfNeeded(Long cabinetId) {
        cabinetRepository.findById(cabinetId).ifPresent(cabinet -> {
            if (!cabinetEntitlementServiceHasUnlimited(cabinet)) {
                abTestQuotaService.addCredits(cabinet, 1);
            }
        });
    }

    private boolean cabinetEntitlementServiceHasUnlimited(Cabinet cabinet) {
        // избегаем лишней зависимости в сигнатуре — через quota DTO
        return Boolean.TRUE.equals(abTestQuotaService.getQuotaDto(cabinet).getUnlimited());
    }

    /**
     * Пересчёт статусной строки по накопленным метрикам.
     */
    @Transactional
    public void refreshInsight(WbAbTest test) {
        List<WbAbTestVariant> variants = abTestVariantRepository.findByAbTestIdOrderBySortOrderAsc(test.getId());
        updateInsight(test, variants);
        abTestRepository.save(test);
    }

    /**
     * Проверка автостопа TRUST_US: достаточно данных и есть лидер / нет разницы.
     * Учитываются только варианты не на паузе.
     *
     * @return true если тест нужно завершить
     */
    public boolean shouldAutoStopTrustUs(WbAbTest test, List<WbAbTestVariant> variants) {
        List<WbAbTestVariant> pool = variants.stream().filter(v -> !v.isPaused()).toList();
        if (pool.size() < 2) {
            return false;
        }
        boolean enough = pool.stream().allMatch(v -> v.getViews() >= minViewsPerVariant);
        if (!enough) {
            return false;
        }
        WbAbTestVariant best = pool.stream().max(Comparator.comparing(WbAbTestVariant::computeCtr)).orElse(null);
        WbAbTestVariant second = pool.stream()
                .filter(v -> best == null || !v.getId().equals(best.getId()))
                .max(Comparator.comparing(WbAbTestVariant::computeCtr))
                .orElse(null);
        if (best == null || second == null) {
            return false;
        }
        BigDecimal bestCtr = best.computeCtr();
        BigDecimal secondCtr = second.computeCtr();
        if (bestCtr.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        double lift = bestCtr.subtract(secondCtr)
                .divide(bestCtr, 6, RoundingMode.HALF_UP)
                .doubleValue();
        return lift >= leaderRelativeLift || lift < leaderRelativeLift / 2.0;
    }

    private void updateInsight(WbAbTest test, List<WbAbTestVariant> variants) {
        List<WbAbTestVariant> pool = variants.stream().filter(v -> !v.isPaused()).toList();
        boolean enough = pool.size() >= 2 && pool.stream().allMatch(v -> v.getViews() >= minViewsPerVariant);
        if (!enough) {
            test.setInsightCode(WbAbTestInsightCode.DATA_LOW);
            return;
        }
        WbAbTestVariant best = pool.stream().max(Comparator.comparing(WbAbTestVariant::computeCtr)).orElse(null);
        WbAbTestVariant second = pool.stream()
                .filter(v -> best == null || !v.getId().equals(best.getId()))
                .max(Comparator.comparing(WbAbTestVariant::computeCtr))
                .orElse(null);
        if (best == null || second == null) {
            test.setInsightCode(WbAbTestInsightCode.DATA_LOW);
            return;
        }
        BigDecimal bestCtr = best.computeCtr();
        if (bestCtr.compareTo(BigDecimal.ZERO) <= 0) {
            test.setInsightCode(WbAbTestInsightCode.NO_DIFF);
            return;
        }
        double lift = bestCtr.subtract(second.computeCtr())
                .divide(bestCtr, 6, RoundingMode.HALF_UP)
                .doubleValue();
        test.setInsightCode(lift >= leaderRelativeLift ? WbAbTestInsightCode.HAS_LEADER : WbAbTestInsightCode.NO_DIFF);
    }

    private void applyMainPhoto(String apiKey, WbAbTest test, WbAbTestVariant variant) {
        if (variant.getStoredFileName() != null && !variant.getStoredFileName().isBlank()) {
            Path path = Paths.get(uploadsDirectory).resolve(variant.getStoredFileName());
            try {
                byte[] bytes = Files.readAllBytes(path);
                contentApiClient.uploadMediaFile(apiKey, test.getNmId(), 1, bytes, variant.getStoredFileName());
                return;
            } catch (IOException e) {
                log.warn("Не удалось прочитать файл варианта id={}: {}", variant.getId(), e.getMessage());
            }
        }
        List<String> urls = buildMediaUrlList(test, variant);
        if (urls.isEmpty()) {
            throw new IllegalStateException("Нет URL для media/save");
        }
        contentApiClient.saveMediaByUrls(apiKey, test.getNmId(), urls);
    }

    /**
     * Восстанавливает исходный набор медиа карточки (главное + галерея) через media/save.
     */
    private void restoreOriginalMediaSet(String apiKey, WbAbTest test) {
        String main = test.getOriginalMainPhotoUrl();
        List<String> gallery = readGallery(test.getOriginalGalleryUrlsJson());
        List<String> urls = new ArrayList<>();
        if (main != null && !main.isBlank()) {
            urls.add(main);
        }
        for (String g : gallery) {
            if (g != null && !g.isBlank() && !g.equals(main)) {
                urls.add(g);
            }
        }
        if (urls.isEmpty()) {
            throw new IllegalStateException("Нет URL исходной галереи для media/save");
        }
        contentApiClient.saveMediaByUrls(apiKey, test.getNmId(), urls);
    }

    private List<String> buildMediaUrlList(WbAbTest test, WbAbTestVariant variant) {
        List<String> gallery = readGallery(test.getOriginalGalleryUrlsJson());
        List<String> urls = new ArrayList<>();
        String main = variant.getPhotoUrl() != null ? variant.getPhotoUrl() : test.getOriginalMainPhotoUrl();
        if (main != null) {
            urls.add(main);
        }
        for (String g : gallery) {
            if (g != null && !g.equals(main)) {
                urls.add(g);
            }
        }
        return urls;
    }

    private CardPhotos resolveCardPhotos(String apiKey, Long nmId) {
        try {
            WbCardsListResponse response = contentApiClient.getCardsList(apiKey, cardsRequestForNm(nmId));
            WbCardDto dto = findCard(response, nmId);
            if (dto != null && dto.getPhotos() != null && !dto.getPhotos().isEmpty()) {
                List<WbCardDto.Photo> photos = dto.getPhotos();
                String main = firstNonBlank(
                        photos.get(0).getBig(),
                        photos.get(0).getHq(),
                        photos.get(0).getC516x688());
                if (main == null) {
                    throw new IllegalStateException(
                            "У карточки nmId=" + nmId + " нет URL big/hq/c516x688 для контрольного фото");
                }
                String preview = firstNonBlank(
                        photos.get(0).getC516x688(),
                        photos.get(0).getC246x328(),
                        photos.get(0).getTm(),
                        main);
                List<String> gallery = new ArrayList<>();
                for (int i = 1; i < photos.size(); i++) {
                    String u = firstNonBlank(
                            photos.get(i).getBig(),
                            photos.get(i).getHq(),
                            photos.get(i).getC516x688(),
                            photos.get(i).getC246x328());
                    if (u != null) {
                        gallery.add(u);
                    }
                }
                return new CardPhotos(main, preview, gallery);
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Не удалось получить фото карточки nmId={} из Content API: {}", nmId, e.getMessage());
            throw new IllegalStateException(
                    "Не удалось получить фото карточки из Content API для контрольного варианта: " + e.getMessage(),
                    e);
        }
        throw new IllegalStateException("Content API не вернул фото карточки nmId=" + nmId);
    }

    /**
     * Скачивает фото по CDN URL WB и сохраняет в uploads для последующей загрузки через media/file.
     *
     * @param photoUrl URL big/hq главного фото
     * @return имя сохранённого файла
     */
    private String downloadAndStoreFromUrl(String photoUrl) {
        if (photoUrl == null || photoUrl.isBlank()) {
            throw new IllegalStateException("Нет URL главного фото для скачивания");
        }
        try {
            java.net.HttpURLConnection connection =
                    (java.net.HttpURLConnection) java.net.URI.create(photoUrl).toURL().openConnection();
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(60_000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Clicki-WbAbTest/1.0");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Не удалось скачать фото карточки: HTTP " + status);
            }
            byte[] bytes;
            try (java.io.InputStream inputStream = connection.getInputStream()) {
                bytes = inputStream.readAllBytes();
            }
            if (bytes.length == 0) {
                throw new IllegalStateException("Скачанное фото карточки пустое");
            }
            if (bytes.length > MAX_UPLOAD_BYTES) {
                throw new IllegalStateException("Скачанное фото больше 32 Мб");
            }
            String extension = extensionFromUrlOrContentType(photoUrl, connection.getContentType());
            return storeBytes(bytes, extension);
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось скачать главное фото карточки: " + e.getMessage(), e);
        }
    }

    private String extensionFromUrlOrContentType(String photoUrl, String contentType) {
        String lowerUrl = photoUrl.toLowerCase(Locale.ROOT);
        if (lowerUrl.contains(".png")) {
            return ".png";
        }
        if (lowerUrl.contains(".webp")) {
            return ".webp";
        }
        if (lowerUrl.contains(".gif")) {
            return ".gif";
        }
        if (lowerUrl.contains(".bmp")) {
            return ".bmp";
        }
        if (contentType != null) {
            String ct = contentType.toLowerCase(Locale.ROOT);
            if (ct.contains("png")) {
                return ".png";
            }
            if (ct.contains("webp")) {
                return ".webp";
            }
            if (ct.contains("gif")) {
                return ".gif";
            }
        }
        return ".jpg";
    }

    private String storeBytes(byte[] bytes, String extension) {
        try {
            Path uploadsPath = Paths.get(uploadsDirectory);
            if (!Files.exists(uploadsPath)) {
                Files.createDirectories(uploadsPath);
            }
            String ext = extension != null && extension.startsWith(".") ? extension : ".jpg";
            String unique = "abtest_" + UUID.randomUUID().toString().replace("-", "") + ext;
            Files.write(uploadsPath.resolve(unique), bytes);
            return unique;
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось сохранить скачанное фото контрольного варианта", e);
        }
    }

    private WbCardsListRequest cardsRequestForNm(Long nmId) {
        return WbCardsListRequest.builder()
                .settings(WbCardsListRequest.Settings.builder()
                        .cursor(WbCardsListRequest.Cursor.builder().limit(100).build())
                        .filter(WbCardsListRequest.Filter.builder().withPhoto(-1).textSearch(String.valueOf(nmId)).build())
                        .build())
                .build();
    }

    private WbCardDto findCard(WbCardsListResponse response, Long nmId) {
        if (response == null || response.getCards() == null) {
            return null;
        }
        return response.getCards().stream()
                .filter(c -> nmId.equals(c.getNmId()))
                .findFirst()
                .orElse(response.getCards().isEmpty() ? null : response.getCards().get(0));
    }

    private String storeUpload(MultipartFile file) {
        validateImageFile(file);
        try {
            Path uploadsPath = Paths.get(uploadsDirectory);
            if (!Files.exists(uploadsPath)) {
                Files.createDirectories(uploadsPath);
            }
            String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "photo.jpg";
            String ext = "";
            int dot = original.lastIndexOf('.');
            if (dot >= 0) {
                ext = original.substring(dot);
            }
            String unique = "abtest_" + UUID.randomUUID().toString().replace("-", "") + ext;
            Path target = uploadsPath.resolve(unique);
            Files.copy(file.getInputStream(), target);
            return unique;
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось сохранить файл варианта", e);
        }
    }

    private void validateImageFile(MultipartFile file) {
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("Файл больше 32 Мб");
        }
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase(Locale.ROOT) : "";
        if (!(name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
                || name.endsWith(".bmp") || name.endsWith(".gif") || name.endsWith(".webp"))) {
            throw new IllegalArgumentException("Поддерживаются JPG, PNG, BMP, GIF, WebP");
        }
    }

    private void validateCreateRequest(WbCreateAbTestRequest request, List<MultipartFile> variantFiles) {
        if (request.getAdvertIds() == null || request.getAdvertIds().isEmpty()) {
            throw new IllegalArgumentException("Выберите хотя бы одну рекламную кампанию");
        }
        validateSettingsFields(
                request.getRotationMode(),
                request.getRotationViewsThreshold(),
                request.getRotationIntervalMinutes(),
                request.getStopMode(),
                request.getDurationDays()
        );
        long nonEmpty = variantFiles == null ? 0 : variantFiles.stream().filter(f -> f != null && !f.isEmpty()).count();
        if (nonEmpty < 1) {
            throw new IllegalArgumentException("Загрузите хотя бы один дополнительный вариант фото");
        }
    }

    /**
     * Валидация полей ротации / остановки (создание и редактирование).
     */
    private void validateSettingsFields(
            WbAbTestRotationMode rotationMode,
            Integer rotationViewsThreshold,
            Integer rotationIntervalMinutes,
            WbAbTestStopMode stopMode,
            Integer durationDays
    ) {
        if (rotationMode == WbAbTestRotationMode.ROTATION_BY_VIEWS) {
            if (rotationViewsThreshold == null || rotationViewsThreshold < 1) {
                throw new IllegalArgumentException("Укажите порог показов для ротации");
            }
        } else if (rotationMode == WbAbTestRotationMode.ROTATION_BY_INTERVAL) {
            if (rotationIntervalMinutes == null
                    || rotationIntervalMinutes < MIN_INTERVAL_MINUTES
                    || rotationIntervalMinutes > MAX_INTERVAL_MINUTES) {
                throw new IllegalArgumentException("Интервал ротации: от 30 минут до 24 часов");
            }
        }
        if (stopMode == WbAbTestStopMode.BY_DURATION) {
            if (durationDays == null || durationDays < 1) {
                throw new IllegalArgumentException("Укажите длительность теста в днях");
            }
        }
    }

    /**
     * Базовый токен: fullstats не чаще 1 раза в час —
     * короткая ротация по времени и несколько РК недоступны.
     */
    private void validateTokenAllowsRotation(Cabinet cabinet, WbCreateAbTestRequest request) {
        if (request.getAdvertIds() != null && request.getAdvertIds().size() > 1) {
            CabinetTokenType tokenType = CabinetTokenType.effective(cabinet.getTokenType());
            if (!tokenType.supportsFrequentFullstats()) {
                throw new IllegalArgumentException(
                        "При базовом токене WB можно выбрать только одну РК: "
                                + "статистика (fullstats) ограничена 1 запросом в час. "
                                + "Смените токен кабинета на персональный/сервисный, чтобы опрашивать несколько РК.");
            }
        }
        validateTokenAllowsInterval(cabinet, request.getRotationMode(), request.getRotationIntervalMinutes());
    }

    /**
     * Базовый токен: интервал ротации по времени не короче 1 часа.
     */
    private void validateTokenAllowsInterval(
            Cabinet cabinet,
            WbAbTestRotationMode rotationMode,
            Integer rotationIntervalMinutes
    ) {
        CabinetTokenType tokenType = CabinetTokenType.effective(cabinet.getTokenType());
        if (tokenType.supportsFrequentFullstats()) {
            return;
        }
        if (rotationMode == WbAbTestRotationMode.ROTATION_BY_INTERVAL
                && rotationIntervalMinutes != null
                && rotationIntervalMinutes < 60) {
            throw new IllegalArgumentException(
                    "Интервал ротации меньше 1 часа недоступен для базового токена WB: "
                            + "статистика РК (fullstats) ограничена 1 запросом в час. "
                            + "Выберите интервал от 1 часа или смените токен кабинета на персональный/сервисный.");
        }
    }

    /**
     * Путь к локальному файлу варианта в uploads (для отдачи превью в UI).
     */
    @Transactional(readOnly = true)
    public Path resolveVariantImagePath(Long cabinetId, Long testId, Long variantId) {
        requireTest(cabinetId, testId);
        WbAbTestVariant variant = abTestVariantRepository.findByIdAndAbTestId(variantId, testId)
                .orElseThrow(() -> new IllegalArgumentException("Вариант не найден"));
        if (variant.getStoredFileName() == null || variant.getStoredFileName().isBlank()) {
            throw new IllegalArgumentException("У варианта нет локального файла");
        }
        Path path = Paths.get(uploadsDirectory).resolve(variant.getStoredFileName()).normalize();
        Path uploadsRoot = Paths.get(uploadsDirectory).toAbsolutePath().normalize();
        if (!path.toAbsolutePath().normalize().startsWith(uploadsRoot)) {
            throw new IllegalArgumentException("Некорректный путь файла варианта");
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Файл варианта не найден на диске");
        }
        return path;
    }

    /**
     * Лёгкое JPEG-превью для UI (кэш рядом с оригиналом).
     * Поиск файла в БД — короткая TX; ImageIO/сжатие — вне транзакции (иначе при списке А/Б
     * десятки запросов держат Hikari на время декода больших фото и сайт зависает).
     */
    public Path resolveVariantUiPreviewPath(Long cabinetId, Long testId, Long variantId) {
        Path original = self.resolveVariantImagePath(cabinetId, testId, variantId);
        return ensureUiPreviewJpeg(original);
    }

    /**
     * Возвращает кэш UI-превью или строит его; без обращения к БД.
     */
    public Path ensureUiPreviewJpeg(Path original) {
        // .ui2.jpg — новый кэш с учётом EXIF Orientation (старые .ui.jpg могли быть «на боку»).
        Path preview = original.resolveSibling(original.getFileName().toString() + ".ui2.jpg");
        try {
            if (Files.isRegularFile(preview)
                    && Files.getLastModifiedTime(preview).compareTo(Files.getLastModifiedTime(original)) >= 0
                    && Files.size(preview) > 0) {
                return preview;
            }
            if (!writeUiPreviewJpeg(original, preview)) {
                log.warn(
                        "Не удалось сжать превью А/Б {} (ImageIO не декодировал файл; для webp нужен imageio-webp), отдаём оригинал",
                        original.getFileName()
                );
                return original;
            }
            return preview;
        } catch (IOException e) {
            log.warn("Ошибка подготовки UI-превью {}: {}", original.getFileName(), e.getMessage());
            return original;
        }
    }

    /**
     * Пишет JPEG-превью с длинной стороной ≤ {@link #UI_PREVIEW_MAX_SIDE}.
     * Учитывает EXIF Orientation: телефоны часто хранят пиксели «лёжа», а поворот — в метаданных;
     * {@link ImageIO#read} ориентацию не применяет, без этого превью в UI оказывается на боку.
     *
     * @return false если исходник не удалось декодировать
     */
    private boolean writeUiPreviewJpeg(Path original, Path preview) throws IOException {
        BufferedImage decoded = ImageIO.read(original.toFile());
        if (decoded == null) {
            return false;
        }
        BufferedImage source = applyExifOrientation(decoded, readExifOrientation(original));
        int srcW = source.getWidth();
        int srcH = source.getHeight();
        if (srcW <= 0 || srcH <= 0) {
            return false;
        }
        double scale = Math.min(1.0, (double) UI_PREVIEW_MAX_SIDE / Math.max(srcW, srcH));
        int dstW = Math.max(1, (int) Math.round(srcW * scale));
        int dstH = Math.max(1, (int) Math.round(srcH * scale));

        BufferedImage rgb = new BufferedImage(dstW, dstH, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, dstW, dstH);
            graphics.drawImage(source, 0, 0, dstW, dstH, null);
        } finally {
            graphics.dispose();
        }

        Path temp = preview.resolveSibling(preview.getFileName().toString() + ".tmp");
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            return false;
        }
        ImageWriter writer = writers.next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(UI_PREVIEW_JPEG_QUALITY);
            }
            try (ImageOutputStream output = ImageIO.createImageOutputStream(temp.toFile())) {
                writer.setOutput(output);
                writer.write(null, new IIOImage(rgb, null, null), param);
            }
            try {
                Files.move(temp, preview, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException moveAtomicFailed) {
                Files.move(temp, preview, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } finally {
            writer.dispose();
            Files.deleteIfExists(temp);
        }
    }

    /**
     * Читает EXIF Orientation (1..8). При ошибке/отсутствии — 1 (без поворота).
     */
    private static int readExifOrientation(Path file) {
        try {
            com.drew.metadata.Metadata metadata = com.drew.imaging.ImageMetadataReader.readMetadata(file.toFile());
            com.drew.metadata.exif.ExifIFD0Directory directory =
                    metadata.getFirstDirectoryOfType(com.drew.metadata.exif.ExifIFD0Directory.class);
            if (directory != null && directory.containsTag(com.drew.metadata.exif.ExifIFD0Directory.TAG_ORIENTATION)) {
                int orientation = directory.getInt(com.drew.metadata.exif.ExifIFD0Directory.TAG_ORIENTATION);
                if (orientation >= 1 && orientation <= 8) {
                    return orientation;
                }
            }
        } catch (Exception ignored) {
            // нет EXIF / не JPEG — считаем, что пиксели уже в нужной ориентации
        }
        return 1;
    }

    /**
     * Приводит пиксели к «нормальному» виду по EXIF Orientation (тег сбрасывается при перекодировании).
     */
    private static BufferedImage applyExifOrientation(BufferedImage source, int orientation) {
        if (orientation <= 1 || orientation > 8) {
            return source;
        }
        int w = source.getWidth();
        int h = source.getHeight();
        boolean swap = orientation >= 5;
        int outW = swap ? h : w;
        int outH = swap ? w : h;
        BufferedImage out = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, outW, outH);
            switch (orientation) {
                case 2 -> { // mirror horizontal
                    g.transform(new java.awt.geom.AffineTransform(-1, 0, 0, 1, w, 0));
                }
                case 3 -> { // 180
                    g.transform(new java.awt.geom.AffineTransform(-1, 0, 0, -1, w, h));
                }
                case 4 -> { // mirror vertical
                    g.transform(new java.awt.geom.AffineTransform(1, 0, 0, -1, 0, h));
                }
                case 5 -> { // mirror horizontal + rotate 270 CW
                    g.transform(new java.awt.geom.AffineTransform(0, 1, 1, 0, 0, 0));
                }
                case 6 -> { // rotate 90 CW
                    g.transform(new java.awt.geom.AffineTransform(0, 1, -1, 0, h, 0));
                }
                case 7 -> { // mirror horizontal + rotate 90 CW
                    g.transform(new java.awt.geom.AffineTransform(0, -1, -1, 0, h, w));
                }
                case 8 -> { // rotate 270 CW / 90 CCW
                    g.transform(new java.awt.geom.AffineTransform(0, -1, 1, 0, 0, w));
                }
                default -> {
                    return source;
                }
            }
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private WbAbTest requireTest(Long cabinetId, Long testId) {
        return abTestRepository.findByIdAndCabinetId(testId, cabinetId)
                .orElseThrow(() -> new IllegalArgumentException("А/Б-тест не найден"));
    }

    private WbAbTestDto toDto(WbAbTest test) {
        List<WbAbTestVariant> variants = abTestVariantRepository.findByAbTestIdOrderBySortOrderAsc(test.getId());
        List<Long> advertIds = abTestCampaignRepository.findByAbTestId(test.getId()).stream()
                .map(WbAbTestCampaign::getAdvertId)
                .collect(Collectors.toList());
        long totalViews = variants.stream().mapToLong(WbAbTestVariant::getViews).sum();
        // Лидер CTR — среди вариантов не на паузе (иначе оба активных «проигрывают» паузе).
        BigDecimal bestCtr = variants.stream()
                .filter(v -> !v.isPaused())
                .map(WbAbTestVariant::computeCtr)
                .max(BigDecimal::compareTo)
                .orElseGet(() -> variants.stream()
                        .map(WbAbTestVariant::computeCtr)
                        .max(BigDecimal::compareTo)
                        .orElse(BigDecimal.ZERO));

        String title = productCardRepository.findByNmIdAndCabinet_Id(test.getNmId(), test.getCabinetId())
                .map(WbProductCard::getTitle)
                .orElse(null);

        List<WbAbTestVariantDto> variantDtos = variants.stream().map(v -> {
            BigDecimal ctr = v.computeCtr();
            BigDecimal share = totalViews > 0
                    ? BigDecimal.valueOf(v.getViews()).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalViews), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal delta = bestCtr.subtract(ctr);
            boolean losing = !v.isPaused()
                    && test.getInsightCode() == WbAbTestInsightCode.HAS_LEADER
                    && ctr.compareTo(bestCtr) < 0
                    && v.getViews() >= minViewsPerVariant;
            boolean hasLocalImage = v.getStoredFileName() != null && !v.getStoredFileName().isBlank();
            // Для локальных файлов не отдаём позиционные CDN URL — они после restore врут.
            String photoUrl = hasLocalImage ? null : v.getPhotoUrl();
            String previewUrl = hasLocalImage
                    ? null
                    : (v.getPreviewUrl() != null ? v.getPreviewUrl() : v.getPhotoUrl());
            return WbAbTestVariantDto.builder()
                    .id(v.getId())
                    .sortOrder(v.getSortOrder())
                    .control(v.isControl())
                    .photoUrl(photoUrl)
                    .previewUrl(previewUrl)
                    .hasLocalImage(hasLocalImage)
                    .views(v.getViews())
                    .clicks(v.getClicks())
                    .atbs(v.getAtbs())
                    .orders(v.getOrders())
                    .ctr(ctr)
                    .cr1(v.computeCr1())
                    .cr(v.computeCr())
                    .sharePercent(share)
                    .activeOnWb(v.getId().equals(test.getActiveVariantId()))
                    .paused(v.isPaused())
                    .ctrDeltaToBest(delta.negate())
                    .losing(losing)
                    .winning(false)
                    .build();
        }).collect(Collectors.toList());

        // Если есть проигрывающий — помечаем лидера CTR среди активных.
        boolean anyLosing = variantDtos.stream().anyMatch(WbAbTestVariantDto::isLosing);
        if (anyLosing) {
            for (WbAbTestVariantDto dto : variantDtos) {
                if (!dto.isPaused() && dto.getCtr() != null && dto.getCtr().compareTo(bestCtr) == 0) {
                    dto.setWinning(true);
                }
            }
        }

        return WbAbTestDto.builder()
                .id(test.getId())
                .cabinetId(test.getCabinetId())
                .nmId(test.getNmId())
                .title(title)
                .status(test.getStatus())
                .rotationMode(test.getRotationMode())
                .rotationViewsThreshold(test.getRotationViewsThreshold())
                .rotationIntervalMinutes(test.getRotationIntervalMinutes())
                .stopMode(test.getStopMode())
                .durationDays(test.getDurationDays())
                .endsAt(test.getEndsAt())
                .finishAction(test.getFinishAction())
                .activeVariantId(test.getActiveVariantId())
                .startedAt(test.getStartedAt())
                .finishedAt(test.getFinishedAt())
                .insightCode(test.getInsightCode())
                .insightLabel(insightLabel(test.getInsightCode(), test.getStatus()))
                .lastWbError(test.getLastWbError())
                .canRestart(Boolean.TRUE.equals(test.getFailedAtStart()) && test.getStatus() == WbAbTestStatus.DISABLED)
                .advertIds(advertIds)
                .variants(variantDtos)
                .build();
    }

    private String insightLabel(WbAbTestInsightCode code, WbAbTestStatus status) {
        if (status == WbAbTestStatus.PENDING_START) {
            return "запускается…";
        }
        if (code == null) {
            return null;
        }
        return switch (code) {
            case DATA_LOW -> "данных мало";
            case NO_DIFF -> "разницы нет";
            case HAS_LEADER -> null;
        };
    }

    private List<String> readGallery(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String writeJson(List<String> urls) {
        try {
            return objectMapper.writeValueAsString(urls != null ? urls : List.of());
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private record CardPhotos(String mainUrl, String previewUrl, List<String> galleryUrls) {}
}
