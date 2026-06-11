package com.example.agent.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Beans backing the self-scheduling feature. Only active when {@code agent.scheduler.enabled} is
 * true (the default). Provides the {@link TaskScheduler} that fires scheduled prompts and the
 * default file-backed {@link ScheduledPromptStore} when no other implementation is supplied.
 */
@Configuration
@ConditionalOnProperty(name = "agent.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulerConfig {

    /** Dedicated pool so a long-running scheduled agent run never blocks trigger evaluation. */
    @Bean(name = "scheduledPromptScheduler")
    @ConditionalOnMissingBean(name = "scheduledPromptScheduler")
    public TaskScheduler scheduledPromptScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("scheduled-prompt-");
        scheduler.setDaemon(true);
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.initialize();
        return scheduler;
    }

    @Bean
    @ConditionalOnMissingBean(ScheduledPromptStore.class)
    public ScheduledPromptStore scheduledPromptStore(ObjectMapper objectMapper,
                                                     SchedulerProperties properties) {
        return new JsonFileScheduledPromptStore(objectMapper, properties.storeFile());
    }
}
