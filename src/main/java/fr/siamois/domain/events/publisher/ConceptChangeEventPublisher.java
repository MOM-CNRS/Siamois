package fr.siamois.domain.events.publisher;

import fr.siamois.domain.models.events.ConceptChangeEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConceptChangeEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * Publishes a ConceptChangeEvent.
     *
     * @param fieldCode The field code related to the concept change.
     */
    public void publishEvent(@NonNull String fieldCode) {
        ConceptChangeEvent event = new ConceptChangeEvent(this, fieldCode);
        applicationEventPublisher.publishEvent(event);
    }

}
