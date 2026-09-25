package fr.siamois.ui.bean.panel.models.panel.single;

import fr.siamois.domain.services.ContainerService;
import fr.siamois.dto.entity.ContainerDTO;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ContainerPanel extends AbstractEntityPanel<ContainerDTO> {

    private final transient ContainerService containerService;

    public ContainerPanel(ApplicationContext context) {
        super("common.entity.container", "bi bi-box-seam", "siamois-panel container-panel single-panel", "/resources/img/svg/box-seam.svg", "container", "container", context);
        this.containerService = context.getBean(ContainerService.class);
    }

    @Override
    protected ContainerDTO loadUnit(Long id) {
        return containerService.findById(id);
    }

    @Override
    protected String titleOf(ContainerDTO unit) {
        return unit.getIdentifier();
    }
}
