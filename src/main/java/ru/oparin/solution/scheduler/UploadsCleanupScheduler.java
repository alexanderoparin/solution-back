package ru.oparin.solution.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.oparin.solution.service.UploadsCleanupResult;
import ru.oparin.solution.service.UploadsCleanupService;

/**
 * Периодическая очистка файлов в каталоге загрузок без ссылок в БД.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UploadsCleanupScheduler {

    private static final long BYTES_IN_MIB = 1024L * 1024L;

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

        UploadsCleanupResult result = uploadsCleanupService.cleanupOrphanedFiles();
        if (result.deletedFiles() > 0) {
            log.info(
                    "Очистка каталога загрузок: удалено осиротевших файлов: {}, освобождено: {} МБ",
                    result.deletedFiles(),
                    formatMb(result.freedBytes())
            );
        } else {
            log.info("Очистка каталога загрузок: осиротевших файлов не найдено");
        }
    }

    /** Объём в мегабайтах (1 МБ = 1024² байт), два знака после запятой. */
    private static String formatMb(long bytes) {
        return String.format(java.util.Locale.ROOT, "%.2f", bytes / (double) BYTES_IN_MIB);
    }
}
