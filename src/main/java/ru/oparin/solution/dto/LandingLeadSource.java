package ru.oparin.solution.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * Источник заявки с лендинга — кнопка, с которой пользователь открыл форму.
 */
@Getter
@RequiredArgsConstructor
public enum LandingLeadSource {

    HERO_CONSULTATION("hero-consultation", "Заказать консультацию (главный экран)"),
    SERVICES_AGENCY("services-agency", "Заказать услугу (раздел «Наши сервисы»)"),
    PRICING_AGENCY("pricing-agency", "Хочу консультацию (раздел «Тарифы»)"),
    PRICING_AUDIT("pricing-audit", "Записаться на аудит (раздел «Тарифы»)"),
    LANDING_SERVICE_PRICING("landing-service-pricing", "Консультация (сервисный лендинг, тарифы)"),
    LANDING_SERVICE_MANAGE("landing-service-manage", "Ведение кабинета (сервисный лендинг)"),
    LANDING_SERVICE_AUDIT("landing-service-audit", "Аудит кабинета (сервисный лендинг)");

    @JsonValue
    private final String code;

    private final String label;

    @JsonCreator
    public static LandingLeadSource fromCode(String code) {
        return Arrays.stream(values())
                .filter(source -> source.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Неизвестный источник заявки: " + code));
    }
}
