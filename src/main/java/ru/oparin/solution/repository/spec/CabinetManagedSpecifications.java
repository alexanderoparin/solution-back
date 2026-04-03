package ru.oparin.solution.repository.spec;

import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.model.User;

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

    public static Specification<Cabinet> managedList(User currentUser, String searchRaw) {
        return (root, query, cb) -> {
            Join<Cabinet, User> userJoin = root.join("user", JoinType.INNER);

            if (!isCountQuery(query)) {
                root.fetch("user", JoinType.INNER);
                query.distinct(true);
            }

            Predicate isSeller = cb.equal(userJoin.get("role"), Role.SELLER);
            Predicate scope;
            if (currentUser.getRole() == Role.ADMIN) {
                scope = isSeller;
            } else if (currentUser.getRole() == Role.MANAGER) {
                Join<User, User> owner = userJoin.join("owner", JoinType.INNER);
                scope = cb.and(isSeller, cb.equal(owner.get("id"), currentUser.getId()));
            } else {
                return cb.disjunction();
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
}
