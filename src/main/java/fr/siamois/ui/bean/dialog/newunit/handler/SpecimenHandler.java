package fr.siamois.ui.bean.dialog.newunit.handler;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.EntityAlreadyExistsException;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.dialog.newunit.UnitKind;
import fr.siamois.ui.exceptions.CannotInitializeNewUnitDialogException;
import fr.siamois.ui.lazydatamodel.SpecimenInRecordingUnitLazyDataModel;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class SpecimenHandler implements INewUnitHandler<Specimen> {

    private final SpecimenService specimenService;
    private final SessionSettingsBean sessionSettingsBean;

    public SpecimenHandler(SessionSettingsBean sessionSettingsBean, SpecimenService specimenService) {
        this.sessionSettingsBean = sessionSettingsBean;
        this.specimenService = specimenService;
    }

    @Override
    public List<SpatialUnit> getSpatialUnitOptions(Specimen unit) {
        return List.of();
    }

    @Override
    public UnitKind kind() {
        return UnitKind.SPECIMEN;
    }

    @Override
    public Specimen newEmpty() {
        return new Specimen();
    }

    @Override
    public Specimen save(UserInfo u, Specimen unit) throws EntityAlreadyExistsException {
        return specimenService.save(unit);
    }

    @Override
    public String dialogWidgetVar() {
        return "newUnitDiag";
    }


    @Override
    public void initFromContext(GenericNewUnitDialogBean<?> bean) throws CannotInitializeNewUnitDialogException {

        Specimen unit = (Specimen) bean.getUnit();
        RecordingUnit ru;
        if (bean.getLazyDataModel() instanceof SpecimenInRecordingUnitLazyDataModel typedModel) {
            ru = typedModel.getRecordingUnit();
        } else if (bean.getParent() instanceof RecordingUnit) {
            ru = (RecordingUnit) bean.getParent();
        } else {
            throw new CannotInitializeNewUnitDialogException("Specimen cannot be created without a context");
        }
        unit.setRecordingUnit(ru);
        unit.setCreatedByInstitution(sessionSettingsBean.getSelectedInstitution());
        unit.setCreatedBy(sessionSettingsBean.getAuthenticatedUser());
        unit.setAuthors(List.of(sessionSettingsBean.getAuthenticatedUser()));
        unit.setCollectors(List.of(sessionSettingsBean.getAuthenticatedUser()));
        unit.setCollectionDate(OffsetDateTime.now());
    }

    @Override
    public String getName(Specimen unit) {
        return unit.getFullIdentifier();
    }

}
