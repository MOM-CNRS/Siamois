package fr.siamois.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * When this annotation is applied to a class that implements {@link fr.siamois.infrastructure.database.initializer.DatabaseInitializer}, that initialization class is ignored
 * by {@link fr.siamois.domain.events.ApplicationReadyListener} when the application starts.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface IgnoreInitializer {
}
