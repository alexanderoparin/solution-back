package ru.oparin.solution.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import ru.oparin.solution.dto.UserSortField;
import ru.oparin.solution.model.User;

public interface UserManagementCriteriaRepository {
    Page<User> findManagedUsersByCriteria(User currentUser,
                                          Pageable pageable,
                                          String email,
                                          boolean onlySellers,
                                          UserSortField sortBy,
                                          Sort.Direction sortDir);
}
