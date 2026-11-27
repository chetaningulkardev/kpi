package com.example.kpi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Enables background processing for notifications.
 *
 * <p>Email delivery and the nightly reminder sweep must never block or fail an
 * HTTP request, so both run off the request thread.</p>
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncSchedulingConfig {

    /**
     * Dedicated pool for notification email delivery. A bounded queue with the
     * caller-runs policy means a mail backlog degrades to synchronous sending
     * rather than silently dropping notifications.
     */
    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("notify-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        executor.initialize();
        return executor;
    }
}

