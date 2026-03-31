package ru.oparin.solution.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Конфигурация для асинхронного выполнения задач.
 * Ограничивает параллелизм для предотвращения перегрузки WB API.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Value("${app.executors.core-pool-size:4}")
    private int corePoolSize;
    @Value("${app.executors.max-pool-size:6}")
    private int maxPoolSize;
    @Value("${app.executors.task-queue-capacity:500}")
    private int taskQueueCapacity;
    @Value("${app.executors.cabinet-queue-capacity:100}")
    private int cabinetQueueCapacity;
    @Value("${app.executors.user-deletion-queue-capacity:50}")
    private int userDeletionQueueCapacity;

    /**
     * Пул потоков для асинхронных задач.
     * 
     * При переполнении очереди задачи выполняются в потоке планировщика (CallerRunsPolicy),
     * что замедляет добавление новых задач, но гарантирует их выполнение.
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(taskQueueCapacity);
        executor.setThreadNamePrefix("analytics-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy()); // Выполнение в текущем потоке при переполнении
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * Пул для параллельного обновления кабинетов по расписанию.
     */
    @Bean(name = "cabinetUpdateExecutor")
    public Executor cabinetUpdateExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(cabinetQueueCapacity);
        executor.setThreadNamePrefix("cabinet-update-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(300);
        executor.initialize();
        return executor;
    }

    /**
     * Пул для фонового удаления пользователей (кабинеты и запись пользователя в отдельных транзакциях).
     * Один поток, чтобы не перегружать БД при массовом удалении.
     */
    @Bean(name = "userDeletionExecutor")
    public Executor userDeletionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(userDeletionQueueCapacity);
        executor.setThreadNamePrefix("user-deletion-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(600);
        executor.initialize();
        return executor;
    }
}

