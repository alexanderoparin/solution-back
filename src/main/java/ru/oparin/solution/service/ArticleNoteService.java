package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.oparin.solution.dto.notes.ArticleNoteDto;
import ru.oparin.solution.dto.notes.ArticleNoteFileDto;
import ru.oparin.solution.dto.notes.CreateNoteRequest;
import ru.oparin.solution.dto.notes.UpdateNoteRequest;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.ArticleNote;
import ru.oparin.solution.model.ArticleNoteFile;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.ArticleNoteFileRepository;
import ru.oparin.solution.repository.ArticleNoteRepository;
import ru.oparin.solution.repository.CabinetRepository;
import ru.oparin.solution.service.SellerContextService.SellerContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Сервис для работы с заметками к артикулам.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ArticleNoteService {

    private final ArticleNoteRepository noteRepository;
    private final ArticleNoteFileRepository fileRepository;
    private final UserService userService;
    private final CabinetRepository cabinetRepository;

    @Value("${app.uploads.directory:/app/uploads/article-notes}")
    private String uploadsDirectory;

    @Value("${app.uploads.max-file-size:31457280}") // 30 MB по умолчанию
    private long maxFileSize;

    /**
     * Создает новую заметку для артикула.
     *
     * @param nmId артикул WB
     * @param context контекст продавца
     * @param request данные для создания заметки
     * @param currentUser текущий пользователь
     * @return созданная заметка
     */
    @Transactional
    public ArticleNoteDto createNote(Long nmId, SellerContext context, CreateNoteRequest request, User currentUser) {
        long cabinetId = resolveCabinetId(context);
        ArticleNote note = ArticleNote.builder()
                .nmId(nmId)
                .sellerId(context.user().getId())
                .cabinetId(cabinetId)
                .user(currentUser)
                .content(request.getContent())
                .build();

        note = noteRepository.save(note);
        log.info("Создана заметка ID={} для артикула nmId={} пользователем userId={}", 
                note.getId(), nmId, currentUser.getId());

        return mapToDto(note);
    }

    /**
     * Получает все заметки для артикула.
     *
     * @param nmId артикул WB
     * @param context контекст продавца
     * @return список заметок
     */
    @Transactional(readOnly = true)
    public List<ArticleNoteDto> getNotes(Long nmId, SellerContext context) {
        long cabinetId = resolveCabinetId(context);
        List<ArticleNote> notes = noteRepository.findByNmIdAndCabinetIdOrderByCreatedAtDesc(nmId, cabinetId);

        return notes.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Обновляет заметку.
     *
     * @param noteId ID заметки
     * @param nmId артикул WB
     * @param context контекст продавца
     * @param request данные для обновления
     * @return обновленная заметка
     */
    @Transactional
    public ArticleNoteDto updateNote(Long noteId, Long nmId, SellerContext context, UpdateNoteRequest request) {
        long cabinetId = resolveCabinetId(context);
        ArticleNote note = noteRepository.findByIdAndNmIdAndCabinetId(noteId, nmId, cabinetId)
                .orElseThrow(() -> new UserException(
                        "Заметка не найдена",
                        HttpStatus.NOT_FOUND
                ));

        note.setContent(request.getContent());
        note = noteRepository.save(note);

        log.info("Обновлена заметка ID={} для артикула nmId={}", noteId, nmId);
        return mapToDto(note);
    }

    /**
     * Удаляет заметку и все прикрепленные файлы.
     *
     * @param noteId ID заметки
     * @param nmId артикул WB
     * @param context контекст продавца
     */
    @Transactional
    public void deleteNote(Long noteId, Long nmId, SellerContext context) {
        long cabinetId = resolveCabinetId(context);
        ArticleNote note = noteRepository.findByIdAndNmIdAndCabinetId(noteId, nmId, cabinetId)
                .orElseThrow(() -> new UserException(
                        "Заметка не найдена",
                        HttpStatus.NOT_FOUND
                ));

        // Удаляем все файлы
        List<ArticleNoteFile> files = fileRepository.findByNoteIdOrderByUploadedAtAsc(noteId);
        for (ArticleNoteFile file : files) {
            deleteFileFromDisk(file.getFilePath());
        }

        noteRepository.delete(note);
        log.info("Удалена заметка ID={} для артикула nmId={}", noteId, nmId);
    }

    /**
     * Загружает файл для заметки.
     *
     * @param noteId ID заметки
     * @param nmId артикул WB
     * @param context контекст продавца
     * @param file загружаемый файл
     * @return информация о загруженном файле
     */
    @Transactional
    public ArticleNoteFileDto uploadFile(Long noteId, Long nmId, SellerContext context, MultipartFile file) {
        long cabinetId = resolveCabinetId(context);
        ArticleNote note = noteRepository.findByIdAndNmIdAndCabinetId(noteId, nmId, cabinetId)
                .orElseThrow(() -> new UserException(
                        "Заметка не найдена",
                        HttpStatus.NOT_FOUND
                ));

        // Валидация файла
        validateFile(file);

        try {
            // Убеждаемся, что директория существует
            Path uploadsPath = Paths.get(uploadsDirectory);
            if (!Files.exists(uploadsPath)) {
                Files.createDirectories(uploadsPath);
            }

            // Генерируем уникальное имя файла
            String originalFileName = file.getOriginalFilename();
            String fileExtension = getFileExtension(originalFileName);
            String uniqueFileName = UUID.randomUUID().toString() + fileExtension;
            Path filePath = uploadsPath.resolve(uniqueFileName);

            // Сохраняем файл
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // Сохраняем информацию о файле в БД
            ArticleNoteFile noteFile = ArticleNoteFile.builder()
                    .note(note)
                    .fileName(originalFileName)
                    .filePath(filePath.toString())
                    .fileSize(file.getSize())
                    .mimeType(file.getContentType())
                    .build();

            noteFile = fileRepository.save(noteFile);

            log.info("Загружен файл ID={} для заметки ID={}, размер={} байт", 
                    noteFile.getId(), noteId, file.getSize());

            return mapFileToDto(noteFile);

        } catch (IOException e) {
            log.error("Ошибка при загрузке файла для заметки ID={}", noteId, e);
            throw new UserException(
                    "Ошибка при загрузке файла: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    /**
     * Удаляет файл заметки.
     *
     * @param noteId ID заметки
     * @param fileId ID файла
     * @param nmId артикул WB
     * @param context контекст продавца
     */
    @Transactional
    public void deleteFile(Long noteId, Long fileId, Long nmId, SellerContext context) {
        long cabinetId = resolveCabinetId(context);
        ArticleNote note = noteRepository.findByIdAndNmIdAndCabinetId(noteId, nmId, cabinetId)
                .orElseThrow(() -> new UserException(
                        "Заметка не найдена",
                        HttpStatus.NOT_FOUND
                ));

        ArticleNoteFile file = fileRepository.findByIdAndNoteId(fileId, noteId)
                .orElseThrow(() -> new UserException(
                        "Файл не найден",
                        HttpStatus.NOT_FOUND
                ));

        // Удаляем файл с диска
        deleteFileFromDisk(file.getFilePath());

        // Удаляем запись из БД
        fileRepository.delete(file);

        log.info("Удален файл ID={} для заметки ID={}", fileId, noteId);
    }

    /**
     * Получает файл для скачивания.
     *
     * @param noteId ID заметки
     * @param fileId ID файла
     * @param nmId артикул WB
     * @param context контекст продавца
     * @return путь к файлу
     */
    @Transactional(readOnly = true)
    public Path getFile(Long noteId, Long fileId, Long nmId, SellerContext context) {
        long cabinetId = resolveCabinetId(context);
        noteRepository.findByIdAndNmIdAndCabinetId(noteId, nmId, cabinetId)
                .orElseThrow(() -> new UserException(
                        "Заметка не найдена",
                        HttpStatus.NOT_FOUND
                ));

        ArticleNoteFile file = fileRepository.findByIdAndNoteId(fileId, noteId)
                .orElseThrow(() -> new UserException(
                        "Файл не найден",
                        HttpStatus.NOT_FOUND
                ));

        Path filePath = Paths.get(file.getFilePath());
        if (!Files.exists(filePath)) {
            throw new UserException(
                    "Файл не найден на диске",
                    HttpStatus.NOT_FOUND
            );
        }

        return filePath;
    }

    /**
     * Получает информацию о файле.
     *
     * @param noteId ID заметки
     * @param fileId ID файла
     * @param nmId артикул WB
     * @param context контекст продавца
     * @return информация о файле
     */
    @Transactional(readOnly = true)
    public ArticleNoteFileDto getFileInfo(Long noteId, Long fileId, Long nmId, SellerContext context) {
        long cabinetId = resolveCabinetId(context);
        noteRepository.findByIdAndNmIdAndCabinetId(noteId, nmId, cabinetId)
                .orElseThrow(() -> new UserException(
                        "Заметка не найдена",
                        HttpStatus.NOT_FOUND
                ));

        ArticleNoteFile file = fileRepository.findByIdAndNoteId(fileId, noteId)
                .orElseThrow(() -> new UserException(
                        "Файл не найден",
                        HttpStatus.NOT_FOUND
                ));

        return mapFileToDto(file);
    }

    /**
     * Определяет ID кабинета по умолчанию для контекста продавца.
     */
    private long resolveCabinetId(SellerContext context) {
        return cabinetRepository.findDefaultByUserId(context.user().getId())
                .orElseThrow(() -> new UserException(
                        "У продавца нет кабинета по умолчанию",
                        HttpStatus.BAD_REQUEST
                ))
                .getId();
    }

    /**
     * Валидирует загружаемый файл.
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new UserException(
                    "Файл не может быть пустым",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (file.getSize() > maxFileSize) {
            throw new UserException(
                    String.format("Размер файла превышает максимально допустимый (%d байт)", maxFileSize),
                    HttpStatus.BAD_REQUEST
            );
        }

        // Проверяем расширение файла (опционально)
        String fileName = file.getOriginalFilename();
        if (fileName != null) {
            String extension = getFileExtension(fileName).toLowerCase();
            // Разрешенные расширения: изображения, PDF, Excel, Word
            List<String> allowedExtensions = List.of(
                    ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp",
                    ".pdf",
                    ".xls", ".xlsx",
                    ".doc", ".docx",
                    ".txt", ".csv"
            );
            if (!allowedExtensions.contains(extension)) {
                throw new UserException(
                        "Недопустимый тип файла. Разрешены: изображения, PDF, Excel, Word, TXT, CSV",
                        HttpStatus.BAD_REQUEST
                );
            }
        }
    }

    /**
     * Получает расширение файла.
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf("."));
    }

    /**
     * Удаляет файл с диска.
     */
    private void deleteFileFromDisk(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (Files.exists(path)) {
                Files.delete(path);
                log.debug("Файл удален с диска: {}", filePath);
            }
        } catch (IOException e) {
            log.warn("Не удалось удалить файл с диска: {}", filePath, e);
        }
    }

    /**
     * Преобразует Entity в DTO.
     */
    private ArticleNoteDto mapToDto(ArticleNote note) {
        // Загружаем файлы отдельно, так как они могут быть ленивыми
        List<ArticleNoteFile> files = fileRepository.findByNoteIdOrderByUploadedAtAsc(note.getId());
        List<ArticleNoteFileDto> fileDtos = files.stream()
                .map(this::mapFileToDto)
                .collect(Collectors.toList());

        return ArticleNoteDto.builder()
                .id(note.getId())
                .nmId(note.getNmId())
                .sellerId(note.getSellerId())
                .userId(note.getUser().getId())
                .userEmail(note.getUser().getEmail())
                .content(note.getContent())
                .files(fileDtos)
                .createdAt(note.getCreatedAt())
                .updatedAt(note.getUpdatedAt())
                .build();
    }

    /**
     * Преобразует Entity файла в DTO.
     */
    private ArticleNoteFileDto mapFileToDto(ArticleNoteFile file) {
        return ArticleNoteFileDto.builder()
                .id(file.getId())
                .fileName(file.getFileName())
                .fileSize(file.getFileSize())
                .mimeType(file.getMimeType())
                .uploadedAt(file.getUploadedAt())
                .build();
    }
}

