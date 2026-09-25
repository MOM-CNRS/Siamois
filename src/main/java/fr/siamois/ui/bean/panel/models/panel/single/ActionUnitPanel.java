package fr.siamois.ui.bean.panel.models.panel.single;

import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.PersonDTO;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ActionUnitPanel extends AbstractEntityPanel<ActionUnitDTO> {

    private final transient ActionUnitService actionUnitService;
    private final transient ProfilePermissionService profilePermissionService;

    public ActionUnitPanel(ApplicationContext context) {
        super("common.entity.actionUnit", "bi bi-arrow-down-square", "siamois-panel action-unit-panel single-panel", "/resources/img/svg/arrow-down-square.svg", "action-unit", "project", context);
        this.actionUnitService = context.getBean(ActionUnitService.class);
        this.profilePermissionService = context.getBean(ProfilePermissionService.class);
    }

    @Override
    protected ActionUnitDTO loadUnit(Long id) {
        return actionUnitService.findById(id);
    }

    @Override
    protected String titleOf(ActionUnitDTO unit) {
        return unit.getName();
    }

    @Override
    protected boolean canView(PersonDTO user, ActionUnitDTO unit) {
        return profilePermissionService.canViewProject(user, unit.getCreatedByInstitution(), unit.getId());
    }
}
