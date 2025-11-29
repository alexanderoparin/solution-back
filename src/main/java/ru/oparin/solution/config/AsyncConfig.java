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
}

