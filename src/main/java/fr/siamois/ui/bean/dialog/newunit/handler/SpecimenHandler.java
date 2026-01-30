package fr.siamois.ui.bean.dialog.newunit.handler;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.EntityAlreadyExistsException;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.dialog.newunit.NewUnitContext;
import fr.siamois.ui.bean.dialog.newunit.UnitKind;
import fr.siamois.ui.exceptions.CannotInitializeNewUnitDialogException;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class SpecimenHandler implements INewUnitHandler<Specimen> {

    private final SpecimenService specimenService;
    private final RecordingUnitService recordingUnitService;
    private final SessionSettingsBean sessionSettingsBean;

    public SpecimenHandler(SessionSettingsBean sessionSettingsBean, SpecimenService specimenService, RecordingUnitService recordingUnitService) {
        this.sessionSettingsBean = sessionSettingsBean;
        this.specimenService = specimenService;
        this.recordingUnitService = recordingUnitService;
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
        NewUnitContext ctx = bean.getNewUnitContext();
        if (ctx == null) throw new CannotInitializeNewUnitDialogException("Specimen cannot be created without a context");

        // 1) If creation comes from toolbar: use SCOPE
        NewUnitContext.Trigger trigger = ctx.getTrigger();
        if (trigger != null && trigger.getType() == NewUnitContext.TriggerType.TOOLBAR) {
            applyScope(unit, ctx);
        }

        handleCellContext(ctx, unit);


    }

    private void handleCellContext(NewUnitContext ctx, Specimen unit) throws CannotInitializeNewUnitDialogException {
        NewUnitContext.Trigger trigger = ctx.getTrigger();
        if (trigger == null || trigger.getClickedId() == null || trigger.getColumnKey() == null) {
            return;
        }

        Long clickedId = trigger.getClickedId();
        String key = trigger.getColumnKey();
        RecordingUnit clicked = recordingUnitService.findById(clickedId);

        if (clicked == null) {
            return;
        }

        if(key.equals("specimen")) {
            unit.setCreatedByInstitution(clicked.getCreatedByInstitution());
            unit.setRecordingUnit(clicked);
            unit.setCreatedBy(sessionSettingsBean.getAuthenticatedUser());
            unit.setAuthors(List.of(sessionSettingsBean.getAuthenticatedUser()));
            unit.setCollectors(List.of(sessionSettingsBean.getAuthenticatedUser()));
            unit.setCollectionDate(OffsetDateTime.now());
        }

        switch (key) {
            default -> { /* no-op */ }
        }
    }

    private void applyScope(Specimen unit, NewUnitContext ctx) throws CannotInitializeNewUnitDialogException {
        NewUnitContext.Scope scope = ctx.getScope();
        if (scope == null || scope.getKey() == null || scope.getEntityId() == null) {
            throw new CannotInitializeNewUnitDialogException("Specimen cannot be created without a context");
        }


        if ("RECORDING".equals(scope.getKey())) {
            RecordingUnit ru = recordingUnitService.findById(scope.getEntityId()); // adapt Optional
            if (ru != null) {
                unit.setCreatedByInstitution(ru.getCreatedByInstitution());
                unit.setRecordingUnit(ru);
                unit.setCreatedBy(sessionSettingsBean.getAuthenticatedUser());
                unit.setAuthors(List.of(sessionSettingsBean.getAuthenticatedUser()));
                unit.setCollectors(List.of(sessionSettingsBean.getAuthenticatedUser()));
                unit.setCollectionDate(OffsetDateTime.now());
                return ;
            }
        }

        throw new CannotInitializeNewUnitDialogException("Specimen cannot be created without a context");
    }

    @Override
    public String getName(Specimen unit) {
        return unit.getFullIdentifier();
    }

}
