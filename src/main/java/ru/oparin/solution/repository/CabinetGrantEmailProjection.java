package ru.oparin.solution.repository;

/**
 * Проекция: email пользователя с доступом к кабинету.
 */
public interface CabinetGrantEmailProjection {

    Long getCabinetId();

    String getGranteeEmail();
}
