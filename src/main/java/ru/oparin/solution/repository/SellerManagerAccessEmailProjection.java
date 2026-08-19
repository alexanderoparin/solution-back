package ru.oparin.solution.repository;

/**
 * Пара sellerId/email менеджера для batch-загрузки списка менеджеров.
 */
public interface SellerManagerAccessEmailProjection {

    /**
     * ID селлера.
     */
    Long getSellerId();

    /**
     * Email менеджера.
     */
    String getManagerEmail();
}
