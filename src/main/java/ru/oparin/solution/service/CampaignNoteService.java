package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.notes.CampaignNoteDto;
import ru.oparin.solution.dto.notes.CreateNoteRequest;
import ru.oparin.solution.dto.notes.UpdateNoteRequest;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.CampaignNote;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.CampaignNoteRepository;
import ru.oparin.solution.service.SellerContextService.SellerContext;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Сервис для заметок к рекламным кампаниям (РК).
 */
@Service
@RequiredArgsConstructor
public class CampaignNoteService {

    private final CampaignNoteRepository noteRepository;

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
        noteRepository.delete(note);
    }

    private CampaignNoteDto mapToDto(CampaignNote note) {
        return CampaignNoteDto.builder()
                .id(note.getId())
                .campaignId(note.getCampaignId())
                .userId(note.getUser().getId())
                .userEmail(note.getUser().getEmail())
                .content(note.getContent())
                .createdAt(note.getCreatedAt())
                .updatedAt(note.getUpdatedAt())
                .build();
    }
}
