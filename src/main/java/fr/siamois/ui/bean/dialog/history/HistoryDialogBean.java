package fr.siamois.ui.bean.dialog.history;

import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.history.RevisionWithInfo;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.history.HistoryAuditService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.utils.DateUtils;
import fr.siamois.utils.MessageUtils;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import lombok.Data;
import org.hibernate.envers.RevisionType;
import org.springframework.stereotype.Component;

import javax.faces.bean.ViewScoped;
import java.util.List;

@ViewScoped
@Data
@Component
public class HistoryDialogBean {

    private final HistoryAuditService historyAuditService;
    private final SpatialUnitService spatialUnitService;
    private final RecordingUnitService recordingUnitService;
    private final ActionUnitService actionUnitService;
    private final SpecimenService specimenService;
    private final LangBean langBean;

    private TraceableEntity entity;
    private List<? extends RevisionWithInfo<? extends TraceableEntity>> history;

    // révision sélectionnée pour restauration
    private RevisionWithInfo<?> pendingRevision;

    public HistoryDialogBean(HistoryAuditService historyAuditService, SpatialUnitService spatialUnitService, RecordingUnitService recordingUnitService, ActionUnitService actionUnitService, SpecimenService specimenService, LangBean langBean) {

        this.historyAuditService = historyAuditService;
        this.spatialUnitService = spatialUnitService;
        this.recordingUnitService = recordingUnitService;
        this.actionUnitService = actionUnitService;
        this.specimenService = specimenService;
        this.langBean = langBean;
    }

    public void open(TraceableEntity entity) {
        this.entity = entity;
        this.history = historyAuditService.findAllRevisionForEntity(
                entity.getClass(), entity.getId());
    }

    public String revisionType(RevisionWithInfo<?> revision) {
        RevisionType type = revision.revisionType();
        if (type == null) return "";
        return switch (type) {
            case ADD    -> langBean.msg("common.history.creation");
            case MOD    -> langBean.msg("common.history.modification");
            case DEL    -> langBean.msg("common.history.deletion");
        };
    }

    public void confirmRestore(RevisionWithInfo<?> revision) {
        this.pendingRevision = revision;
    }

    public void restorePending() {
        if (pendingRevision == null || entity == null) return;

        // Sanity check : l'entité de la révision doit être du même type que l'entité courante
        if (!pendingRevision.entity().getClass().isInstance(entity)) {
            MessageUtils.displayErrorMessage(langBean, "common.action.restoringFailed");
            pendingRevision = null;
            return;
        }

        try {
            @SuppressWarnings("unchecked")
            RevisionWithInfo<TraceableEntity> typedRev =
                    (RevisionWithInfo<TraceableEntity>) pendingRevision;

            // Sauvegarde en base
            persistRestoredEntity(historyAuditService.restoreEntity(typedRev, entity));

            // Refresh du panel?


            // Message de confirmation à l'écran.

            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_INFO,
                            "Restauration effectuée",
                            "L'entité a été restaurée à la révision "
                                    + pendingRevision.revisionEntity().getRevId()));


        } catch (Exception ex) {
            MessageUtils.displayErrorMessage(langBean, "common.action.restoringFailed");
        } finally {
            pendingRevision = null;
        }
    }


    public String revisionDate(RevisionWithInfo<?> revision) {
        return DateUtils.formatUtcDateTime(revision.getDate(), true);
    }

    public String revisionAuthor(RevisionWithInfo<?> revision) {
        return revision.revisionEntity().getUpdatedBy().displayName();
    }

    public Object entity(RevisionWithInfo<?> revision) {
        return revision.entity();
    }

    private void persistRestoredEntity(TraceableEntity entity) {
        if (entity == null) return;

        if (entity instanceof SpatialUnit spatialUnit) {
            spatialUnitService.save(spatialUnit);
        } else if (entity instanceof ActionUnit actionUnit) {
            actionUnitService.save(actionUnit);
        } else if (entity instanceof RecordingUnit recordingUnit) {
            recordingUnitService.save(recordingUnit);
        } else if (entity instanceof Specimen specimen) {
            specimenService.save(specimen);
        }else {
            throw new IllegalArgumentException("Type d'entité non géré : " + entity.getClass());
        }
    }
}


