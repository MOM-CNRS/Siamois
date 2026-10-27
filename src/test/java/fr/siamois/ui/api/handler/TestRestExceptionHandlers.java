package fr.siamois.ui.api.handler;

import fr.siamois.domain.services.LangService;
import org.springframework.context.support.ResourceBundleMessageSource;

/** A {@link RestExceptionHandler} reading the application's real message bundles, for the controller tests. */
public final class TestRestExceptionHandlers {

    private TestRestExceptionHandlers() {
    }

    public static RestExceptionHandler create() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("language/messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        return new RestExceptionHandler(new LangService(messageSource));
    }
}
