package ru.oparin.solution.model;

/**
 * Номинальный тип аккаунта (статистика и отображение, не права доступа).
 */
public enum AccountType {
    /** Продавец Wildberries */
    SELLER,
    /** Агентство / менеджер */
    AGENCY,
    /** Сотрудник продавца или агентства */
    EMPLOYEE
}
