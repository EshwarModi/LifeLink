package com.lifelink.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Asynchronous execution configuration for non-blocking background notifications.
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Dedicated bounded thread pool for dispatching email notifications.
     *
     * <ul>
     *   <li>Core pool size: 2</li>
     *   <li>Max pool size: 4</li>
     *   <li>Queue capacity: 100</li>
     *   <li>Thread name prefix: {@code "mail-"}</li>
     *   <li>Rejection policy: logs a warning and drops the notification rather than blocking the HTTP thread.</li>
     * </ul>
     */
    @Bean(name = {"mailTaskExecutor", "taskExecutor"})
    public ThreadPoolTaskExecutor mailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mail-");
        executor.setRejectedExecutionHandler((runnable, pool) ->
                log.warn("Dropping mail notification task because mailTaskExecutor queue is full " +
                         "(active={}, poolSize={}, queueSize={})",
                        pool.getActiveCount(), pool.getPoolSize(), pool.getQueue().size())
        );
        executor.initialize();
        return executor;
    }
}
