package fr.siamois.ui.config;

import fr.siamois.ui.bean.NavBean;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.PhaseEvent;
import jakarta.faces.event.PhaseId;
import jakarta.faces.event.PhaseListener;
import jakarta.servlet.ServletContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

public class ApplicationModePhaseListener implements PhaseListener {

    @Override
    public PhaseId getPhaseId() {
        return PhaseId.RESTORE_VIEW;
    }

    @Override
    public void beforePhase(PhaseEvent event) {}

    @Override
    public void afterPhase(PhaseEvent event) {
        FacesContext ctx = event.getFacesContext();
        if (ctx.getViewRoot() == null) return;
        String viewId = ctx.getViewRoot().getViewId();
        if (viewId == null || !viewId.contains("/settings")) return;

        NavBean navBean = WebApplicationContextUtils
                .getRequiredWebApplicationContext((ServletContext) ctx.getExternalContext().getContext())
                .getBean(NavBean.class);
        navBean.setApplicationMode(NavBean.ApplicationMode.SETTINGS);
    }
}
