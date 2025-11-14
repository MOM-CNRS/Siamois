package fr.siamois.domain.events.publisher;

import fr.siamois.domain.models.events.LangageChangeEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Publisher for LangageChangeEvent.
 * This service is responsible for publishing events related to changes in the language settings of the application.
 */
@Service
@RequiredArgsConstructor
public class LangageChangeEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * Publishes a LangageChangeEvent.
     * This method is used to notify listeners that a language change has occurred.
     */
    public void publishInstitutionChangeEvent() {
        LangageChangeEvent event = new LangageChangeEvent(this);
        applicationEventPublisher.publishEvent(event);
    }

}
