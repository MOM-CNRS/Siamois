package fr.siamois.ui.bean.dialog.newunit.handler;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.exceptions.EntityAlreadyExistsException;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.RecordingUnitSummaryDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.dialog.newunit.NewUnitContext;
import fr.siamois.ui.bean.dialog.newunit.UnitKind;
import fr.siamois.ui.exceptions.CannotInitializeNewUnitDialogException;
import fr.siamois.utils.MessageUtils;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class RecordingUnitHandler implements INewUnitHandler<RecordingUnitDTO> {

    public static final String CHILDREN = "children";
    public static final String PARENTS = "parents";
    private final RecordingUnitService recordingUnitService;
    private final ActionUnitService actionUnitService;
    private final SessionSettingsBean sessionSettingsBean;
    private final LangBean langBean;

    public RecordingUnitHandler(RecordingUnitService recordingUnitService,
                                ActionUnitService actionUnitService,
                                SessionSettingsBean sessionSettingsBean,
                                LangBean langBean) {
        this.recordingUnitService = recordingUnitService;
        this.actionUnitService = actionUnitService;
        this.sessionSettingsBean = sessionSettingsBean;
        this.langBean = langBean;
    }

    @Override
    public List<SpatialUnitDTO> getSpatialUnitOptions(RecordingUnitDTO unit) {
        ActionUnitDTO actionUnit = unit.getActionUnit();
        // Return the spatial context of the parent action
        if (actionUnit != null) {
            return new ArrayList<>(actionUnit.getSpatialContext());
        }

        return List.of();
    }

    @Override public UnitKind kind() { return UnitKind.RECORDING; }
    @Override public RecordingUnitDTO newEmpty() {
        RecordingUnitDTO recordingUnit = new RecordingUnitDTO();
        recordingUnit.setOpeningDate(OffsetDateTime.now());
        return recordingUnit;
    }
    @Override public RecordingUnitDTO save(UserInfo u, RecordingUnitDTO unit) throws EntityAlreadyExistsException {
        RecordingUnitDTO created = recordingUnitService.save(unit, unit.getType());
        String generatedFullIdentifier = recordingUnitService.generateFullIdentifier(created.getActionUnit(), created);
        created.setFullIdentifier(generatedFullIdentifier);

        if (recordingUnitService.fullIdentifierAlreadyExistInAction(created)) {
            MessageUtils.displayWarnMessage(langBean, "recordingunit.error.identifier.alreadyExists");
            created.setFullIdentifier(unit.getActionUnit().getRecordingUnitIdentifierFormat());
        }

        created = recordingUnitService.save(created);

        return created;
    }

    @Override public String dialogWidgetVar() { return "newUnitDiag"; }

    @Override public void initFromContext(GenericNewUnitDialogBean<?> bean) throws CannotInitializeNewUnitDialogException {

        RecordingUnitDTO unit = (RecordingUnitDTO) bean.getUnit();
        NewUnitContext ctx = bean.getNewUnitContext();
        if (ctx == null) throw new CannotInitializeNewUnitDialogException("Recording unit cannot be created without a context");

        // 1) If creation comes from toolbar: use SCOPE
        NewUnitContext.Trigger trigger = ctx.getTrigger();
        if (trigger != null && trigger.getType() == NewUnitContext.TriggerType.TOOLBAR) {
            applyScope(unit, ctx);
            return;
        }

        handleCellContext(ctx, unit);

    }

    private void handleCellContext(NewUnitContext ctx, RecordingUnitDTO unit) throws CannotInitializeNewUnitDialogException {
        NewUnitContext.Trigger trigger = ctx.getTrigger();
        if (trigger == null || trigger.getClickedId() == null || trigger.getColumnKey() == null) {
            return;
        }

        Long clickedId = trigger.getClickedId();
        String key = trigger.getColumnKey();
        RecordingUnitDTO clicked = recordingUnitService.findById(clickedId);

        if (clicked == null) {
            return;
        }

        if(key.equals(PARENTS) || key.equals(CHILDREN)) {
            unit.setCreatedByInstitution(clicked.getCreatedByInstitution());
            unit.setActionUnit(clicked.getActionUnit());
            unit.setAuthor(sessionSettingsBean.getAuthenticatedUser());
            unit.setContributors(List.of(sessionSettingsBean.getAuthenticatedUser()));
            unit.setOpeningDate(OffsetDateTime.now());
            unit.setSpatialUnit(clicked.getSpatialUnit());
        }

        switch (key) {
            case PARENTS -> unit.getChildren().add(new RecordingUnitSummaryDTO(clicked));
            case CHILDREN -> unit.getParents().add(new RecordingUnitSummaryDTO(clicked));
            default -> { /* no-op */ }
        }
    }

    private void applyScope(RecordingUnitDTO unit, NewUnitContext ctx) throws CannotInitializeNewUnitDialogException {
        NewUnitContext.Scope scope = ctx.getScope();
        if (scope == null || scope.getKey() == null || scope.getEntityId() == null) {
            throw new CannotInitializeNewUnitDialogException("Recording unit cannot be created without a context");
        }


        if ("ACTION".equals(scope.getKey())) {
            ActionUnitDTO au = actionUnitService.findById(scope.getEntityId()); // adapt Optional
            if (au != null) {
                unit.setCreatedByInstitution(au.getCreatedByInstitution());
                unit.setActionUnit(au);
                unit.setAuthor(sessionSettingsBean.getAuthenticatedUser());
                unit.setContributors(List.of(sessionSettingsBean.getAuthenticatedUser()));
                unit.setOpeningDate(OffsetDateTime.now());
                return ;
            }
        }

        throw new CannotInitializeNewUnitDialogException("Recording unit cannot be created without a context");
    }


    @Override
    public String getTitle(NewUnitContext ctx) {
        if (ctx == null) {
            return INewUnitHandler.super.getTitle(ctx);
        }

        // =========================
        // 1) CELL trigger (clic sur colonne)
        // =========================
        NewUnitContext.Trigger trigger = ctx.getTrigger();
        if (trigger != null
                && trigger.getType() == NewUnitContext.TriggerType.CELL
                && trigger.getClickedKind() == UnitKind.RECORDING
                && trigger.getClickedId() != null
                && trigger.getColumnKey() != null) {

            RecordingUnitDTO clicked = recordingUnitService.findById(trigger.getClickedId());
            String name = clicked != null
                    ? clicked.getFullIdentifier()
                    : ("#" + trigger.getClickedId());

            return switch (trigger.getColumnKey()) {
                case PARENTS ->
                        langBean.msg("dialog.label.title.recording.parent") + " " + name;
                case CHILDREN ->
                        langBean.msg("dialog.label.title.recording.child") + " " + name;
                default ->
                        INewUnitHandler.super.getTitle(ctx);
            };
        }


        // =========================
        // 2) TOOLBAR trigger (scope table)
        // =========================
        NewUnitContext.Scope scope = ctx.getScope();
        if (scope != null && "ACTION".equals(scope.getKey()) && scope.getEntityId() != null) {

            ActionUnitDTO scoped = actionUnitService.findById(scope.getEntityId());
            String name = scoped != null ? scoped.getName() : ("#" + scope.getEntityId());

            return langBean.msg("dialog.label.title.recording.actionContext",name);
        }

        // =========================
        // 3) Default fallback
        // =========================
        return INewUnitHandler.super.getTitle(ctx);
    }

    @Override
    public String getName(RecordingUnitDTO unit) {
        return unit.getFullIdentifier();
    }


}
