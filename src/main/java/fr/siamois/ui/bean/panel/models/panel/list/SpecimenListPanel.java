package fr.siamois.ui.bean.panel.models.panel.list;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class SpecimenListPanel extends AbstractListPanel {

    public SpecimenListPanel(ApplicationContext context) {
        super("panel.title.allspecimenunit", "bi bi-bucket", "siamois-panel specimen-panel list-panel", "/resources/img/svg/bucket.svg", "specimen", "find", context);
    }
}
