package fr.siamois.domain.events.publisher;

import fr.siamois.domain.events.ConceptChangeEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConceptChangeEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public void publishEvent(String fieldCode) {
        ConceptChangeEvent event = new ConceptChangeEvent(this, fieldCode);
        applicationEventPublisher.publishEvent(event);

    }

}
