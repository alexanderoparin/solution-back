package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.repository.UserRepository;

import java.time.LocalDateTime;

/**
 * Сервис активности пользователя.
 */
@Service
@RequiredArgsConstructor
public class UserActivityService {

    private static final long LAST_SEEN_UPDATE_INTERVAL_MINUTES = 5L;

    private final UserRepository userRepository;

    /**
     * Обновляет время последней активности пользователя не чаще одного раза за интервал.
     * Асинхронно, чтобы не блокировать HTTP-поток и не конкурировать за пул соединений с WB-событиями.
     * {@code @Transactional} на том же методе, что и {@code @Async}: иначе self-invocation
     * не открывает транзакцию и {@code @Modifying}-запрос падает с TransactionRequiredException.
     *
     * @param userId ID пользователя
     */
    @Async("taskExecutor")
    @Transactional
    public void touchLastSeenAt(Long userId) {
        if (userId == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime minAllowedPreviousSeenAt = now.minusMinutes(LAST_SEEN_UPDATE_INTERVAL_MINUTES);
        userRepository.touchLastSeenAtIfOlderThan(userId, now, minAllowedPreviousSeenAt);
    }
}
