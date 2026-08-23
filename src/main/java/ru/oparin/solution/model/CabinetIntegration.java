package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Учётные данные одной интеграции кабинета (WB / Ozon Seller / Ozon Performance).
 * Phase 5.2: каноническое хранение в {@code cabinet_integrations}; поля {@link Cabinet} — {@code @Transient} overlay.
 */
@Entity
@Table(
        name = "cabinet_integrations",
        schema = "solution",
        uniqueConstraints = @UniqueConstraint(columnNames = {"cabinet_id", "integration_type"})
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CabinetIntegration {

    /**
     * Уникальный идентификатор записи.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Идентификатор кабинета.
     */
    @Column(name = "cabinet_id", nullable = false)
    private Long cabinetId;

    /**
     * Тип интеграции ({@link CabinetIntegrationType}).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "integration_type", nullable = false, length = 32)
    private CabinetIntegrationType integrationType;

    /**
     * Основной секрет: api_key или Performance client_secret.
     */
    @Column(name = "credential_primary", length = 500)
    private String credentialPrimary;

    /**
     * Вторичный идентификатор: Ozon client_id.
     */
    @Column(name = "credential_secondary", length = 128)
    private String credentialSecondary;

    /**
     * JSON-метаданные (например {@code {"tokenType":"BASIC"}}).
     */
    @Column(name = "meta_json", columnDefinition = "TEXT")
    private String metaJson;

    /**
     * Результат последней валидации учётных данных.
     */
    @Column(name = "is_valid")
    private Boolean isValid;

    /**
     * Время последней проверки учётных данных.
     */
    @Column(name = "last_validated_at")
    private LocalDateTime lastValidatedAt;

    /**
     * Текст ошибки последней валидации.
     */
    @Column(name = "validation_error", columnDefinition = "TEXT")
    private String validationError;

    /**
     * Дата создания записи.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Дата последнего обновления записи.
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
