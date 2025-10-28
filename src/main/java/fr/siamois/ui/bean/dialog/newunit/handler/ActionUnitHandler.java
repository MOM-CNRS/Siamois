package fr.siamois.ui.bean.dialog.newunit.handler;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.exceptions.EntityAlreadyExistsException;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.dialog.newunit.UnitKind;
import fr.siamois.ui.exceptions.CannotInitializeNewUnitDialogException;
import fr.siamois.ui.lazydatamodel.ActionUnitInSpatialUnitLazyDataModel;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class ActionUnitHandler implements INewUnitHandler<ActionUnit> {

    private final ActionUnitService actionUnitService;

    public ActionUnitHandler(ActionUnitService actionUnitService) {
        this.actionUnitService = actionUnitService;
    }

    @Override
    public UnitKind kind() {
        return UnitKind.ACTION;
    }

    @Override
    public ActionUnit newEmpty() {
        return new ActionUnit();
    }

    @Override
    public ActionUnit save(UserInfo u, ActionUnit unit) throws EntityAlreadyExistsException {
        return actionUnitService.save(u, unit, unit.getType());
    }

    @Override
    public String dialogWidgetVar() {
        return "newUnitDiag";
    }

    @Override
    public void initFromContext(GenericNewUnitDialogBean<?> bean) throws CannotInitializeNewUnitDialogException {
        ActionUnit unit = (ActionUnit) bean.getUnit();
        SpatialUnit spatialUnit;
        if (bean.getLazyDataModel() instanceof ActionUnitInSpatialUnitLazyDataModel typedModel) {
            spatialUnit = typedModel.getSpatialUnit();
            unit.getSpatialContext().add(spatialUnit);
        } else if (bean.getParent() instanceof SpatialUnit) {
            spatialUnit = (SpatialUnit) bean.getParent();
            unit.getSpatialContext().add(spatialUnit);
        }
        unit.setBeginDate(OffsetDateTime.now());
    }


    @Override
    public List<SpatialUnit> getSpatialUnitOptions(ActionUnit unit) {
        return List.of();
    }

    @Override
    public String getName(ActionUnit unit) {
        return unit.getName();
    }


}
