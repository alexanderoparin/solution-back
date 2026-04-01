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

        List<Predicate> predicates = buildPredicates(cb, cq, root, currentUser, email, onlySellers);
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
        List<Predicate> countPredicates = buildPredicates(cb, countQuery, countRoot, currentUser, email, onlySellers);
        countQuery.select(cb.countDistinct(countRoot));
        countQuery.where(countPredicates.toArray(new Predicate[0]));
        return entityManager.createQuery(countQuery).getSingleResult();
    }

    private <T> List<Predicate> buildPredicates(CriteriaBuilder cb,
                                                CriteriaQuery<T> query,
                                                Root<User> root,
                                                User currentUser,
                                                String email,
                                                boolean onlySellers) {
        List<Predicate> predicates = new ArrayList<>();

        Role role = currentUser.getRole();
        if (role == Role.ADMIN) {
            if (onlySellers) {
                predicates.add(cb.equal(root.get("role"), Role.SELLER));
            } else {
                predicates.add(cb.notEqual(root.get("role"), Role.ADMIN));
            }
        } else if (role == Role.MANAGER) {
            // Менеджер всегда работает только со своими селлерами.
            predicates.add(cb.equal(root.get("role"), Role.SELLER));
            predicates.add(cb.equal(root.get("owner").get("id"), currentUser.getId()));
        } else if (role == Role.SELLER) {
            predicates.add(cb.equal(root.get("role"), Role.WORKER));
            predicates.add(cb.equal(root.get("owner").get("id"), currentUser.getId()));
        } else {
            predicates.add(cb.disjunction());
        }

        String trimmed = email == null ? null : email.trim();
        if (trimmed != null && !trimmed.isEmpty()) {
            predicates.add(cb.like(cb.lower(root.get("email")), "%" + trimmed.toLowerCase() + "%"));
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
                    ? "lastDataUpdateAt"
                    : "lastDataUpdateRequestedAt";

            Subquery<LocalDateTime> aggregateSubquery = cq.subquery(LocalDateTime.class);
            Root<Cabinet> cabinetRoot = aggregateSubquery.from(Cabinet.class);
            Expression<LocalDateTime> aggregateField = cabinetRoot.get(field).as(LocalDateTime.class);
            aggregateSubquery.select(cb.greatest(aggregateField));
            aggregateSubquery.where(cb.equal(cabinetRoot.get("user").get("id"), root.get("id")));

            Expression<Integer> nullRank = cb.<Integer>selectCase()
                    .when(cb.isNull(aggregateSubquery), asc ? 0 : 1)
                    .otherwise(asc ? 1 : 0);

            cq.orderBy(
                    cb.asc(nullRank),
                    asc ? cb.asc(aggregateSubquery) : cb.desc(aggregateSubquery),
                    cb.asc(root.get("id"))
            );
            return;
        }

        Expression<?> sortExpression = switch (sortBy) {
            case EMAIL -> root.get("email");
            case ROLE -> root.get("role");
            case IS_ACTIVE -> root.get("isActive");
            default -> root.get("createdAt");
        };

        cq.orderBy(
                asc ? cb.asc(sortExpression) : cb.desc(sortExpression),
                cb.asc(root.get("id"))
        );
    }
}
