package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "cabinet_update_errors", schema = "solution")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CabinetUpdateError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cabinet_id", nullable = false)
    private Cabinet cabinet;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 20)
    private CabinetUpdateErrorScope scope;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "error_message", nullable = false, columnDefinition = "TEXT")
    private String errorMessage;
}
