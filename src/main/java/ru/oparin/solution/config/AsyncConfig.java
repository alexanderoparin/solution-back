package ru.oparin.solution.config;

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

    public static final int MIN_POOL_SIZE = 2;
    public static final int MAX_POOL_SIZE = 5;

    /**
     * Пул потоков для асинхронных задач.
     * 
     * При переполнении очереди задачи выполняются в потоке планировщика (CallerRunsPolicy),
     * что замедляет добавление новых задач, но гарантирует их выполнение.
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(MIN_POOL_SIZE); // Минимум потоков
        executor.setMaxPoolSize(MAX_POOL_SIZE); // Максимум потоков
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
     */
    @Bean(name = "cabinetUpdateExecutor")
    public Executor cabinetUpdateExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(MIN_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(100);
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
        executor.setCorePoolSize(MIN_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("user-deletion-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(600);
        executor.initialize();
        return executor;
    }
}

