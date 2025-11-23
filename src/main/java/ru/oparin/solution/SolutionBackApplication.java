package ru.oparin.solution;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Главный класс Spring Boot приложения.
 */
@SpringBootApplication
@EnableScheduling
@EnableJpaAuditing
public class SolutionBackApplication {

    /**
     * Точка входа в приложение.
     *
     * @param args аргументы командной строки
     */
    public static void main(String[] args) {
        SpringApplication.run(SolutionBackApplication.class, args);
    }
}

