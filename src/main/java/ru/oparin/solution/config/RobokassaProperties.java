package ru.oparin.solution.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "robokassa")
public class RobokassaProperties {

    private String merchantLogin;
    private String password1;
    private String password2;
    private boolean test = true;
    private String successUrl;
    private String failUrl;
    private String resultUrl;
}
