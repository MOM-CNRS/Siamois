package fr.siamois.ui.bean.panel.models.panel.single;

import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class SpatialUnitPanel extends AbstractEntityPanel<SpatialUnitDTO> {

    private final transient SpatialUnitService spatialUnitService;
    private final transient ProfilePermissionService profilePermissionService;

    public SpatialUnitPanel(ApplicationContext context) {
        super("common.entity.spatialUnit", "bi bi-geo-alt", "siamois-panel spatial-unit-panel single-panel", "/resources/img/svg/geo-alt.svg", "spatial-unit", "place", context);
        this.spatialUnitService = context.getBean(SpatialUnitService.class);
        this.profilePermissionService = context.getBean(ProfilePermissionService.class);
    }

    @Override
    protected SpatialUnitDTO loadUnit(Long id) {
        return spatialUnitService.findById(id);
    }

    @Override
    protected String titleOf(SpatialUnitDTO unit) {
        return unit.getName();
    }

    @Override
    protected boolean canView(PersonDTO user, SpatialUnitDTO unit) {
        return profilePermissionService.canViewInstitutionData(user, unit.getCreatedByInstitution());
    }
}
