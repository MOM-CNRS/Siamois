package fr.siamois.ui.bean.panel.models.panel.single;

import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class RecordingUnitPanel extends AbstractEntityPanel<RecordingUnitDTO> {

    private final transient RecordingUnitService recordingUnitService;
    private final transient ProfilePermissionService profilePermissionService;

    public RecordingUnitPanel(ApplicationContext context) {
        super("common.entity.recordingunit", "bi bi-pencil-square", "siamois-panel recording-unit-panel single-panel", "/resources/img/svg/pencil-square.svg", "recording-unit", "recordingUnit", context);
        this.recordingUnitService = context.getBean(RecordingUnitService.class);
        this.profilePermissionService = context.getBean(ProfilePermissionService.class);
    }

    @Override
    protected RecordingUnitDTO loadUnit(Long id) {
        return recordingUnitService.findById(id);
    }

    @Override
    protected String titleOf(RecordingUnitDTO unit) {
        return unit.getFullIdentifier();
    }

    @Override
    protected boolean canView(PersonDTO user, RecordingUnitDTO unit) {
        return profilePermissionService.canViewRecordingUnit(user, unit);
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
