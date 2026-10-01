package fr.siamois.ui.bean.panel.models.panel.list;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class RecordingUnitListPanel extends AbstractListPanel {

    public RecordingUnitListPanel(ApplicationContext context) {
        super("panel.title.allrecordingunit", "bi bi-pencil-square", "siamois-panel recording-unit-panel list-panel", "/resources/img/svg/pencil-square.svg", "recording-unit", "recordingUnit", context);
    }
}
