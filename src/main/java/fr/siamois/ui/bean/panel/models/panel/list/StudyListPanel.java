package fr.siamois.ui.bean.panel.models.panel.list;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class StudyListPanel extends AbstractListPanel {

    public StudyListPanel(ApplicationContext context) {
        super("panel.title.allstudies", "bi bi-clipboard-data", "siamois-panel study-panel list-panel",
                "/resources/img/svg/clipboard-data.svg", "study", "study", context);
    }
}
