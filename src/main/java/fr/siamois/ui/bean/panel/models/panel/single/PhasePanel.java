package fr.siamois.ui.bean.panel.models.panel.single;

import fr.siamois.domain.services.PhaseService;
import fr.siamois.dto.entity.PhaseDTO;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class PhasePanel extends AbstractEntityPanel<PhaseDTO> {

    private final transient PhaseService phaseService;

    public PhasePanel(ApplicationContext context) {
        super("common.entity.phase", "bi bi-layers", "siamois-panel phase-panel single-panel", "/resources/img/svg/layers.svg", "phase", "phase", context);
        this.phaseService = context.getBean(PhaseService.class);
    }

    @Override
    protected PhaseDTO loadUnit(Long id) {
        return phaseService.findById(id);
    }

    @Override
    protected String titleOf(PhaseDTO unit) {
        return unit.getIdentifier();
    }
}
