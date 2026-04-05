package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.oparin.solution.dto.notes.CampaignNoteDto;
import ru.oparin.solution.dto.notes.CampaignNoteFileDto;
import ru.oparin.solution.dto.notes.CreateNoteRequest;
import ru.oparin.solution.dto.notes.UpdateNoteRequest;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.CampaignNote;
import ru.oparin.solution.model.CampaignNoteFile;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.CampaignNoteFileRepository;
import ru.oparin.solution.repository.CampaignNoteRepository;
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
 * Сервис для заметок к рекламным кампаниям (РК), включая вложения на диске.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignNoteService {

    private final CampaignNoteRepository noteRepository;
    private final CampaignNoteFileRepository fileRepository;

    @Value("${app.uploads.directory}")
    private String uploadsDirectory;

    @Value("${app.uploads.max-file-size}")
    private long maxFileSize;

    private long resolveCabinetId(SellerContext context) {
        if (context.cabinet() == null) {
            throw new UserException("Кабинет не указан", HttpStatus.BAD_REQUEST);
        }
        return context.cabinet().getId();
    }

    @Transactional
    public CampaignNoteDto createNote(Long campaignId, SellerContext context, CreateNoteRequest request, User currentUser) {
        long cabinetId = resolveCabinetId(context);
        CampaignNote note = CampaignNote.builder()
                .campaignId(campaignId)
                .cabinetId(cabinetId)
                .user(currentUser)
                .content(request.getContent())
                .build();
        note = noteRepository.save(note);
        log.info("Создана заметка РК ID={} campaignId={}", note.getId(), campaignId);
        return mapToDto(note);
    }

    @Transactional(readOnly = true)
    public List<CampaignNoteDto> getNotes(Long campaignId, SellerContext context) {
        long cabinetId = resolveCabinetId(context);
        return noteRepository.findByCampaignIdAndCabinetIdOrderByCreatedAtDesc(campaignId, cabinetId)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public CampaignNoteDto updateNote(Long noteId, Long campaignId, SellerContext context, UpdateNoteRequest request) {
        long cabinetId = resolveCabinetId(context);
        CampaignNote note = noteRepository.findByIdAndCampaignIdAndCabinetId(noteId, campaignId, cabinetId)
                .orElseThrow(() -> new UserException("Заметка не найдена", HttpStatus.NOT_FOUND));
        note.setContent(request.getContent());
        note = noteRepository.save(note);
        return mapToDto(note);
    }

    @Transactional
    public void deleteNote(Long noteId, Long campaignId, SellerContext context) {
        long cabinetId = resolveCabinetId(context);
        CampaignNote note = noteRepository.findByIdAndCampaignIdAndCabinetId(noteId, campaignId, cabinetId)
                .orElseThrow(() -> new UserException("Заметка не найдена", HttpStatus.NOT_FOUND));

        List<CampaignNoteFile> files = fileRepository.findByNote_IdOrderByUploadedAtAsc(noteId);
        for (CampaignNoteFile file : files) {
            deleteFileFromDisk(file.getFilePath());
        }
        noteRepository.delete(note);
        log.info("Удалена заметка РК ID={} campaignId={}", noteId, campaignId);
    }

    @Transactional
    public CampaignNoteFileDto uploadFile(Long noteId, Long campaignId, SellerContext context, MultipartFile file) {
        long cabinetId = resolveCabinetId(context);
        noteRepository.findByIdAndCampaignIdAndCabinetId(noteId, campaignId, cabinetId)
                .orElseThrow(() -> new UserException("Заметка не найдена", HttpStatus.NOT_FOUND));

        validateFile(file);

        try {
            Path uploadsPath = Paths.get(uploadsDirectory);
            if (!Files.exists(uploadsPath)) {
                Files.createDirectories(uploadsPath);
            }

            String originalFileName = file.getOriginalFilename();
            String fileExtension = getFileExtension(originalFileName);
            String uniqueFileName = UUID.randomUUID().toString() + fileExtension;
            Path filePath = uploadsPath.resolve(uniqueFileName);

            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            CampaignNote noteRef = noteRepository.getReferenceById(noteId);
            CampaignNoteFile noteFile = CampaignNoteFile.builder()
                    .note(noteRef)
                    .fileName(originalFileName != null ? originalFileName : uniqueFileName)
                    .filePath(filePath.toString())
                    .fileSize(file.getSize())
                    .mimeType(file.getContentType())
                    .build();
            noteFile = fileRepository.save(noteFile);
            log.info("Загружен файл РК ID={} для заметки ID={}", noteFile.getId(), noteId);
            return mapFileToDto(noteFile);
        } catch (IOException e) {
            log.error("Ошибка при загрузке файла заметки РК noteId={}", noteId, e);
            throw new UserException("Ошибка при загрузке файла: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    public void deleteFile(Long noteId, Long fileId, Long campaignId, SellerContext context) {
        long cabinetId = resolveCabinetId(context);
        noteRepository.findByIdAndCampaignIdAndCabinetId(noteId, campaignId, cabinetId)
                .orElseThrow(() -> new UserException("Заметка не найдена", HttpStatus.NOT_FOUND));

        CampaignNoteFile file = fileRepository.findByIdAndNoteId(fileId, noteId)
                .orElseThrow(() -> new UserException("Файл не найден", HttpStatus.NOT_FOUND));

        deleteFileFromDisk(file.getFilePath());
        fileRepository.delete(file);
        log.info("Удалён файл РК ID={} заметки ID={}", fileId, noteId);
    }

    @Transactional(readOnly = true)
    public Path getFile(Long noteId, Long fileId, Long campaignId, SellerContext context) {
        long cabinetId = resolveCabinetId(context);
        noteRepository.findByIdAndCampaignIdAndCabinetId(noteId, campaignId, cabinetId)
                .orElseThrow(() -> new UserException("Заметка не найдена", HttpStatus.NOT_FOUND));

        CampaignNoteFile file = fileRepository.findByIdAndNoteId(fileId, noteId)
                .orElseThrow(() -> new UserException("Файл не найден", HttpStatus.NOT_FOUND));

        Path filePath = Paths.get(file.getFilePath());
        if (!Files.exists(filePath)) {
            throw new UserException("Файл не найден на диске", HttpStatus.NOT_FOUND);
        }
        return filePath;
    }

    @Transactional(readOnly = true)
    public CampaignNoteFileDto getFileInfo(Long noteId, Long fileId, Long campaignId, SellerContext context) {
        long cabinetId = resolveCabinetId(context);
        noteRepository.findByIdAndCampaignIdAndCabinetId(noteId, campaignId, cabinetId)
                .orElseThrow(() -> new UserException("Заметка не найдена", HttpStatus.NOT_FOUND));

        CampaignNoteFile file = fileRepository.findByIdAndNoteId(fileId, noteId)
                .orElseThrow(() -> new UserException("Файл не найден", HttpStatus.NOT_FOUND));
        return mapFileToDto(file);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new UserException("Файл не может быть пустым", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > maxFileSize) {
            throw new UserException(
                    String.format("Размер файла превышает максимально допустимый (%d байт)", maxFileSize),
                    HttpStatus.BAD_REQUEST
            );
        }
        String fileName = file.getOriginalFilename();
        if (fileName != null) {
            String extension = getFileExtension(fileName).toLowerCase();
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

    private static String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf("."));
    }

    private void deleteFileFromDisk(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (Files.exists(path)) {
                Files.delete(path);
                log.debug("Файл заметки РК удалён с диска: {}", filePath);
            }
        } catch (IOException e) {
            log.warn("Не удалось удалить файл с диска: {}", filePath, e);
        }
    }

    private CampaignNoteDto mapToDto(CampaignNote note) {
        List<CampaignNoteFile> files = fileRepository.findByNote_IdOrderByUploadedAtAsc(note.getId());
        List<CampaignNoteFileDto> fileDtos = files.stream().map(this::mapFileToDto).collect(Collectors.toList());
        return CampaignNoteDto.builder()
                .id(note.getId())
                .campaignId(note.getCampaignId())
                .userId(note.getUser().getId())
                .userEmail(note.getUser().getEmail())
                .content(note.getContent())
                .createdAt(note.getCreatedAt())
                .updatedAt(note.getUpdatedAt())
                .files(fileDtos)
                .build();
    }

    private CampaignNoteFileDto mapFileToDto(CampaignNoteFile file) {
        return CampaignNoteFileDto.builder()
                .id(file.getId())
                .fileName(file.getFileName())
                .fileSize(file.getFileSize())
                .mimeType(file.getMimeType())
                .uploadedAt(file.getUploadedAt())
                .build();
    }
}
