package fr.siamois.ui.bean.dialog.newunit.handler;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.exceptions.EntityAlreadyExistsException;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.dialog.newunit.UnitKind;
import fr.siamois.ui.exceptions.CannotInitializeNewUnitDialogException;
import fr.siamois.ui.lazydatamodel.RecordingUnitInActionUnitLazyDataModel;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class RecordingUnitHandler implements INewUnitHandler<RecordingUnit> {

    private final RecordingUnitService recordingUnitService;
    private final ActionUnitService actionUnitService;
    private final SessionSettingsBean sessionSettingsBean;

    public RecordingUnitHandler(RecordingUnitService recordingUnitService, ActionUnitService actionUnitService, SessionSettingsBean sessionSettingsBean) {
        this.recordingUnitService = recordingUnitService;
        this.actionUnitService = actionUnitService;
        this.sessionSettingsBean = sessionSettingsBean;
    }

    @Override
    public List<SpatialUnit> getSpatialUnitOptions(RecordingUnit unit) {
        ActionUnit actionUnit = unit.getActionUnit();
        // Return the spatial context of the parent action
        if (actionUnit != null) {
            return new ArrayList<>(actionUnit.getSpatialContext());
        }

        return List.of();
    }

    @Override public UnitKind kind() { return UnitKind.RECORDING; }
    @Override public RecordingUnit newEmpty() {
        return new RecordingUnit();
    }
    @Override public RecordingUnit save(UserInfo u, RecordingUnit unit) throws EntityAlreadyExistsException {
        return recordingUnitService.save(unit); }
    @Override public String dialogWidgetVar() { return "newUnitDiag"; }

    @Override public void initFromContext(GenericNewUnitDialogBean<?> bean) throws CannotInitializeNewUnitDialogException {
        RecordingUnit unit = (RecordingUnit) bean.getUnit();
        ActionUnit actionUnit ;
        if (bean.getLazyDataModel() instanceof RecordingUnitInActionUnitLazyDataModel typedModel) {
            actionUnit = actionUnitService.findById(typedModel.getActionUnit().getId());
        }
        else if(bean.getParent() instanceof ActionUnit) {
            actionUnit = (ActionUnit) bean.getParent();
        }
        else {
            throw new CannotInitializeNewUnitDialogException("Recording unit cannot be created without a context");
        }
        unit.setCreatedByInstitution(actionUnit.getCreatedByInstitution());
        unit.setActionUnit(actionUnit);
        unit.setAuthor(sessionSettingsBean.getAuthenticatedUser());
        unit.setContributors(List.of(sessionSettingsBean.getAuthenticatedUser()));
        unit.setOpeningDate(OffsetDateTime.now());
    }

    @Override
    public String getName(RecordingUnit unit) {
        return unit.getFullIdentifier();
    }


}
