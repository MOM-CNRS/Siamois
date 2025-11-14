package fr.siamois.domain.events.publisher;

import fr.siamois.domain.models.events.LoginEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Publisher for LoginEvent.
 * This service is responsible for publishing events related to user login actions.
 */
@Service
@RequiredArgsConstructor
public class LoginEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * Publishes a LoginEvent.
     * This method is used to notify listeners that a user has logged in. It's used to trigger bean's reset method annotated with @EventListener
     */
    public void publishLoginEvent() {
        LoginEvent event = new LoginEvent(this);
        applicationEventPublisher.publishEvent(event);
    }

}
