package ru.oparin.solution.dto;

import lombok.*;
import ru.oparin.solution.model.AccountType;
import ru.oparin.solution.model.Role;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO профиля пользователя.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    private Long id;
    private String name;
    private String email;
    private Role role;
    private List<AccountType> accountTypes;
    private Boolean isActive;
    private Boolean emailConfirmed;
    private Instant lastEmailConfirmationSentAt;
    private LocalDateTime createdAt;
    private ProfileSubscriptionSummaryDto subscription;
    private AccountDeletionStatusDto deletionRequest;
}
