package fr.siamois.ui.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean
    public ThreadPoolTaskExecutor importTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("import-");
        executor.initialize();
        return executor;
    }

    @Bean
    public ThreadPoolTaskExecutor feedbackTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("feedback-");
        executor.initialize();
        return executor;
    }

    /**
     * The bulk actions on a list's selection (BulkAsyncRunner): each runs in the background while the
     * client polls its progress. The caller's security context goes along with the task.
     *
     * @return the executor of bulk actions
     */
    @Bean
    public ThreadPoolTaskExecutor bulkTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("bulk-");
        executor.setTaskDecorator(DelegatingSecurityContextRunnable::new);
        executor.initialize();
        return executor;
    }
}
