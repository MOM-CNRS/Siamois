package fr.siamois.ui.config;

import fr.siamois.infrastructure.config.ApplicationContextProvider;
import fr.siamois.ui.bean.panel.FlowBean;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.PhaseEvent;
import jakarta.faces.event.PhaseId;
import jakarta.faces.event.PhaseListener;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Logs duration of each JSF lifecycle phase. Enable with DEBUG on this logger (dev2 profile).
 */
@Slf4j
public class TimingPhaseListener implements PhaseListener {

    private static final ThreadLocal<Map<PhaseId, Instant>> STARTS =
            ThreadLocal.withInitial(HashMap::new);
    private static final ThreadLocal<Instant> REQUEST_START = new ThreadLocal<>();

    @Override
    public void beforePhase(PhaseEvent event) {
        if (event.getPhaseId() == PhaseId.RESTORE_VIEW) {
            REQUEST_START.set(Instant.now());
        }
        STARTS.get().put(event.getPhaseId(), Instant.now());
    }

    @Override
    public void afterPhase(PhaseEvent event) {
        Instant start = STARTS.get().remove(event.getPhaseId());
        if (start != null && log.isDebugEnabled()) {
            log.debug("Temps d'exécution de la phase JSF {} : {} ms",
                    event.getPhaseId(), Instant.now().toEpochMilli() - start.toEpochMilli());
        }
        if (event.getPhaseId() == PhaseId.RENDER_RESPONSE) {
            dumpRequestSummary();
            STARTS.remove();
            REQUEST_START.remove();
        }
    }

    private void dumpRequestSummary() {
        if (!log.isDebugEnabled()) {
            return;
        }
        Instant requestStart = REQUEST_START.get();
        long totalMs = requestStart == null
                ? -1
                : Instant.now().toEpochMilli() - requestStart.toEpochMilli();

        FacesContext faces = FacesContext.getCurrentInstance();
        String viewId = faces != null && faces.getViewRoot() != null
                ? faces.getViewRoot().getViewId()
                : "?";
        boolean ajax = faces != null && faces.getPartialViewContext() != null
                && faces.getPartialViewContext().isAjaxRequest();

        int panelCount = -1;
        int expandedBodies = -1;
        try {
            FlowBean flowBean = ApplicationContextProvider.getBean(FlowBean.class);
            if (flowBean != null && flowBean.getPanels() != null) {
                panelCount = flowBean.getPanels().size();
                expandedBodies = 0;
                for (int i = 0; i < panelCount; i++) {
                    if (flowBean.shouldIncludePanelBody(i)) {
                        expandedBodies++;
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // outside flow page
        }

        log.debug(
                "⏱ JSF request summary : total={} ms | viewId={} | ajax={} | panels={} | bodies={} ",
                totalMs, viewId, ajax,
                panelCount >= 0 ? panelCount : "n/a",
                expandedBodies >= 0 ? expandedBodies : "n/a");
    }

    @Override
    public PhaseId getPhaseId() {
        return PhaseId.ANY_PHASE;
    }
}
