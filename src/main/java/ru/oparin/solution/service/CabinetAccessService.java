package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.cabinet.*;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.CabinetAccessGrantRepository;
import ru.oparin.solution.repository.CabinetAccessInvitationRepository;
import ru.oparin.solution.repository.CabinetRepository;
import ru.oparin.solution.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Доступы к кабинетам: grants, invitations, проверки разделов.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CabinetAccessService {

    private static final int INVITATION_TTL_DAYS = 14;

    private final CabinetAccessGrantRepository grantRepository;
    private final CabinetAccessInvitationRepository invitationRepository;
    private final CabinetRepository cabinetRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final AccountTypeService accountTypeService;

    @Transactional(readOnly = true)
    public CabinetsOverviewDto getOverview(User currentUser, String search) {
        String q = search != null ? search.trim().toLowerCase() : "";
        List<OwnedCabinetRowDto> owned = cabinetRepository.findByUser_IdOrderByCreatedAtDesc(currentUser.getId()).stream()
                .filter(c -> matchesSearch(c.getName(), q))
                .map(this::toOwnedRow)
                .collect(Collectors.toCollection(ArrayList::new));
        List<GrantedCabinetRowDto> granted = grantRepository
                .findActiveGrantedForUser(currentUser.getId(), CabinetAccessGrantStatus.ACTIVE, LocalDateTime.now())
                .stream()
                .filter(g -> matchesSearch(g.getCabinet().getName(), q))
                .map(this::toGrantedRow)
                .collect(Collectors.toCollection(ArrayList::new));
        return CabinetsOverviewDto.builder().owned(owned).granted(granted).build();
    }

    @Transactional(readOnly = true)
    public boolean isCabinetOwner(User user, Long cabinetId) {
        if (user.getRole() == Role.ADMIN) {
            return cabinetRepository.existsById(cabinetId);
        }
        return cabinetRepository.existsByIdAndUser_Id(cabinetId, user.getId());
    }

    @Transactional(readOnly = true)
    public boolean canManageCabinet(User user, Long cabinetId) {
        return user.getRole() == Role.ADMIN || cabinetRepository.existsByIdAndUser_Id(cabinetId, user.getId());
    }

    @Transactional(readOnly = true)
    public boolean hasSectionAccess(User user, Long cabinetId, CabinetAccessSection section) {
        if (user.getRole() == Role.ADMIN) {
            return true;
        }
        if (cabinetRepository.existsByIdAndUser_Id(cabinetId, user.getId())) {
            return true;
        }
        return grantRepository.findByCabinet_IdAndUser_Id(cabinetId, user.getId())
                .filter(g -> g.getStatus() == CabinetAccessGrantStatus.ACTIVE)
                .filter(g -> g.getValidUntil() == null || g.getValidUntil().isAfter(LocalDateTime.now()))
                .map(g -> g.getSections() != null && g.getSections().contains(section))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<CabinetAccessSection> getSectionsForUser(User user, Long cabinetId) {
        if (user.getRole() == Role.ADMIN || cabinetRepository.existsByIdAndUser_Id(cabinetId, user.getId())) {
            return List.of(CabinetAccessSection.values());
        }
        return grantRepository.findByCabinet_IdAndUser_Id(cabinetId, user.getId())
                .filter(g -> g.getStatus() == CabinetAccessGrantStatus.ACTIVE)
                .filter(g -> g.getValidUntil() == null || g.getValidUntil().isAfter(LocalDateTime.now()))
                .map(CabinetAccessGrant::getSections)
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public boolean hasAnyCabinetAccess(User user) {
        if (user.getRole() == Role.ADMIN) {
            return true;
        }
        if (!cabinetRepository.findByUser_IdOrderByCreatedAtDesc(user.getId()).isEmpty()) {
            return true;
        }
        return !grantRepository.findActiveGrantedForUser(
                user.getId(), CabinetAccessGrantStatus.ACTIVE, LocalDateTime.now()).isEmpty();
    }

    @Transactional(readOnly = true)
    public List<CabinetAccessEntryDto> listAccessEntries(User owner, Long cabinetId) {
        ensureCanManage(owner, cabinetId);
        List<CabinetAccessEntryDto> result = new ArrayList<>();
        for (CabinetAccessGrant grant : grantRepository.findByCabinet_IdOrderByCreatedAtDesc(cabinetId)) {
            result.add(toAccessEntry(grant));
        }
        for (CabinetAccessInvitation invitation : invitationRepository.findByCabinet_IdOrderByCreatedAtDesc(cabinetId)) {
            if (invitation.getStatus() == CabinetAccessInvitationStatus.PENDING) {
                result.add(toAccessEntry(invitation));
            }
        }
        return result;
    }

    @Transactional
    public void grantAccess(User owner, Long cabinetId, GrantCabinetAccessRequest request) {
        ensureCanManage(owner, cabinetId);
        validateGrantRequest(request);
        String email = request.email().trim().toLowerCase();
        Cabinet cabinet = cabinetRepository.findById(cabinetId)
                .orElseThrow(() -> new UserException("Кабинет не найден", HttpStatus.NOT_FOUND));

        Optional<User> existingUser = userRepository.findByEmail(email);
        if (existingUser.isPresent()) {
            User grantee = existingUser.get();
            if (grantee.getId().equals(owner.getId())) {
                throw new UserException("Нельзя выдать доступ самому себе", HttpStatus.BAD_REQUEST);
            }
            upsertActiveGrant(cabinet, grantee, owner, request.sections(), request.comment(), request.validUntil(), null);
            accountTypeService.ensureAccountType(grantee.getId(), AccountType.EMPLOYEE);
            return;
        }

        invitationRepository.findPendingByCabinetAndEmail(cabinetId, email, CabinetAccessInvitationStatus.PENDING)
                .ifPresent(inv -> {
                    inv.setStatus(CabinetAccessInvitationStatus.REVOKED);
                    invitationRepository.save(inv);
                });

        String token = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();
        CabinetAccessInvitation invitation = CabinetAccessInvitation.builder()
                .token(token)
                .email(email)
                .cabinet(cabinet)
                .invitedByUser(owner)
                .sections(new ArrayList<>(request.sections()))
                .commentText(request.comment())
                .status(CabinetAccessInvitationStatus.PENDING)
                .validUntil(request.validUntil())
                .expiresAt(now.plusDays(INVITATION_TTL_DAYS))
                .createdAt(now)
                .updatedAt(now)
                .build();
        invitationRepository.save(invitation);
        emailService.sendCabinetInvitationEmail(email, owner, cabinet.getName(), token);
    }

    @Transactional
    public void revokeGrant(User owner, Long cabinetId, Long grantId) {
        ensureCanManage(owner, cabinetId);
        CabinetAccessGrant grant = grantRepository.findById(grantId)
                .orElseThrow(() -> new UserException("Доступ не найден", HttpStatus.NOT_FOUND));
        if (!grant.getCabinet().getId().equals(cabinetId)) {
            throw new UserException("Доступ не принадлежит кабинету", HttpStatus.BAD_REQUEST);
        }
        grant.setStatus(CabinetAccessGrantStatus.REVOKED);
        grant.setRevokedAt(LocalDateTime.now());
        grantRepository.save(grant);
    }

    @Transactional
    public void revokeInvitation(User owner, Long cabinetId, Long invitationId) {
        ensureCanManage(owner, cabinetId);
        CabinetAccessInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new UserException("Приглашение не найдено", HttpStatus.NOT_FOUND));
        if (!invitation.getCabinet().getId().equals(cabinetId)) {
            throw new UserException("Приглашение не принадлежит кабинету", HttpStatus.BAD_REQUEST);
        }
        invitation.setStatus(CabinetAccessInvitationStatus.REVOKED);
        invitationRepository.save(invitation);
    }

    @Transactional(readOnly = true)
    public CabinetInvitationPreviewDto previewInvitation(String token) {
        CabinetAccessInvitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new UserException("Приглашение не найдено", HttpStatus.NOT_FOUND));
        boolean expired = invitation.getStatus() == CabinetAccessInvitationStatus.EXPIRED
                || invitation.getExpiresAt().isBefore(LocalDateTime.now());
        boolean accepted = invitation.getStatus() == CabinetAccessInvitationStatus.ACCEPTED;
        User inviter = invitation.getInvitedByUser();
        return CabinetInvitationPreviewDto.builder()
                .cabinetName(invitation.getCabinet().getName())
                .inviterName(displayName(inviter))
                .inviterEmail(inviter.getEmail())
                .sections(invitation.getSections())
                .expired(expired)
                .alreadyAccepted(accepted)
                .email(invitation.getEmail())
                .build();
    }

    @Transactional
    public void acceptInvitation(User user, String token) {
        CabinetAccessInvitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new UserException("Приглашение не найдено", HttpStatus.NOT_FOUND));
        if (invitation.getStatus() != CabinetAccessInvitationStatus.PENDING) {
            throw new UserException("Приглашение уже обработано", HttpStatus.BAD_REQUEST);
        }
        if (invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            invitation.setStatus(CabinetAccessInvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
            throw new UserException("Срок действия приглашения истёк", HttpStatus.BAD_REQUEST);
        }
        if (!user.getEmail().equalsIgnoreCase(invitation.getEmail())) {
            throw new UserException("Приглашение отправлено на другой email", HttpStatus.FORBIDDEN);
        }
        LocalDateTime now = LocalDateTime.now();
        invitation.setStatus(CabinetAccessInvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(now);
        invitation.setAcceptedByUser(user);
        invitationRepository.save(invitation);

        upsertActiveGrant(
                invitation.getCabinet(),
                user,
                invitation.getInvitedByUser(),
                invitation.getSections(),
                invitation.getCommentText(),
                invitation.getValidUntil(),
                invitation
        );
        accountTypeService.ensureAccountType(user.getId(), AccountType.EMPLOYEE);
    }

    @Transactional(readOnly = true)
    public List<User> listCabinetOwnersAccessibleByUser(User user) {
        if (user.getRole() == Role.ADMIN) {
            return userRepository.findByRoleAndIsActive(Role.USER, true);
        }
        List<User> owners = new ArrayList<>();
        for (CabinetAccessGrant grant : grantRepository.findActiveGrantedForUser(
                user.getId(), CabinetAccessGrantStatus.ACTIVE, LocalDateTime.now())) {
            User owner = grant.getCabinet().getUser();
            if (Boolean.TRUE.equals(owner.getIsActive()) && owners.stream().noneMatch(o -> o.getId().equals(owner.getId()))) {
                owners.add(owner);
            }
        }
        return owners;
    }

    private void upsertActiveGrant(
            Cabinet cabinet,
            User grantee,
            User grantedBy,
            List<CabinetAccessSection> sections,
            String comment,
            LocalDateTime validUntil,
            CabinetAccessInvitation invitation
    ) {
        LocalDateTime now = LocalDateTime.now();
        CabinetAccessGrant grant = grantRepository.findByCabinet_IdAndUser_Id(cabinet.getId(), grantee.getId())
                .orElseGet(() -> CabinetAccessGrant.builder()
                        .cabinet(cabinet)
                        .user(grantee)
                        .validFrom(now)
                        .createdAt(now)
                        .build());
        grant.setSections(new ArrayList<>(sections));
        grant.setStatus(CabinetAccessGrantStatus.ACTIVE);
        grant.setCommentText(comment);
        grant.setValidUntil(validUntil);
        grant.setGrantedByUser(grantedBy);
        grant.setInvitation(invitation);
        grant.setRevokedAt(null);
        grant.setUpdatedAt(now);
        grantRepository.save(grant);
    }

    private void ensureCanManage(User user, Long cabinetId) {
        if (!canManageCabinet(user, cabinetId)) {
            throw new UserException("Нет прав на управление кабинетом", HttpStatus.FORBIDDEN);
        }
    }

    private void validateGrantRequest(GrantCabinetAccessRequest request) {
        if (request.email() == null || request.email().isBlank()) {
            throw new UserException("Укажите email", HttpStatus.BAD_REQUEST);
        }
        if (request.sections() == null || request.sections().isEmpty()) {
            throw new UserException("Выберите хотя бы один раздел", HttpStatus.BAD_REQUEST);
        }
    }

    private OwnedCabinetRowDto toOwnedRow(Cabinet cabinet) {
        return OwnedCabinetRowDto.builder()
                .id(cabinet.getId())
                .name(cabinet.getName())
                .createdAt(cabinet.getCreatedAt())
                .lastValidatedAt(cabinet.getLastValidatedAt())
                .apiKeyValid(cabinet.getIsValid())
                .lastDataUpdateAt(cabinet.getLastDataUpdateAt())
                .apiKeyMasked(maskApiKey(cabinet.getApiKey()))
                .build();
    }

    private GrantedCabinetRowDto toGrantedRow(CabinetAccessGrant grant) {
        Cabinet cabinet = grant.getCabinet();
        return GrantedCabinetRowDto.builder()
                .id(cabinet.getId())
                .name(cabinet.getName())
                .accessFrom(grant.getValidFrom())
                .accessUntil(grant.getValidUntil())
                .lastValidatedAt(cabinet.getLastValidatedAt())
                .apiKeyValid(cabinet.getIsValid())
                .lastDataUpdateAt(cabinet.getLastDataUpdateAt())
                .sections(grant.getSections())
                .build();
    }

    private CabinetAccessEntryDto toAccessEntry(CabinetAccessGrant grant) {
        User user = grant.getUser();
        String statusLabel = grant.getStatus() == CabinetAccessGrantStatus.ACTIVE ? "Активен" : "Доступ отозван";
        return CabinetAccessEntryDto.builder()
                .id(grant.getId())
                .kind("GRANT")
                .userName(displayName(user))
                .userEmail(user.getEmail())
                .sections(grant.getSections())
                .accessFrom(grant.getValidFrom())
                .accessUntil(grant.getValidUntil())
                .grantedByLabel(displayName(grant.getGrantedByUser()))
                .grantedAt(grant.getCreatedAt())
                .statusLabel(statusLabel)
                .build();
    }

    private CabinetAccessEntryDto toAccessEntry(CabinetAccessInvitation invitation) {
        return CabinetAccessEntryDto.builder()
                .id(invitation.getId())
                .kind("INVITATION")
                .userName(null)
                .userEmail(invitation.getEmail())
                .sections(invitation.getSections())
                .accessFrom(invitation.getCreatedAt())
                .accessUntil(invitation.getValidUntil())
                .grantedByLabel(displayName(invitation.getInvitedByUser()))
                .grantedAt(invitation.getCreatedAt())
                .invitationStatus(invitation.getStatus())
                .statusLabel("Приглашение отправлено / Ожидает принятия")
                .build();
    }

    private static String displayName(User user) {
        if (user == null) {
            return "—";
        }
        if (user.getName() != null && !user.getName().isBlank()) {
            return user.getName();
        }
        return user.getEmail();
    }

    private static boolean matchesSearch(String name, String q) {
        return q.isEmpty() || (name != null && name.toLowerCase().contains(q));
    }

    static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 12) {
            return apiKey;
        }
        return apiKey.substring(0, 8) + "..." + apiKey.substring(apiKey.length() - 8);
    }
}
