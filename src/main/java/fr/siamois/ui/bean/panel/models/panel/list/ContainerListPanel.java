package fr.siamois.ui.bean.panel.models.panel.list;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ContainerListPanel extends AbstractListPanel {

    public ContainerListPanel(ApplicationContext context) {
        super("panel.title.allcontainers", "bi bi-box-seam", "siamois-panel container-panel list-panel", "/resources/img/svg/box-seam.svg", "container", "container", context);
    }
}
