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
        List<PendingCabinetInvitationRowDto> pendingInvitations = listPendingInvitationsForEmail(currentUser.getEmail()).stream()
                .filter(inv -> matchesSearch(inv.cabinetName(), q))
                .collect(Collectors.toCollection(ArrayList::new));
        return CabinetsOverviewDto.builder()
                .owned(owned)
                .granted(granted)
                .pendingInvitations(pendingInvitations)
                .build();
    }

    /**
     * Активные (ожидающие принятия) приглашения на email пользователя.
     */
    @Transactional(readOnly = true)
    public List<PendingCabinetInvitationRowDto> listPendingInvitationsForEmail(String email) {
        LocalDateTime now = LocalDateTime.now();
        return invitationRepository
                .findByEmailIgnoreCaseAndStatus(email, CabinetAccessInvitationStatus.PENDING)
                .stream()
                .filter(inv -> inv.getExpiresAt() == null || inv.getExpiresAt().isAfter(now))
                .map(this::toPendingInvitationRow)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isCabinetOwner(User user, Long cabinetId) {
        if (user.getRole() == Role.ADMIN) {
            return cabinetRepository.existsById(cabinetId);
        }
        return cabinetRepository.existsByIdAndUser_Id(cabinetId, user.getId());
    }

    /**
     * Управление доступами к кабинету — только владелец (не ADMIN).
     */
    @Transactional(readOnly = true)
    public boolean canManageCabinet(User user, Long cabinetId) {
        return cabinetRepository.existsByIdAndUser_Id(cabinetId, user.getId());
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

    /**
     * Требует доступ хотя бы к одному из указанных разделов кабинета.
     *
     * @throws UserException 403, если раздел недоступен
     */
    @Transactional(readOnly = true)
    public void requireAnySectionAccess(User user, Long cabinetId, CabinetAccessSection... sections) {
        if (sections == null || sections.length == 0) {
            return;
        }
        for (CabinetAccessSection section : sections) {
            if (hasSectionAccess(user, cabinetId, section)) {
                return;
            }
        }
        throw new UserException("Нет доступа к этому разделу кабинета", HttpStatus.FORBIDDEN);
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
            // ACCEPTED не показываем отдельно — доступ уже в ACTIVE grant
            if (invitation.getStatus() != CabinetAccessInvitationStatus.ACCEPTED) {
                result.add(toAccessEntry(invitation));
            }
        }
        return result;
    }

    /**
     * Создаёт PENDING-приглашение и отправляет письмо со ссылкой.
     * Активный доступ (grant) появляется только после принятия приглашения.
     */
    @Transactional
    public void grantAccess(User owner, Long cabinetId, GrantCabinetAccessRequest request) {
        ensureCanManage(owner, cabinetId);
        validateGrantRequest(request);
        String email = request.email().trim().toLowerCase();
        Cabinet cabinet = cabinetRepository.findById(cabinetId)
                .orElseThrow(() -> new UserException("Кабинет не найден", HttpStatus.NOT_FOUND));

        if (email.equalsIgnoreCase(owner.getEmail())) {
            throw new UserException("Нельзя выдать доступ самому себе", HttpStatus.BAD_REQUEST);
        }
        Optional<User> existingUser = userRepository.findByEmail(email);
        if (existingUser.isPresent() && existingUser.get().getId().equals(owner.getId())) {
            throw new UserException("Нельзя выдать доступ самому себе", HttpStatus.BAD_REQUEST);
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
                .accountType(request.accountType())
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

    @Transactional
    public void updateGrantValidUntil(User owner, Long cabinetId, Long grantId, LocalDateTime validUntil) {
        ensureCanManage(owner, cabinetId);
        CabinetAccessGrant grant = grantRepository.findById(grantId)
                .orElseThrow(() -> new UserException("Доступ не найден", HttpStatus.NOT_FOUND));
        if (!grant.getCabinet().getId().equals(cabinetId)) {
            throw new UserException("Доступ не принадлежит кабинету", HttpStatus.BAD_REQUEST);
        }
        if (grant.getStatus() != CabinetAccessGrantStatus.ACTIVE) {
            throw new UserException("Можно изменить срок только для активного доступа", HttpStatus.BAD_REQUEST);
        }
        validateValidUntil(validUntil);
        grant.setValidUntil(validUntil);
        grantRepository.save(grant);
    }

    @Transactional
    public void updateInvitationValidUntil(User owner, Long cabinetId, Long invitationId, LocalDateTime validUntil) {
        ensureCanManage(owner, cabinetId);
        CabinetAccessInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new UserException("Приглашение не найдено", HttpStatus.NOT_FOUND));
        if (!invitation.getCabinet().getId().equals(cabinetId)) {
            throw new UserException("Приглашение не принадлежит кабинету", HttpStatus.BAD_REQUEST);
        }
        if (invitation.getStatus() != CabinetAccessInvitationStatus.PENDING) {
            throw new UserException("Можно изменить срок только для ожидающего приглашения", HttpStatus.BAD_REQUEST);
        }
        validateValidUntil(validUntil);
        invitation.setValidUntil(validUntil);
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
                .declined(invitation.getStatus() == CabinetAccessInvitationStatus.DECLINED)
                .revoked(invitation.getStatus() == CabinetAccessInvitationStatus.REVOKED)
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
        applyGrantedAccountType(user.getId(), invitation.getAccountType());
    }

    /**
     * Отклонение приглашения приглашённым пользователем.
     * Статус становится {@link CabinetAccessInvitationStatus#DECLINED}, доступ не создаётся.
     */
    @Transactional
    public void declineInvitation(User user, String token) {
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
        invitation.setStatus(CabinetAccessInvitationStatus.DECLINED);
        invitationRepository.save(invitation);
        log.info("Приглашение id={} отклонено пользователем id={}", invitation.getId(), user.getId());
    }

    /**
     * Повторная отправка приглашения по отозванному / отклонённому / истёкшему приглашению.
     * Создаётся новое PENDING-приглашение с теми же параметрами доступа.
     */
    @Transactional
    public void resendInvitation(User owner, Long cabinetId, Long invitationId) {
        ensureCanManage(owner, cabinetId);
        CabinetAccessInvitation source = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new UserException("Приглашение не найдено", HttpStatus.NOT_FOUND));
        if (!source.getCabinet().getId().equals(cabinetId)) {
            throw new UserException("Приглашение не принадлежит кабинету", HttpStatus.BAD_REQUEST);
        }
        if (!canResendInvitation(source.getStatus())) {
            throw new UserException("Повторно отправить можно только отозванное, отклонённое или истёкшее приглашение",
                    HttpStatus.BAD_REQUEST);
        }

        invitationRepository.findPendingByCabinetAndEmail(
                        cabinetId, source.getEmail(), CabinetAccessInvitationStatus.PENDING)
                .ifPresent(inv -> {
                    inv.setStatus(CabinetAccessInvitationStatus.REVOKED);
                    invitationRepository.save(inv);
                });

        String token = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();
        CabinetAccessInvitation invitation = CabinetAccessInvitation.builder()
                .token(token)
                .email(source.getEmail())
                .cabinet(source.getCabinet())
                .invitedByUser(owner)
                .sections(new ArrayList<>(source.getSections()))
                .accountType(source.getAccountType())
                .commentText(source.getCommentText())
                .status(CabinetAccessInvitationStatus.PENDING)
                .validUntil(source.getValidUntil())
                .expiresAt(now.plusDays(INVITATION_TTL_DAYS))
                .createdAt(now)
                .updatedAt(now)
                .build();
        invitationRepository.save(invitation);
        emailService.sendCabinetInvitationEmail(
                source.getEmail(), owner, source.getCabinet().getName(), token);
        log.info("Повторно отправлено приглашение id={} (источник id={}) на {}",
                invitation.getId(), source.getId(), source.getEmail());
    }

    private static boolean canResendInvitation(CabinetAccessInvitationStatus status) {
        return status == CabinetAccessInvitationStatus.REVOKED
                || status == CabinetAccessInvitationStatus.DECLINED
                || status == CabinetAccessInvitationStatus.EXPIRED;
    }

    /**
     * При выдаче доступа владелец указывает тип «Агентство» или «Сотрудник» —
     * он добавляется в профиль пользователя, если ещё не был назначен.
     */
    private void applyGrantedAccountType(Long userId, AccountType accountType) {
        accountTypeService.ensureAccountType(userId, accountType);
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
            throw new UserException("Только владелец кабинета может управлять доступами", HttpStatus.FORBIDDEN);
        }
    }

    private void validateGrantRequest(GrantCabinetAccessRequest request) {
        if (request.email() == null || request.email().isBlank()) {
            throw new UserException("Укажите email", HttpStatus.BAD_REQUEST);
        }
        if (request.sections() == null || request.sections().isEmpty()) {
            throw new UserException("Выберите хотя бы один раздел", HttpStatus.BAD_REQUEST);
        }
        if (request.accountType() == null) {
            throw new UserException("Выберите тип аккаунта", HttpStatus.BAD_REQUEST);
        }
        if (request.accountType() != AccountType.AGENCY && request.accountType() != AccountType.EMPLOYEE) {
            throw new UserException("Доступ можно выдать только с типом «Агентство» или «Сотрудник»", HttpStatus.BAD_REQUEST);
        }
        validateValidUntil(request.validUntil());
    }

    private void validateValidUntil(LocalDateTime validUntil) {
        if (validUntil != null && validUntil.isBefore(LocalDateTime.now())) {
            throw new UserException("Дата окончания доступа не может быть в прошлом", HttpStatus.BAD_REQUEST);
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
                .apiKeyMasked(maskApiKey(cabinet.getApiKey()))
                .grantedByName(displayName(grant.getGrantedByUser()))
                .sections(grant.getSections())
                .build();
    }

    private PendingCabinetInvitationRowDto toPendingInvitationRow(CabinetAccessInvitation invitation) {
        User inviter = invitation.getInvitedByUser();
        return PendingCabinetInvitationRowDto.builder()
                .token(invitation.getToken())
                .cabinetId(invitation.getCabinet().getId())
                .cabinetName(invitation.getCabinet().getName())
                .inviterName(displayName(inviter))
                .inviterEmail(inviter != null ? inviter.getEmail() : null)
                .sections(invitation.getSections())
                .accessUntil(invitation.getValidUntil())
                .expiresAt(invitation.getExpiresAt())
                .createdAt(invitation.getCreatedAt())
                .build();
    }

    private CabinetAccessEntryDto toAccessEntry(CabinetAccessGrant grant) {
        User user = grant.getUser();
        String statusLabel = grant.getStatus() == CabinetAccessGrantStatus.ACTIVE ? "Активен" : "Доступ отозван";
        return CabinetAccessEntryDto.builder()
                .id(grant.getId())
                .kind("GRANT")
                .userName(resolveUserName(user))
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
                .statusLabel(invitationStatusLabel(invitation.getStatus()))
                .build();
    }

    private static String invitationStatusLabel(CabinetAccessInvitationStatus status) {
        return switch (status) {
            case PENDING -> "Ожидает принятия";
            case REVOKED -> "Отозвано владельцем";
            case DECLINED -> "Отклонено пользователем";
            case EXPIRED -> "Приглашение истекло";
            case ACCEPTED -> "Принято";
        };
    }

    private static String resolveUserName(User user) {
        if (user == null || user.getName() == null || user.getName().isBlank()) {
            return null;
        }
        return user.getName();
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
