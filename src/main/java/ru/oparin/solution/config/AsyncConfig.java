package ru.oparin.solution.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Конфигурация для асинхронного выполнения задач.
 * Ограничивает параллелизм для предотвращения перегрузки WB API.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Пул потоков для асинхронных задач.
     * Максимум 5 параллельных задач для предотвращения превышения лимитов WB API.
     * 
     * При переполнении очереди задачи выполняются в потоке планировщика (CallerRunsPolicy),
     * что замедляет добавление новых задач, но гарантирует их выполнение.
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1); // Минимум 1 потока
        executor.setMaxPoolSize(2); // Максимум 2 потоков
        executor.setQueueCapacity(500); // Очередь на 500 задач (для поддержки большого количества селлеров)
        executor.setThreadNamePrefix("analytics-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy()); // Выполнение в текущем потоке при переполнении
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * Пул для параллельного обновления кабинетов по расписанию.
     * До 4 кабинетов одновременно (с учётом лимитов WB API).
     */
    @Bean(name = "cabinetUpdateExecutor")
    public Executor cabinetUpdateExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("cabinet-update-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(300);
        executor.initialize();
        return executor;
    }
}

