package ru.oparin.solution.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "subscription")
public class SubscriptionProperties {

    /**
     * Включена ли глобальная оплата сайта. При false весь сайт бесплатен.
     */
    private boolean billingEnabled = true;

    /**
     * Включена ли платная подписка на раздел «Управление РК».
     */
    private boolean campaignManagementEnabled = false;

    /**
     * Длительность триала в днях для самостоятельных селлеров (legacy, при глобальной оплате).
     */
    private int trialDays = 14;
}

