package ru.oparin.solution.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.oparin.solution.service.UploadsCleanupService;

/**
 * Периодическая очистка файлов в каталоге загрузок без ссылок в БД.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UploadsCleanupScheduler {

    private final UploadsCleanupService uploadsCleanupService;

    @Value("${app.uploads.cleanup-enabled:true}")
    private boolean cleanupEnabled;

    /**
     * Каждое воскресенье в 12:00 удаляет осиротевшие файлы из каталога загрузок.
     */
    @Scheduled(cron = "0 0 12 ? * SUN")
    @SchedulerLock(name = "uploadsOrphanCleanup", lockAtLeastFor = "PT5S", lockAtMostFor = "PT30M")
    public void cleanupOrphanedUploads() {
        if (!cleanupEnabled) {
            return;
        }

        int deleted = uploadsCleanupService.cleanupOrphanedFiles();
        if (deleted > 0) {
            log.info("Очистка каталога загрузок: удалено осиротевших файлов: {}", deleted);
        }
    }
}
