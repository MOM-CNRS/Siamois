package fr.siamois.domain.events;

import org.springframework.context.ApplicationEvent;

public class ConceptChangeEvent extends ApplicationEvent {
    public ConceptChangeEvent(Object source) {
        super(source);
    }
}
