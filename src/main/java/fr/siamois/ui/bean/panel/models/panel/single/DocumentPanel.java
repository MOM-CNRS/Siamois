package fr.siamois.ui.bean.panel.models.panel.single;

import fr.siamois.domain.services.document.DocumentService;
import fr.siamois.dto.entity.DocumentDTO;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class DocumentPanel extends AbstractEntityPanel<DocumentDTO> {

    private final transient DocumentService documentService;

    public DocumentPanel(ApplicationContext context) {
        super("common.entity.document", "bi bi-file-earmark-text", "siamois-panel document-panel single-panel",
                "/resources/img/svg/file-earmark-text.svg", "document", "document", context);
        this.documentService = context.getBean(DocumentService.class);
    }

    @Override
    protected DocumentDTO loadUnit(Long id) {
        return documentService.findDtoById(id);
    }

    @Override
    protected String titleOf(DocumentDTO unit) {
        return unit.getIdentifier();
    }

    // A panel is identified by its resource URI, which AbstractPanel already compares.
    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
