package ru.oparin.solution.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.oparin.solution.dto.notes.ArticleNoteDto;
import ru.oparin.solution.dto.notes.CreateNoteRequest;
import ru.oparin.solution.dto.notes.UpdateNoteRequest;
import ru.oparin.solution.model.User;
import ru.oparin.solution.service.ArticleNoteService;
import ru.oparin.solution.service.SellerContextService;
import ru.oparin.solution.service.UserService;

import java.nio.file.Path;
import java.util.List;

/**
 * Контроллер для работы с заметками к артикулам.
 */
@RestController
@RequestMapping("/analytics/article/{nmId}/notes")
@RequiredArgsConstructor
@Slf4j
public class ArticleNotesController {

    private final ArticleNoteService noteService;
    private final SellerContextService sellerContextService;
    private final UserService userService;

    /**
     * Создает новую заметку для артикула.
     */
    @PostMapping
    public ResponseEntity<ArticleNoteDto> createNote(
            @PathVariable Long nmId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            @Valid @RequestBody CreateNoteRequest request,
            Authentication authentication) {

        User currentUser = userService.findByEmail(authentication.getName());
        SellerContextService.SellerContext context = sellerContextService.createContext(authentication, sellerId, cabinetId);
        
        ArticleNoteDto note = noteService.createNote(nmId, context, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(note);
    }

    /**
     * Получает все заметки для артикула.
     */
    @GetMapping
    public ResponseEntity<List<ArticleNoteDto>> getNotes(
            @PathVariable Long nmId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            Authentication authentication) {

        SellerContextService.SellerContext context = sellerContextService.createContext(authentication, sellerId, cabinetId);
        List<ArticleNoteDto> notes = noteService.getNotes(nmId, context);
        return ResponseEntity.ok(notes);
    }

    /**
     * Обновляет заметку.
     */
    @PutMapping("/{noteId}")
    public ResponseEntity<ArticleNoteDto> updateNote(
            @PathVariable Long nmId,
            @PathVariable Long noteId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            @Valid @RequestBody UpdateNoteRequest request,
            Authentication authentication) {

        SellerContextService.SellerContext context = sellerContextService.createContext(authentication, sellerId, cabinetId);
        ArticleNoteDto note = noteService.updateNote(noteId, nmId, context, request);
        return ResponseEntity.ok(note);
    }

    /**
     * Удаляет заметку.
     */
    @DeleteMapping("/{noteId}")
    public ResponseEntity<Void> deleteNote(
            @PathVariable Long nmId,
            @PathVariable Long noteId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            Authentication authentication) {

        SellerContextService.SellerContext context = sellerContextService.createContext(authentication, sellerId, cabinetId);
        noteService.deleteNote(noteId, nmId, context);
        return ResponseEntity.noContent().build();
    }

    /**
     * Загружает файл для заметки.
     */
    @PostMapping("/{noteId}/files")
    public ResponseEntity<ru.oparin.solution.dto.notes.ArticleNoteFileDto> uploadFile(
            @PathVariable Long nmId,
            @PathVariable Long noteId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        SellerContextService.SellerContext context = sellerContextService.createContext(authentication, sellerId, cabinetId);
        ru.oparin.solution.dto.notes.ArticleNoteFileDto fileDto = noteService.uploadFile(noteId, nmId, context, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(fileDto);
    }

    /**
     * Скачивает файл заметки.
     */
    @GetMapping("/{noteId}/files/{fileId}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable Long nmId,
            @PathVariable Long noteId,
            @PathVariable Long fileId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            Authentication authentication) {

        SellerContextService.SellerContext context = sellerContextService.createContext(authentication, sellerId, cabinetId);
        Path filePath = noteService.getFile(noteId, fileId, nmId, context);
        
        Resource resource = new FileSystemResource(filePath);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        // Получаем оригинальное имя файла из БД
        ru.oparin.solution.dto.notes.ArticleNoteFileDto fileInfo = noteService.getFileInfo(noteId, fileId, nmId, context);
        String fileName = fileInfo.getFileName();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType(fileInfo.getMimeType() != null ? fileInfo.getMimeType() : MediaType.APPLICATION_OCTET_STREAM_VALUE))
                .body(resource);
    }

    /**
     * Удаляет файл заметки.
     */
    @DeleteMapping("/{noteId}/files/{fileId}")
    public ResponseEntity<Void> deleteFile(
            @PathVariable Long nmId,
            @PathVariable Long noteId,
            @PathVariable Long fileId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            Authentication authentication) {

        SellerContextService.SellerContext context = sellerContextService.createContext(authentication, sellerId, cabinetId);
        noteService.deleteFile(noteId, fileId, nmId, context);
        return ResponseEntity.noContent().build();
    }
}

