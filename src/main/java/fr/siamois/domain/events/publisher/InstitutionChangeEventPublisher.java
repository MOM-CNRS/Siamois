package fr.siamois.domain.events.publisher;

import fr.siamois.domain.models.events.InstitutionChangeEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Publisher for InstitutionChangeEvent.
 * This service is responsible for publishing events related to changes related to institutions loading.
 */
@Service
@RequiredArgsConstructor
public class InstitutionChangeEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * Publishes an InstitutionChangeEvent.
     * This method is used to notify listeners that an institution change has occurred.
     */
    public void publishInstitutionChangeEvent() {
        InstitutionChangeEvent event = new InstitutionChangeEvent(this);
        applicationEventPublisher.publishEvent(event);
    }

}
