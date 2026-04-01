package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.CabinetUpdateError;
import ru.oparin.solution.model.CabinetUpdateErrorScope;
import ru.oparin.solution.repository.CabinetRepository;
import ru.oparin.solution.repository.CabinetUpdateErrorRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class CabinetUpdateErrorService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 4000;

    private final CabinetUpdateErrorRepository repository;
    private final CabinetRepository cabinetRepository;

    @Transactional
    public void recordError(Long cabinetId, CabinetUpdateErrorScope scope, String errorMessage) {
        Cabinet cabinet = cabinetRepository.findById(cabinetId)
                .orElseThrow(() -> new IllegalArgumentException("Кабинет не найден: " + cabinetId));

        CabinetUpdateError updateError = CabinetUpdateError.builder()
                .cabinet(cabinet)
                .scope(scope)
                .occurredAt(LocalDateTime.now())
                .errorMessage(sanitize(errorMessage))
                .build();
        repository.save(updateError);
    }

    private String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "Неизвестная ошибка обновления";
        }
        String normalized = message.replace("<EOL>", " ").replace("\n", " ").trim();
        if (normalized.length() > MAX_ERROR_MESSAGE_LENGTH) {
            return normalized.substring(0, MAX_ERROR_MESSAGE_LENGTH) + "...";
        }
        return normalized;
    }
}
