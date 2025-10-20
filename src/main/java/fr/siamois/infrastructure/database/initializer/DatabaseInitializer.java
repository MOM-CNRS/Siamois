package fr.siamois.infrastructure.database.initializer;


import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;

import java.lang.reflect.InvocationTargetException;

/**
 * Database initializer are services that are executed after the application startup.
 * They should consider having an {@link org.springframework.core.annotation.Order} annotation to specify when to execute.
 */
public interface DatabaseInitializer {
    /**
     * Initialize the data mandatory for application's logic.
     */
    void initialize() throws DatabaseDataInitException;

}
