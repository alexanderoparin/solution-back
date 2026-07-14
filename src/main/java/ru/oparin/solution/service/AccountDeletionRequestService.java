package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.AccountDeletionRequestAdminDto;
import ru.oparin.solution.dto.AccountDeletionRequestDto;
import ru.oparin.solution.dto.AccountDeletionStatusDto;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.AccountDeletionRequest;
import ru.oparin.solution.model.AccountDeletionRequestStatus;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.AccountDeletionRequestRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Заявки пользователей на удаление аккаунта.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountDeletionRequestService {

    private final AccountDeletionRequestRepository repository;
    private final UserService userService;
    private final EmailService emailService;

    @Transactional(readOnly = true)
    public AccountDeletionStatusDto getStatus(Long userId) {
        return repository.findByUser_IdAndStatus(userId, AccountDeletionRequestStatus.PENDING)
                .map(r -> AccountDeletionStatusDto.builder()
                        .hasPendingRequest(true)
                        .status(r.getStatus())
                        .message("Запрос на удаление отправлен и ожидает обработки. "
                                + "Если запрос был отправлен по ошибке, обратитесь в поддержку corp@click-i.ru")
                        .build())
                .orElse(AccountDeletionStatusDto.builder()
                        .hasPendingRequest(false)
                        .build());
    }

    /**
     * Число заявок в статусе PENDING (для бейджа в админке).
     */
    @Transactional(readOnly = true)
    public long countPending() {
        return repository.countByStatus(AccountDeletionRequestStatus.PENDING);
    }

    @Transactional
    public void createRequest(User user, AccountDeletionRequestDto request) {
        if (request.reason() == null) {
            throw new UserException("Укажите причину удаления", HttpStatus.BAD_REQUEST);
        }
        if (repository.findByUser_IdAndStatus(user.getId(), AccountDeletionRequestStatus.PENDING).isPresent()) {
            throw new UserException("Заявка на удаление уже отправлена", HttpStatus.CONFLICT);
        }
        AccountDeletionRequest saved = repository.save(AccountDeletionRequest.builder()
                .user(user)
                .userEmail(user.getEmail())
                .userName(user.getName())
                .reason(request.reason())
                .commentText(request.comment())
                .status(AccountDeletionRequestStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build());
        notifyCorpInbox(user, saved);
    }

    @Transactional(readOnly = true)
    public List<AccountDeletionRequestAdminDto> listAll() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toAdminDto)
                .toList();
    }

    @Transactional
    public void approve(User admin, Long requestId) {
        AccountDeletionRequest request = loadPending(requestId);
        User targetUser = request.getUser();
        if (targetUser == null) {
            throw new UserException("Пользователь заявки уже удалён", HttpStatus.BAD_REQUEST);
        }
        fillUserSnapshotIfNeeded(request, targetUser);
        request.setStatus(AccountDeletionRequestStatus.APPROVED);
        request.setProcessedAt(LocalDateTime.now());
        request.setProcessedByUser(admin);
        repository.save(request);
        userService.runDeletionAsync(targetUser.getId());
    }

    /**
     * Отклонение заявки: статус {@link AccountDeletionRequestStatus#REJECTED}, удаление не запускается.
     * Пользователь сможет подать новую заявку.
     */
    @Transactional
    public void reject(User admin, Long requestId) {
        AccountDeletionRequest request = loadPending(requestId);
        request.setStatus(AccountDeletionRequestStatus.REJECTED);
        request.setProcessedAt(LocalDateTime.now());
        request.setProcessedByUser(admin);
        repository.save(request);
        log.info("Заявка на удаление id={} отклонена админом id={}", requestId, admin.getId());
    }

    private AccountDeletionRequest loadPending(Long requestId) {
        AccountDeletionRequest request = repository.findById(requestId)
                .orElseThrow(() -> new UserException("Заявка не найдена", HttpStatus.NOT_FOUND));
        if (request.getStatus() != AccountDeletionRequestStatus.PENDING) {
            throw new UserException("Заявка уже обработана", HttpStatus.BAD_REQUEST);
        }
        return request;
    }

    private void notifyCorpInbox(User user, AccountDeletionRequest request) {
        try {
            emailService.sendAccountDeletionRequestNotification(
                    user,
                    request.getId(),
                    request.getReason(),
                    request.getCommentText()
            );
        } catch (Exception e) {
            log.error("Не удалось отправить уведомление о заявке на удаление id={}: {}",
                    request.getId(), e.getMessage(), e);
        }
    }

    /**
     * Дозаполняет snapshot email/name перед удалением пользователя (для старых заявок без snapshot).
     */
    private void fillUserSnapshotIfNeeded(AccountDeletionRequest request, User user) {
        if (request.getUserEmail() == null || request.getUserEmail().isBlank()) {
            request.setUserEmail(user.getEmail());
        }
        if (request.getUserName() == null || request.getUserName().isBlank()) {
            request.setUserName(user.getName());
        }
    }

    private AccountDeletionRequestAdminDto toAdminDto(AccountDeletionRequest request) {
        User user = request.getUser();
        Long userId = user != null ? user.getId() : null;
        String email = request.getUserEmail() != null
                ? request.getUserEmail()
                : (user != null ? user.getEmail() : "—");
        String name = request.getUserName() != null
                ? request.getUserName()
                : (user != null ? user.getName() : null);
        return AccountDeletionRequestAdminDto.builder()
                .id(request.getId())
                .userId(userId)
                .userEmail(email)
                .userName(name)
                .reason(request.getReason())
                .comment(request.getCommentText())
                .status(request.getStatus())
                .createdAt(request.getCreatedAt())
                .build();
    }
}
