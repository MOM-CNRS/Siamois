package fr.siamois.ui.config;

import fr.siamois.utils.RenderCallStats;
import jakarta.faces.event.PhaseEvent;
import jakarta.faces.event.PhaseId;
import jakarta.faces.event.PhaseListener;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Logs how long each JSF lifecycle phase takes for the current request, to tell apart server-side time
 * spent in application code (our beans, already instrumented individually) from time spent in the JSF
 * machinery itself — building/encoding the component tree, which no amount of bean-level timing can see.
 * Temporary diagnostic added while investigating a "single unit panel" load that measured 627-800ms
 * server-side despite each of our own methods accounting for well under 100ms of it.
 */
@Slf4j
public class TimingPhaseListener implements PhaseListener {

    private static final ThreadLocal<Map<PhaseId, Instant>> STARTS =
            ThreadLocal.withInitial(HashMap::new);

    @Override
    public void beforePhase(PhaseEvent event) {
        STARTS.get().put(event.getPhaseId(), Instant.now());
    }

    @Override
    public void afterPhase(PhaseEvent event) {
        Instant start = STARTS.get().remove(event.getPhaseId());
        if (start != null) {
            log.debug("Temps d'exécution de la phase JSF {} : {} ms",
                    event.getPhaseId(), Instant.now().toEpochMilli() - start.toEpochMilli());
        }
        if (event.getPhaseId() == PhaseId.RENDER_RESPONSE) {
            RenderCallStats.dumpAndClear();
            STARTS.remove();
        }
    }

    @Override
    public PhaseId getPhaseId() {
        return PhaseId.ANY_PHASE;
    }
}
