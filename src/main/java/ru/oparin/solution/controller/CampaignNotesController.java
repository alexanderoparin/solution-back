package ru.oparin.solution.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.oparin.solution.dto.notes.CampaignNoteDto;
import ru.oparin.solution.dto.notes.CampaignNoteFileDto;
import ru.oparin.solution.dto.notes.CreateNoteRequest;
import ru.oparin.solution.dto.notes.UpdateNoteRequest;
import ru.oparin.solution.model.User;
import ru.oparin.solution.service.CampaignNoteService;
import ru.oparin.solution.service.SellerContextService;
import ru.oparin.solution.service.UserService;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * Контроллер заметок к рекламным кампаниям (РК).
 */
@RestController
@RequestMapping("/advertising/campaigns/{campaignId}/notes")
@RequiredArgsConstructor
public class CampaignNotesController {

    private final CampaignNoteService noteService;
    private final SellerContextService sellerContextService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<CampaignNoteDto>> getNotes(
            @PathVariable Long campaignId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            Authentication authentication) {
        SellerContextService.SellerContext context = sellerContextService.createContext(authentication, sellerId, cabinetId);
        List<CampaignNoteDto> notes = noteService.getNotes(campaignId, context);
        return ResponseEntity.ok(notes);
    }

    @PostMapping
    public ResponseEntity<CampaignNoteDto> createNote(
            @PathVariable Long campaignId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            @Valid @RequestBody CreateNoteRequest request,
            Authentication authentication) {
        User currentUser = userService.findByEmail(authentication.getName());
        SellerContextService.SellerContext context = sellerContextService.createContext(authentication, sellerId, cabinetId);
        CampaignNoteDto note = noteService.createNote(campaignId, context, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(note);
    }

    @PutMapping("/{noteId}")
    public ResponseEntity<CampaignNoteDto> updateNote(
            @PathVariable Long campaignId,
            @PathVariable Long noteId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            @Valid @RequestBody UpdateNoteRequest request,
            Authentication authentication) {
        SellerContextService.SellerContext context = sellerContextService.createContext(authentication, sellerId, cabinetId);
        CampaignNoteDto note = noteService.updateNote(noteId, campaignId, context, request);
        return ResponseEntity.ok(note);
    }

    @DeleteMapping("/{noteId}")
    public ResponseEntity<Void> deleteNote(
            @PathVariable Long campaignId,
            @PathVariable Long noteId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            Authentication authentication) {
        SellerContextService.SellerContext context = sellerContextService.createContext(authentication, sellerId, cabinetId);
        noteService.deleteNote(noteId, campaignId, context);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{noteId}/files")
    public ResponseEntity<CampaignNoteFileDto> uploadFile(
            @PathVariable Long campaignId,
            @PathVariable Long noteId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        SellerContextService.SellerContext context = sellerContextService.createContext(authentication, sellerId, cabinetId);
        CampaignNoteFileDto dto = noteService.uploadFile(noteId, campaignId, context, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping("/{noteId}/files/{fileId}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable Long campaignId,
            @PathVariable Long noteId,
            @PathVariable Long fileId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            Authentication authentication) {
        SellerContextService.SellerContext context = sellerContextService.createContext(authentication, sellerId, cabinetId);
        Path filePath = noteService.getFile(noteId, fileId, campaignId, context);
        Resource resource = new FileSystemResource(filePath);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        CampaignNoteFileDto fileInfo = noteService.getFileInfo(noteId, fileId, campaignId, context);
        String fileName = fileInfo.getFileName();
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(fileName, StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .contentType(MediaType.parseMediaType(
                        fileInfo.getMimeType() != null ? fileInfo.getMimeType() : MediaType.APPLICATION_OCTET_STREAM_VALUE))
                .body(resource);
    }

    @DeleteMapping("/{noteId}/files/{fileId}")
    public ResponseEntity<Void> deleteFile(
            @PathVariable Long campaignId,
            @PathVariable Long noteId,
            @PathVariable Long fileId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Long cabinetId,
            Authentication authentication) {
        SellerContextService.SellerContext context = sellerContextService.createContext(authentication, sellerId, cabinetId);
        noteService.deleteFile(noteId, fileId, campaignId, context);
        return ResponseEntity.noContent().build();
    }
}
