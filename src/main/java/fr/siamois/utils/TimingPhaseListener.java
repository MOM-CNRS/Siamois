package fr.siamois.utils;

import jakarta.faces.event.PhaseEvent;
import jakarta.faces.event.PhaseId;
import jakarta.faces.event.PhaseListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TEMPORAIRE (diagnostic perf) : chronomètre chaque phase du cycle Faces et journalise sa durée.
 * Permet de partitionner le temps d'une requête (RESTORE_VIEW / INVOKE_APPLICATION / RENDER_RESPONSE)
 * pour cibler le goulot du chargement/navigation des fiches. À retirer une fois le diagnostic terminé.
 */
public class TimingPhaseListener implements PhaseListener {

    private static final transient Logger log = LoggerFactory.getLogger(TimingPhaseListener.class);
    private static final String KEY = "phaseStartNs_";

    @Override
    public PhaseId getPhaseId() {
        return PhaseId.ANY_PHASE;
    }

    @Override
    public void beforePhase(PhaseEvent event) {
        event.getFacesContext().getAttributes()
                .put(KEY + event.getPhaseId(), System.nanoTime());
    }

    @Override
    public void afterPhase(PhaseEvent event) {
        Object start = event.getFacesContext().getAttributes().get(KEY + event.getPhaseId());
        if (start instanceof Long startNs) {
            long ms = (System.nanoTime() - startNs) / 1_000_000;
            if (ms >= 5) {
                log.debug("⏱ phase {} = {} ms", event.getPhaseId(), ms);
            }
        }
    }
}
