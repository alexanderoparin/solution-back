package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.oparin.solution.model.PaymentCabinet;

import java.util.List;

/**
 * Репозиторий кабинетов в оплате.
 */
public interface PaymentCabinetRepository extends JpaRepository<PaymentCabinet, PaymentCabinet.Pk> {

    /**
     * Кабинеты платежа.
     */
    List<PaymentCabinet> findByPaymentId(Long paymentId);
}
