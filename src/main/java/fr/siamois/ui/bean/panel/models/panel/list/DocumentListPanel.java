package fr.siamois.ui.bean.panel.models.panel.list;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class DocumentListPanel extends AbstractListPanel {

    public DocumentListPanel(ApplicationContext context) {
        super("panel.title.alldocuments", "bi bi-file-earmark-text", "siamois-panel document-panel list-panel",
                "/resources/img/svg/file-earmark-text.svg", "document", "document", context);
    }
}
