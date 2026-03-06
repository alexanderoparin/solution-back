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
     * Включена ли оплата (тарифы, Робокасса). При false селлеры получают бесплатный доступ без триала.
     */
    private boolean billingEnabled = true;

    /**
     * Длительность триала в днях для самостоятельных селлеров.
     */
    private int trialDays = 14;
}

