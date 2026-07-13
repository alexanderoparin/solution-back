package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.model.AccountType;
import ru.oparin.solution.model.UserAccountType;
import ru.oparin.solution.repository.UserAccountTypeRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Управление номинальными типами аккаунта пользователя.
 */
@Service
@RequiredArgsConstructor
public class AccountTypeService {

    private final UserAccountTypeRepository userAccountTypeRepository;

    @Transactional(readOnly = true)
    public List<AccountType> getAccountTypes(Long userId) {
        return userAccountTypeRepository.findByUserId(userId).stream()
                .map(UserAccountType::getAccountType)
                .sorted(Enum::compareTo)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Transactional
    public void replaceAccountTypes(Long userId, List<AccountType> accountTypes) {
        if (accountTypes == null || accountTypes.isEmpty()) {
            throw new IllegalArgumentException("Укажите хотя бы один тип аккаунта");
        }
        Set<AccountType> unique = EnumSet.copyOf(accountTypes);
        userAccountTypeRepository.deleteByUserId(userId);
        LocalDateTime now = LocalDateTime.now();
        for (AccountType type : unique) {
            userAccountTypeRepository.save(UserAccountType.builder()
                    .userId(userId)
                    .accountType(type)
                    .createdAt(now)
                    .build());
        }
    }

    @Transactional
    public void ensureAccountType(Long userId, AccountType accountType) {
        if (!userAccountTypeRepository.existsByUserIdAndAccountType(userId, accountType)) {
            userAccountTypeRepository.save(UserAccountType.builder()
                    .userId(userId)
                    .accountType(accountType)
                    .createdAt(LocalDateTime.now())
                    .build());
        }
    }

    @Transactional(readOnly = true)
    public boolean hasAccountType(Long userId, AccountType accountType) {
        return userAccountTypeRepository.existsByUserIdAndAccountType(userId, accountType);
    }
}
