package fr.siamois.domain.events;

import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.infrastructure.database.initializer.DatabaseInitializer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.util.List;

/**
 * ApplicationReadyListener is a Spring configuration class that listens for the ApplicationReadyEvent.
 * It initializes the database using the provided DatabaseInitializer instances when the application is ready.
 * If any initialization fails, it logs the error and exits the application with a non-zero status.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ApplicationReadyListener {

    private final List<DatabaseInitializer> databaseInitializer;

    /**
     * onApplicationReady is triggered when the application is fully started.
     * It calls every DatabaseInitializer's initialize method to set up the database.
     * If an exception occurs during initialization, it logs the error and exits the application.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        for (DatabaseInitializer initializer : databaseInitializer) {
            try {
                initializer.initialize();
            } catch (DatabaseDataInitException e) {
                log.error(e.getMessage(), e);
                System.exit(1);
            }
        }
    }

}
