package fr.siamois.ui.bean.panel.models.panel.list;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ActionUnitListPanel extends AbstractListPanel {

    public ActionUnitListPanel(ApplicationContext context) {
        super("panel.title.allactionunit", "bi bi-arrow-down-square", "siamois-panel action-unit-panel list-panel", "/resources/img/svg/arrow-down-square.svg", "action-unit", "project", context);
    }
}
