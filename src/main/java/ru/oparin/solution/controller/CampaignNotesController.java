package ru.oparin.solution.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.oparin.solution.dto.notes.CampaignNoteDto;
import ru.oparin.solution.dto.notes.CreateNoteRequest;
import ru.oparin.solution.dto.notes.UpdateNoteRequest;
import ru.oparin.solution.model.User;
import ru.oparin.solution.service.CampaignNoteService;
import ru.oparin.solution.service.SellerContextService;
import ru.oparin.solution.service.UserService;

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
}
