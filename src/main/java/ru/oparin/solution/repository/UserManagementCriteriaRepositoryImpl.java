package ru.oparin.solution.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.dto.UserSortField;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.model.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class UserManagementCriteriaRepositoryImpl implements UserManagementCriteriaRepository {

    private static final String USER_ROLE_FIELD = "role";
    private static final String USER_OWNER_FIELD = "owner";
    private static final String USER_ID_FIELD = "id";
    private static final String USER_EMAIL_FIELD = "email";
    private static final String CABINET_USER_FIELD = "user";
    private static final String CABINET_LAST_UPDATE_AT_FIELD = "lastDataUpdateAt";
    private static final String CABINET_LAST_UPDATE_REQUESTED_AT_FIELD = "lastDataUpdateRequestedAt";

    private final EntityManager entityManager;

    @Override
    public Page<User> findManagedUsersByCriteria(User currentUser,
                                                 Pageable pageable,
                                                 String email,
                                                 boolean onlySellers,
                                                 UserSortField sortBy,
                                                 Sort.Direction sortDir) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<User> cq = cb.createQuery(User.class);
        Root<User> root = cq.from(User.class);

        List<Predicate> predicates = buildPredicates(cb, root, currentUser, email, onlySellers);
        cq.where(predicates.toArray(new Predicate[0]));

        applySorting(cb, cq, root, sortBy, sortDir);

        TypedQuery<User> query = entityManager.createQuery(cq);
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());
        List<User> content = query.getResultList();

        long total = count(cb, currentUser, email, onlySellers);
        return new PageImpl<>(content, pageable, total);
    }

    private long count(CriteriaBuilder cb, User currentUser, String email, boolean onlySellers) {
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<User> countRoot = countQuery.from(User.class);
        List<Predicate> countPredicates = buildPredicates(cb, countRoot, currentUser, email, onlySellers);
        countQuery.select(cb.countDistinct(countRoot));
        countQuery.where(countPredicates.toArray(new Predicate[0]));
        return entityManager.createQuery(countQuery).getSingleResult();
    }

    private List<Predicate> buildPredicates(CriteriaBuilder cb,
                                            Root<User> root,
                                            User currentUser,
                                            String email,
                                            boolean onlySellers) {
        List<Predicate> predicates = new ArrayList<>();

        Role role = currentUser.getRole();
        if (role == Role.ADMIN) {
            if (onlySellers) {
                predicates.add(cb.equal(root.get(USER_ROLE_FIELD), Role.SELLER));
            } else {
                predicates.add(cb.notEqual(root.get(USER_ROLE_FIELD), Role.ADMIN));
            }
        } else if (role == Role.MANAGER) {
            // Менеджер всегда работает только со своими селлерами.
            predicates.add(cb.equal(root.get(USER_ROLE_FIELD), Role.SELLER));
            predicates.add(cb.equal(root.get(USER_OWNER_FIELD).get(USER_ID_FIELD), currentUser.getId()));
        } else if (role == Role.SELLER) {
            predicates.add(cb.equal(root.get(USER_ROLE_FIELD), Role.WORKER));
            predicates.add(cb.equal(root.get(USER_OWNER_FIELD).get(USER_ID_FIELD), currentUser.getId()));
        } else {
            predicates.add(cb.disjunction());
        }

        String trimmed = email == null ? null : email.trim();
        if (trimmed != null && !trimmed.isEmpty()) {
            predicates.add(cb.like(cb.lower(root.get(USER_EMAIL_FIELD)), "%" + trimmed.toLowerCase() + "%"));
        }

        return predicates;
    }

    private void applySorting(CriteriaBuilder cb,
                              CriteriaQuery<User> cq,
                              Root<User> root,
                              UserSortField sortBy,
                              Sort.Direction sortDir) {
        boolean asc = sortDir == Sort.Direction.ASC;

        if (sortBy == UserSortField.LAST_DATA_UPDATE_AT || sortBy == UserSortField.LAST_DATA_UPDATE_REQUESTED_AT) {
            String field = sortBy == UserSortField.LAST_DATA_UPDATE_AT
                    ? CABINET_LAST_UPDATE_AT_FIELD
                    : CABINET_LAST_UPDATE_REQUESTED_AT_FIELD;

            Subquery<LocalDateTime> aggregateSubquery = cq.subquery(LocalDateTime.class);
            Root<Cabinet> cabinetRoot = aggregateSubquery.from(Cabinet.class);
            Expression<LocalDateTime> aggregateField = cabinetRoot.get(field).as(LocalDateTime.class);
            aggregateSubquery.select(cb.greatest(aggregateField));
            aggregateSubquery.where(cb.equal(cabinetRoot.get(CABINET_USER_FIELD).get(USER_ID_FIELD), root.get(USER_ID_FIELD)));

            Expression<Integer> nullRank = cb.<Integer>selectCase()
                    .when(cb.isNull(aggregateSubquery), asc ? 0 : 1)
                    .otherwise(asc ? 1 : 0);

            cq.orderBy(
                    cb.asc(nullRank),
                    asc ? cb.asc(aggregateSubquery) : cb.desc(aggregateSubquery),
                    cb.asc(root.get(USER_ID_FIELD))
            );
            return;
        }

        Expression<?> sortExpression = switch (sortBy) {
            case EMAIL -> root.get(USER_EMAIL_FIELD);
            case ROLE -> root.get(USER_ROLE_FIELD);
            case IS_ACTIVE -> root.get("isActive");
            default -> root.get("createdAt");
        };

        cq.orderBy(
                asc ? cb.asc(sortExpression) : cb.desc(sortExpression),
                cb.asc(root.get(USER_ID_FIELD))
        );
    }
}
