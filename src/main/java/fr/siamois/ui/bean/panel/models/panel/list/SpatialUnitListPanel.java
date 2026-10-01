package fr.siamois.ui.bean.panel.models.panel.list;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class SpatialUnitListPanel extends AbstractListPanel {

    public SpatialUnitListPanel(ApplicationContext context) {
        super("panel.title.allspatialunit", "bi bi-geo-alt", "siamois-panel spatial-unit-panel list-panel", "/resources/img/svg/geo-alt.svg", "spatial-unit", "place", context);
    }
}
