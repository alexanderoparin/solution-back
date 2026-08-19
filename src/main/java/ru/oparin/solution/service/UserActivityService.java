package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
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
     *
     * @param userId ID пользователя
     */
    @Transactional
    public void touchLastSeenAt(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime minAllowedPreviousSeenAt = now.minusMinutes(LAST_SEEN_UPDATE_INTERVAL_MINUTES);
        userRepository.touchLastSeenAtIfOlderThan(userId, now, minAllowedPreviousSeenAt);
    }
}
