package fr.siamois.ui.bean.dialog.newunit.handler;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.EntityAlreadyExistsException;
import fr.siamois.domain.services.ContainerService;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.dto.entity.SpatialUnitSummaryDTO;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.dialog.newunit.NewUnitContext;
import fr.siamois.ui.bean.dialog.newunit.UnitKind;
import fr.siamois.ui.exceptions.CannotInitializeNewUnitDialogException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ContainerHandler implements INewUnitHandler<ContainerDTO> {

    private final ContainerService containerService;
    private final SessionSettingsBean sessionSettingsBean;

    public ContainerHandler(ContainerService containerService, SessionSettingsBean sessionSettingsBean) {
        this.containerService = containerService;
        this.sessionSettingsBean = sessionSettingsBean;
    }

    @Override
    public UnitKind kind() {
        return UnitKind.CONTAINER;
    }

    @Override
    public ContainerDTO newEmpty() {
        return new ContainerDTO();
    }

    @Override
    public ContainerDTO save(UserInfo user, ContainerDTO unit) throws EntityAlreadyExistsException {
        return containerService.save(unit);
    }

    @Override
    public String dialogWidgetVar() {
        return "newUnitDiag";
    }

    @Override
    public void initFromContext(GenericNewUnitDialogBean<?> bean) throws CannotInitializeNewUnitDialogException {
        ContainerDTO unit = (ContainerDTO) bean.getUnit();
        NewUnitContext ctx = bean.getNewUnitContext();
        if (ctx == null) {
            return;
        }

        unit.setCreatedBy(sessionSettingsBean.getAuthenticatedUser());
        unit.setCreatedByInstitution(sessionSettingsBean.getSelectedInstitution());
    }

    @Override
    public String getName(ContainerDTO unit) {
        return unit.getIdentifier() != null ? unit.getIdentifier() : "";
    }

    @Override
    public List<SpatialUnitSummaryDTO> getSpatialUnitOptions(ContainerDTO unit) {
        return List.of();
    }
}