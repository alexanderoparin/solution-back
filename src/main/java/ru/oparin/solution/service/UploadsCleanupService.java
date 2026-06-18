package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.oparin.solution.repository.ArticleNoteFileRepository;
import ru.oparin.solution.repository.CampaignNoteFileRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Очистка файлов в каталоге загрузок, на которые больше нет ссылок в БД.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UploadsCleanupService {

    private final ArticleNoteFileRepository articleNoteFileRepository;
    private final CampaignNoteFileRepository campaignNoteFileRepository;

    @Value("${app.uploads.directory}")
    private String uploadsDirectory;

    @Value("${app.uploads.orphan-min-age-hours:1}")
    private int orphanMinAgeHours;

    /**
     * Удаляет с диска файлы из каталога загрузок, которых нет в {@code article_note_files} и {@code campaign_note_files}.
     *
     * @return количество удалённых файлов
     */
    public int cleanupOrphanedFiles() {
        Path uploadsRoot = Paths.get(uploadsDirectory).toAbsolutePath().normalize();
        if (!Files.isDirectory(uploadsRoot)) {
            log.warn("Каталог загрузок не найден, очистка пропущена: {}", uploadsRoot);
            return 0;
        }

        Set<String> referencedPaths = loadReferencedPaths();
        Instant modifiedBefore = Instant.now().minus(orphanMinAgeHours, ChronoUnit.HOURS);
        int deleted = 0;

        try (Stream<Path> files = Files.list(uploadsRoot)) {
            for (Path filePath : files.filter(Files::isRegularFile).toList()) {
                Path normalizedPath = filePath.toAbsolutePath().normalize();
                if (!normalizedPath.startsWith(uploadsRoot)) {
                    log.warn("Пропущен файл вне каталога загрузок: {}", normalizedPath);
                    continue;
                }
                if (referencedPaths.contains(normalizedPath.toString())) {
                    continue;
                }
                if (!isOlderThan(normalizedPath, modifiedBefore)) {
                    continue;
                }
                if (deleteFile(normalizedPath)) {
                    deleted++;
                }
            }
        } catch (IOException e) {
            log.error("Ошибка при обходе каталога загрузок: {}", uploadsRoot, e);
        }

        return deleted;
    }

    private Set<String> loadReferencedPaths() {
        Set<String> referencedPaths = new HashSet<>();
        for (String filePath : articleNoteFileRepository.findAllFilePaths()) {
            referencedPaths.add(normalizeStoredPath(filePath));
        }
        for (String filePath : campaignNoteFileRepository.findAllFilePaths()) {
            referencedPaths.add(normalizeStoredPath(filePath));
        }
        return referencedPaths;
    }

    private String normalizeStoredPath(String filePath) {
        return Paths.get(filePath).toAbsolutePath().normalize().toString();
    }

    private boolean isOlderThan(Path filePath, Instant modifiedBefore) {
        try {
            return Files.getLastModifiedTime(filePath).toInstant().isBefore(modifiedBefore);
        } catch (IOException e) {
            log.warn("Не удалось прочитать время изменения файла, пропускаем: {}", filePath, e);
            return false;
        }
    }

    private boolean deleteFile(Path filePath) {
        try {
            Files.delete(filePath);
            log.info("Удалён осиротевший файл загрузок: {}", filePath);
            return true;
        } catch (IOException e) {
            log.warn("Не удалось удалить осиротевший файл загрузок: {}", filePath, e);
            return false;
        }
    }
}
