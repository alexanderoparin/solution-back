package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.model.SellerWorker;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.SellerWorkerRepository;

import java.util.List;
import java.util.Optional;

/**
 * Привязка работников (WORKER) к селлерам.
 */
@Service
@RequiredArgsConstructor
public class SellerWorkerService {

    private final SellerWorkerRepository sellerWorkerRepository;

    /**
     * Создаёт привязку работника к селлеру (при создании WORKER селлером).
     */
    @Transactional
    public void linkWorkerToSeller(User seller, User worker) {
        if (seller.getRole() != Role.SELLER) {
            throw new UserException("Работника может привязать только селлер", HttpStatus.BAD_REQUEST);
        }
        if (worker.getRole() != Role.WORKER) {
            throw new UserException("Привязка только для роли WORKER", HttpStatus.BAD_REQUEST);
        }
        if (sellerWorkerRepository.findByWorker_Id(worker.getId()).isPresent()) {
            throw new UserException("Работник уже привязан к селлеру", HttpStatus.CONFLICT);
        }
        sellerWorkerRepository.save(SellerWorker.builder()
                .seller(seller)
                .worker(worker)
                .build());
    }

    @Transactional(readOnly = true)
    public Optional<User> findSellerByWorkerId(Long workerId) {
        return sellerWorkerRepository.findSellerByWorkerId(workerId);
    }

    @Transactional(readOnly = true)
    public Optional<Long> findSellerIdByWorkerId(Long workerId) {
        return findSellerByWorkerId(workerId).map(User::getId);
    }

    @Transactional(readOnly = true)
    public boolean isWorkerOfSeller(Long workerId, Long sellerId) {
        return sellerWorkerRepository.existsByWorkerIdAndSellerId(workerId, sellerId);
    }

    @Transactional(readOnly = true)
    public boolean isWorkerOfSeller(User worker, User seller) {
        if (worker == null || seller == null || worker.getRole() != Role.WORKER || seller.getRole() != Role.SELLER) {
            return false;
        }
        return isWorkerOfSeller(worker.getId(), seller.getId());
    }

    @Transactional(readOnly = true)
    public List<User> findWorkersBySellerId(Long sellerId) {
        return sellerWorkerRepository.findWorkersBySellerId(sellerId);
    }

    @Transactional(readOnly = true)
    public String findSellerEmailForWorker(User worker) {
        if (worker.getRole() != Role.WORKER) {
            return null;
        }
        return findSellerByWorkerId(worker.getId())
                .map(User::getEmail)
                .orElse(null);
    }
}
