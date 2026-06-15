package ru.oparin.solution.repository.spec;

import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;
import ru.oparin.solution.model.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Критерии выборки кабинетов для плоского списка в админке (ADMIN / MANAGER).
 */
public final class CabinetManagedSpecifications {

    private CabinetManagedSpecifications() {
    }

    private static boolean isCountQuery(CriteriaQuery<?> query) {
        Class<?> rt = query.getResultType();
        return Long.class.equals(rt) || long.class.equals(rt);
    }

    public static Specification<Cabinet> managedList(User currentUser, String searchRaw, boolean onlyActiveUsers) {
        return (root, query, cb) -> {
            Join<Cabinet, User> userJoin = root.join("user", JoinType.INNER);

            if (!isCountQuery(query)) {
                root.fetch("user", JoinType.INNER);
            }

            Predicate isSeller = cb.equal(userJoin.get("role"), Role.SELLER);
            Predicate scope;
            if (currentUser.getRole() == Role.ADMIN) {
                scope = isSeller;
            } else if (currentUser.getRole() == Role.MANAGER) {
                scope = cb.and(isSeller, managerCanAccessSeller(cb, query, userJoin, currentUser.getId()));
            } else {
                return cb.disjunction();
            }

            if (onlyActiveUsers) {
                scope = cb.and(scope, cb.isTrue(userJoin.get("isActive")));
            }

            if (!StringUtils.hasText(searchRaw)) {
                return scope;
            }

            String term = searchRaw.trim().toLowerCase();
            String like = "%" + term + "%";
            List<Predicate> orParts = new ArrayList<>();
            orParts.add(cb.like(cb.lower(userJoin.get("email")), like));
            orParts.add(cb.like(cb.lower(root.get("name")), like));
            try {
                long id = Long.parseLong(term);
                orParts.add(cb.equal(root.get("id"), id));
            } catch (NumberFormatException ignored) {
                // только подстроковый поиск
            }
            return cb.and(scope, cb.or(orParts.toArray(Predicate[]::new)));
        };
    }

    /**
     * Та же зона видимости, что у {@link #managedList(User, String, boolean)}, но только кабинеты с непустым API-ключом.
     */
    public static Specification<Cabinet> managedListWithApiKey(User currentUser) {
        return (root, query, cb) -> {
            Join<Cabinet, User> userJoin = root.join("user", JoinType.INNER);

            if (!isCountQuery(query)) {
                root.fetch("user", JoinType.INNER);
                // См. комментарий в {@link #managedList(User, String, boolean)} — без DISTINCT (PostgreSQL + ORDER BY lower).
            }

            Predicate isSeller = cb.equal(userJoin.get("role"), Role.SELLER);
            Predicate scope;
            if (currentUser.getRole() == Role.ADMIN) {
                scope = isSeller;
            } else if (currentUser.getRole() == Role.MANAGER) {
                scope = cb.and(isSeller, managerCanAccessSeller(cb, query, userJoin, currentUser.getId()));
            } else {
                return cb.disjunction();
            }

            Predicate hasKey = cb.and(
                    cb.isNotNull(root.get("apiKey")),
                    cb.notEqual(root.get("apiKey"), "")
            );
            return cb.and(scope, hasKey);
        };
    }

    /**
     * Менеджер видит селлера при активном grant.
     */
    private static Predicate managerCanAccessSeller(
            CriteriaBuilder cb,
            CriteriaQuery<?> query,
            Join<Cabinet, User> userJoin,
            Long managerId
    ) {
        Subquery<Long> grantSub = query.subquery(Long.class);
        Root<SellerManagerAccess> accessRoot = grantSub.from(SellerManagerAccess.class);
        grantSub.select(accessRoot.get("seller").get("id"));
        grantSub.where(
                cb.equal(accessRoot.get("manager").get("id"), managerId),
                cb.equal(accessRoot.get("status"), SellerManagerAccessStatus.ACTIVE)
        );

        return userJoin.get("id").in(grantSub);
    }
}
