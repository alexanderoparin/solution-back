package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.SellerManagerAccessDto;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.model.SellerManagerAccess;
import ru.oparin.solution.model.SellerManagerAccessStatus;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.SellerManagerAccessRepository;
import ru.oparin.solution.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Делегирование селлером доступа менеджерам ко всем кабинетам селлера.
 */
@Service
@RequiredArgsConstructor
public class SellerManagerAccessService {

    private final SellerManagerAccessRepository accessRepository;
    private final UserRepository userRepository;

    /**
     * Выдаёт менеджеру доступ к аккаунту селлера (по email менеджера).
     */
    @Transactional
    public SellerManagerAccessDto grantAccess(User seller, String managerEmail) {
        validateSellerCanGrant(seller);

        String normalizedEmail = managerEmail.trim();
        User manager = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UserException(
                        "Пользователь с таким email не найден",
                        HttpStatus.NOT_FOUND
                ));

        if (manager.getRole() != Role.MANAGER) {
            throw new UserException(
                    "Доступ можно выдать только пользователю с ролью MANAGER",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (!Boolean.TRUE.equals(manager.getIsActive())) {
            throw new UserException(
                    "Нельзя выдать доступ неактивному менеджеру",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (seller.getId().equals(manager.getId())) {
            throw new UserException("Нельзя выдать доступ самому себе", HttpStatus.BAD_REQUEST);
        }

        SellerManagerAccess access = accessRepository
                .findBySeller_IdAndManager_Id(seller.getId(), manager.getId())
                .orElse(null);

        if (access != null && access.getStatus() == SellerManagerAccessStatus.ACTIVE) {
            throw new UserException("Этому менеджеру доступ уже выдан", HttpStatus.CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now();
        if (access == null) {
            access = SellerManagerAccess.builder()
                    .seller(seller)
                    .manager(manager)
                    .status(SellerManagerAccessStatus.ACTIVE)
                    .grantedAt(now)
                    .build();
        } else {
            access.setStatus(SellerManagerAccessStatus.ACTIVE);
            access.setRevokedAt(null);
            access.setGrantedAt(now);
        }

        access = accessRepository.save(access);
        return toDto(access);
    }

    /**
     * Отзывает доступ менеджера к аккаунту селлера.
     */
    @Transactional
    public void revokeAccess(User seller, Long managerId) {
        validateSellerCanGrant(seller);

        SellerManagerAccess access = accessRepository
                .findBySeller_IdAndManager_Id(seller.getId(), managerId)
                .orElseThrow(() -> new UserException(
                        "Доступ для этого менеджера не найден",
                        HttpStatus.NOT_FOUND
                ));

        if (access.getStatus() == SellerManagerAccessStatus.REVOKED) {
            throw new UserException("Доступ уже отозван", HttpStatus.BAD_REQUEST);
        }

        access.setStatus(SellerManagerAccessStatus.REVOKED);
        access.setRevokedAt(LocalDateTime.now());
        accessRepository.save(access);
    }

    /**
     * Список менеджеров с активным доступом к селлеру.
     */
    @Transactional(readOnly = true)
    public List<SellerManagerAccessDto> listManagersForSeller(Long sellerId) {
        return accessRepository.findBySeller_IdAndStatus(sellerId, SellerManagerAccessStatus.ACTIVE)
                .stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Email активных менеджеров по списку селлеров (для таблицы пользователей).
     */
    @Transactional(readOnly = true)
    public Map<Long, List<String>> findActiveManagerEmailsBySellerIds(Collection<Long> sellerIds) {
        if (sellerIds == null || sellerIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = accessRepository.findManagerEmailsBySellerIds(sellerIds, SellerManagerAccessStatus.ACTIVE);
        Map<Long, List<String>> result = new HashMap<>();
        for (Object[] row : rows) {
            Long sellerId = (Long) row[0];
            String email = (String) row[1];
            result.computeIfAbsent(sellerId, ignored -> new ArrayList<>()).add(email);
        }
        return result;
    }

    /**
     * Селлеры, выдавшие менеджеру активный доступ.
     */
    @Transactional(readOnly = true)
    public List<User> listActiveSellersForManager(Long managerId) {
        return accessRepository.findActiveSellersByManagerId(managerId, SellerManagerAccessStatus.ACTIVE)
                .stream()
                .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
                .toList();
    }

    /**
     * Может ли менеджер работать с аккаунтом селлера (активный grant).
     */
    @Transactional(readOnly = true)
    public boolean canManagerAccessSeller(Long managerId, Long sellerId) {
        return accessRepository.existsActiveAccessToActiveSeller(
                managerId, sellerId, SellerManagerAccessStatus.ACTIVE);
    }

    /**
     * Может ли менеджер работать с аккаунтом селлера.
     */
    @Transactional(readOnly = true)
    public boolean canManagerAccessSeller(User manager, User seller) {
        if (manager == null || seller == null || manager.getRole() != Role.MANAGER) {
            return false;
        }
        return canManagerAccessSeller(manager.getId(), seller.getId());
    }

    private void validateSellerCanGrant(User seller) {
        if (seller.getRole() != Role.SELLER) {
            throw new UserException("Только селлер может управлять доступом менеджеров", HttpStatus.FORBIDDEN);
        }
        if (!Boolean.TRUE.equals(seller.getEmailConfirmed())) {
            throw new UserException(
                    "Подтвердите почту, чтобы выдавать доступ менеджерам",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    private SellerManagerAccessDto toDto(SellerManagerAccess access) {
        return SellerManagerAccessDto.builder()
                .managerId(access.getManager().getId())
                .managerEmail(access.getManager().getEmail())
                .grantedAt(access.getGrantedAt())
                .build();
    }
}
