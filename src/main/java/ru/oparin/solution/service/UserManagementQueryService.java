package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import ru.oparin.solution.dto.UserSortField;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class UserManagementQueryService {

    private final UserRepository userRepository;

    public Page<User> findManagedUsers(User currentUser,
                                       Pageable pageable,
                                       String email,
                                       boolean onlySellers,
                                       UserSortField sortBy,
                                       Sort.Direction sortDir) {
        return userRepository.findManagedUsersByCriteria(
                currentUser,
                pageable,
                email,
                onlySellers,
                sortBy,
                sortDir
        );
    }
}
