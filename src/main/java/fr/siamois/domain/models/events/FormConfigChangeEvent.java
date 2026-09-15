package fr.siamois.domain.models.events;

import org.springframework.context.ApplicationEvent;

/**
 * Published whenever a {@code FormConfig} or {@code FieldFormConfig} row is created or modified,
 * so consumers caching resolved form configuration (see
 * {@code TableFieldConfigServiceImpl#findFormConfig}) know to drop what they hold rather than keep
 * serving a stale answer.
 */
public class FormConfigChangeEvent extends ApplicationEvent {

    public FormConfigChangeEvent(Object source) {
        super(source);
    }
}
