package ru.oparin.solution.repository.spec;

import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;
import ru.oparin.solution.model.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Критерии выборки кабинетов для плоского списка в админке (ADMIN).
 */
public final class CabinetManagedSpecifications {

    private CabinetManagedSpecifications() {
    }

    private static boolean isCountQuery(CriteriaQuery<?> query) {
        Class<?> rt = query.getResultType();
        return Long.class.equals(rt) || long.class.equals(rt);
    }

    public static Specification<Cabinet> managedList(
            User currentUser,
            String searchRaw,
            boolean onlyActiveUsers,
            MarketplaceType marketplaceType
    ) {
        return (root, query, cb) -> {
            Join<Cabinet, User> userJoin = root.join("user", JoinType.INNER);

            if (!isCountQuery(query)) {
                root.fetch("user", JoinType.INNER);
            }

            if (currentUser.getRole() != Role.ADMIN) {
                return cb.disjunction();
            }

            Predicate scope = cb.equal(userJoin.get("role"), Role.USER);

            if (onlyActiveUsers) {
                scope = cb.and(scope, cb.isTrue(userJoin.get("isActive")));
            }

            if (marketplaceType != null) {
                if (marketplaceType == MarketplaceType.WB) {
                    scope = cb.and(scope, cb.or(
                            cb.equal(root.get("marketplaceType"), MarketplaceType.WB),
                            cb.isNull(root.get("marketplaceType"))
                    ));
                } else {
                    scope = cb.and(scope, cb.equal(root.get("marketplaceType"), marketplaceType));
                }
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
     * Та же зона видимости, что у {@link #managedList(User, String, boolean, MarketplaceType)}, но только кабинеты с непустым API-ключом.
     */
    public static Specification<Cabinet> managedListWithApiKey(User currentUser) {
        return (root, query, cb) -> {
            Join<Cabinet, User> userJoin = root.join("user", JoinType.INNER);

            if (!isCountQuery(query)) {
                root.fetch("user", JoinType.INNER);
            }

            if (currentUser.getRole() != Role.ADMIN) {
                return cb.disjunction();
            }

            Predicate scope = cb.equal(userJoin.get("role"), Role.USER);
            Subquery<Long> integrationSubquery = query.subquery(Long.class);
            Root<CabinetIntegration> integrationRoot = integrationSubquery.from(CabinetIntegration.class);
            integrationSubquery.select(integrationRoot.get("cabinetId"));
            integrationSubquery.where(
                    cb.equal(integrationRoot.get("cabinetId"), root.get("id")),
                    cb.equal(integrationRoot.get("integrationType"), CabinetIntegrationType.WB_API),
                    cb.isNotNull(integrationRoot.get("credentialPrimary")),
                    cb.notEqual(integrationRoot.get("credentialPrimary"), "")
            );
            Predicate hasKey = cb.exists(integrationSubquery);
            return cb.and(scope, hasKey);
        };
    }
}
