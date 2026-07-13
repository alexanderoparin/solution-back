package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.CabinetRepository;
import ru.oparin.solution.repository.UserRepository;

import java.util.Comparator;
import java.util.List;

/**
 * Контекст кабинета для аналитики и рекламы.
 */
@Service
@RequiredArgsConstructor
public class SellerContextService {

    private final UserService userService;
    private final WbApiKeyService wbApiKeyService;
    private final UserRepository userRepository;
    private final CabinetService cabinetService;
    private final CabinetAccessService cabinetAccessService;
    private final CabinetRepository cabinetRepository;

    @Transactional(readOnly = true)
    public SellerContext createContext(Authentication authentication, Long sellerId, Long cabinetId) {
        User currentUser = userService.findByEmail(authentication.getName());
        User owner;
        Cabinet cabinet;

        if (currentUser.getRole() == Role.ADMIN) {
            owner = resolveAdminOwner(sellerId, cabinetId);
            cabinet = resolveCabinet(owner.getId(), cabinetId);
        } else if (cabinetId != null) {
            cabinet = cabinetRepository.findById(cabinetId)
                    .orElseThrow(() -> new UserException("Кабинет не найден", HttpStatus.NOT_FOUND));
            owner = cabinet.getUser();
            if (!cabinetAccessService.isCabinetOwner(currentUser, cabinetId)
                    && !cabinetAccessService.hasSectionAccess(currentUser, cabinetId,
                    ru.oparin.solution.model.CabinetAccessSection.SUMMARY)
                    && !cabinetAccessService.hasSectionAccess(currentUser, cabinetId,
                    ru.oparin.solution.model.CabinetAccessSection.PRODUCTS)
                    && !cabinetAccessService.hasSectionAccess(currentUser, cabinetId,
                    ru.oparin.solution.model.CabinetAccessSection.AD_CAMPAIGNS)
                    && !cabinetAccessService.hasSectionAccess(currentUser, cabinetId,
                    ru.oparin.solution.model.CabinetAccessSection.CAMPAIGN_MANAGE)) {
                throw new UserException("Нет доступа к кабинету", HttpStatus.FORBIDDEN);
            }
        } else if (cabinetRepository.existsByIdAndUser_Id(
                wbApiKeyService.findDefaultCabinetByUserId(currentUser.getId()).getId(), currentUser.getId())) {
            owner = currentUser;
            cabinet = wbApiKeyService.findDefaultCabinetByUserId(currentUser.getId());
        } else {
            var grants = cabinetAccessService.getOverview(currentUser, null).granted();
            if (grants.isEmpty()) {
                throw new UserException("Не найдено доступных кабинетов", HttpStatus.NOT_FOUND);
            }
            cabinet = cabinetRepository.findById(grants.get(0).id())
                    .orElseThrow(() -> new UserException("Кабинет не найден", HttpStatus.NOT_FOUND));
            owner = cabinet.getUser();
        }

        if (!Boolean.TRUE.equals(owner.getIsActive())) {
            throw new UserException("Аккаунт владельца кабинета неактивен", HttpStatus.FORBIDDEN);
        }
        return new SellerContext(owner, cabinet);
    }

    public SellerContext createContext(Authentication authentication, Long sellerId) {
        return createContext(authentication, sellerId, null);
    }

    public SellerContext createContext(Authentication authentication) {
        return createContext(authentication, null, null);
    }

    private User resolveAdminOwner(Long sellerId, Long cabinetId) {
        if (cabinetId != null) {
            Cabinet cabinet = cabinetRepository.findById(cabinetId)
                    .orElseThrow(() -> new UserException("Кабинет не найден", HttpStatus.NOT_FOUND));
            return cabinet.getUser();
        }
        if (sellerId != null) {
            User seller = userRepository.findById(sellerId)
                    .orElseThrow(() -> new UserException("Пользователь не найден: " + sellerId, HttpStatus.NOT_FOUND));
            return seller;
        }
        List<User> owners = userRepository.findByRoleAndIsActive(Role.USER, true).stream()
                .filter(u -> cabinetService.findDefaultByUserId(u.getId()).isPresent())
                .sorted(Comparator.comparing(User::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        if (owners.isEmpty()) {
            throw new UserException("Не найдено активных кабинетов", HttpStatus.NOT_FOUND);
        }
        return owners.get(0);
    }

    private Cabinet resolveCabinet(Long ownerId, Long cabinetId) {
        if (cabinetId != null && cabinetService.existsByIdAndUser_Id(cabinetId, ownerId)) {
            return cabinetService.findById(cabinetId)
                    .orElseGet(() -> wbApiKeyService.findDefaultCabinetByUserId(ownerId));
        }
        return wbApiKeyService.findDefaultCabinetByUserId(ownerId);
    }

    public record SellerContext(User user, Cabinet cabinet) {
        public String apiKey() {
            return cabinet != null ? cabinet.getApiKey() : null;
        }

        public Long cabinetId() {
            return cabinet != null ? cabinet.getId() : null;
        }
    }
}
