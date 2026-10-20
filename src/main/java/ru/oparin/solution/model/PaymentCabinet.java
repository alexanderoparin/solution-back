package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

/**
 * Кабинет, включённый в оплату (корзина multi-cabinet).
 */
@Entity
@Table(name = "payment_cabinets", schema = "solution")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(PaymentCabinet.Pk.class)
public class PaymentCabinet {

    /**
     * Идентификатор платежа.
     */
    @Id
    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    /**
     * Идентификатор кабинета.
     */
    @Id
    @Column(name = "cabinet_id", nullable = false)
    private Long cabinetId;

    /**
     * Платёж.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, insertable = false, updatable = false)
    private Payment payment;

    /**
     * Кабинет.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cabinet_id", nullable = false, insertable = false, updatable = false)
    private Cabinet cabinet;

    /**
     * Составной ключ.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        private Long paymentId;
        private Long cabinetId;
    }
}
