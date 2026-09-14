package fr.siamois.domain.events.publisher;

import fr.siamois.domain.models.events.FormConfigChangeEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FormConfigChangeEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * Publishes a FormConfigChangeEvent.
     */
    public void publishEvent() {
        applicationEventPublisher.publishEvent(new FormConfigChangeEvent(this));
    }

}
