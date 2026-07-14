package ru.oparin.solution.model;

/**
 * Статус приглашения в кабинет.
 */
public enum CabinetAccessInvitationStatus {
    /** Ожидает принятия. */
    PENDING,
    /** Принято приглашённым. */
    ACCEPTED,
    /** Отозвано владельцем кабинета. */
    REVOKED,
    /** Отклонено приглашённым пользователем. */
    DECLINED,
    /** Срок ссылки истёк. */
    EXPIRED
}
