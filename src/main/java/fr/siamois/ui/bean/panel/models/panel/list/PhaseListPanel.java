package fr.siamois.ui.bean.panel.models.panel.list;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class PhaseListPanel extends AbstractListPanel {

    public PhaseListPanel(ApplicationContext context) {
        super("panel.title.allphases", "bi bi-layers", "siamois-panel phase-panel list-panel", "/resources/img/svg/layers.svg", "phase", "phase", context);
    }
}
