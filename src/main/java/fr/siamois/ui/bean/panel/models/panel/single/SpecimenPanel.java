package fr.siamois.ui.bean.panel.models.panel.single;

import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.dto.entity.SpecimenDTO;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class SpecimenPanel extends AbstractEntityPanel<SpecimenDTO> {

    private final transient SpecimenService specimenService;

    public SpecimenPanel(ApplicationContext context) {
        super("common.entity.specimen", "bi bi-bucket", "siamois-panel specimen-panel single-panel", "/resources/img/svg/bucket.svg", "specimen", "find", context);
        this.specimenService = context.getBean(SpecimenService.class);
    }

    @Override
    protected SpecimenDTO loadUnit(Long id) {
        return specimenService.findById(id);
    }

    @Override
    protected String titleOf(SpecimenDTO unit) {
        return unit.getFullIdentifier();
    }
}
