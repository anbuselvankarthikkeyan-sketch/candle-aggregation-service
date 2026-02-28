package com.candle.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Application-wide Spring configuration.
 */
@Configuration
@EnableScheduling
public class AppConfig {

    /**
     * Dedicated task scheduler for scheduled methods ({@code @Scheduled}).
     *
     * <p>Configured with enough threads to run both the generator and the flush
     * scheduler concurrently without blocking.
     */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("candle-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(5);
        return scheduler;
    }
}
