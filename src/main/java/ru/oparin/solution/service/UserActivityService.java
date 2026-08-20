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
     *
     * @param userId ID пользователя
     */
    @Async("taskExecutor")
    public void touchLastSeenAt(Long userId) {
        touchLastSeenAtSync(userId);
    }

    @Transactional
    void touchLastSeenAtSync(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime minAllowedPreviousSeenAt = now.minusMinutes(LAST_SEEN_UPDATE_INTERVAL_MINUTES);
        userRepository.touchLastSeenAtIfOlderThan(userId, now, minAllowedPreviousSeenAt);
    }
}
