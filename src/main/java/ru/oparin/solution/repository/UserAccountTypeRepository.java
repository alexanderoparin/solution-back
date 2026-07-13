package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.oparin.solution.model.AccountType;
import ru.oparin.solution.model.UserAccountType;

import java.util.List;

public interface UserAccountTypeRepository extends JpaRepository<UserAccountType, UserAccountType.UserAccountTypeId> {

    List<UserAccountType> findByUserId(Long userId);

    void deleteByUserId(Long userId);

    boolean existsByUserIdAndAccountType(Long userId, AccountType accountType);
}
