package fr.siamois.domain.models.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

public class ConceptChangeEvent extends ApplicationEvent {

    @Getter
    private final String fieldCode;

    public ConceptChangeEvent(Object source, String fieldCode) {
        super(source);
        this.fieldCode = fieldCode;
    }
}
