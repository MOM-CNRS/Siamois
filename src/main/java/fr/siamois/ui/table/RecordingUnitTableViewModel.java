package fr.siamois.ui.table;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.CustomFieldDateTime;
import fr.siamois.domain.models.form.customfield.CustomFieldInteger;
import fr.siamois.domain.models.form.customform.CustomCol;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.form.customform.CustomFormPanel;
import fr.siamois.domain.models.form.customform.CustomRow;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitTreeService;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.form.EntityFormContext;
import fr.siamois.ui.form.FieldSource;
import fr.siamois.ui.form.TableRowFieldSource;
import fr.siamois.ui.lazydatamodel.BaseRecordingUnitLazyDataModel;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.*;

/**
 * Bean de vue pour les tableaux de RecordingUnit.
 *
 * - tient la définition des colonnes (TableDefinition)
 * - s'appuie sur un BaseRecordingUnitLazyDataModel "pur data"
 * - crée un EntityFormContext par ligne pour gérer :
 *     - les réponses
 *     - les règles d'activation
 *     - les min/max des champs système
 */
@Getter
@Setter
public class RecordingUnitTableViewModel {

    /** Lazy model "pur data" (chargement, tri, filtres, sélection, etc.) */
    private final BaseRecordingUnitLazyDataModel lazyDataModel;

    /** Services nécessaires pour la logique formulaire de ligne */
    private final FormService formService;
    private final SessionSettingsBean sessionSettingsBean;
    private final SpatialUnitTreeService spatialUnitTreeService;
    private final SpatialUnitService spatialUnitService;

    /** Définition globale des colonnes de la table */
    private final TableDefinition tableDefinition = new TableDefinition();

    /** Contexte de formulaire par ligne (clé = id de RecordingUnit) */
    private final Map<Long, EntityFormContext<RecordingUnit>> rowContexts = new HashMap<>();

    public RecordingUnitTableViewModel(BaseRecordingUnitLazyDataModel lazyDataModel,
                                       FormService formService,
                                       SessionSettingsBean sessionSettingsBean,
                                       SpatialUnitTreeService spatialUnitTreeService,
                                       SpatialUnitService spatialUnitService) {
        this.lazyDataModel = lazyDataModel;
        this.formService = formService;
        this.sessionSettingsBean = sessionSettingsBean;
        this.spatialUnitTreeService = spatialUnitTreeService;
        this.spatialUnitService = spatialUnitService;
    }

    /**
     * Colonnes visibles (pour <p:columns>).
     */
    public List<TableColumn> getColumns() {
        return tableDefinition.getVisibleColumns();
    }

    /**
     * Retourne (ou crée) le contexte de formulaire pour une ligne donnée.
     * Utilisable directement en EL : #{tableViewModel.rowContext(item)}
     */
    public EntityFormContext<RecordingUnit> getRowContext(RecordingUnit ru) {
        if (ru == null || ru.getId() == null) {
            return null;
        }

        return rowContexts.computeIfAbsent(ru.getId(), id -> {
            // 1) Formulaire spécifique à ce RecordingUnit (selon son type + institution)
            CustomForm rowForm = resolveRowFormFor(ru);

            // 2) Configuration min/max des champs système pour CETTE ligne
            configureRowSystemFields(ru, rowForm);

            // 3) FieldSource pour cette ligne : colonnes de la table + form spécifique
            FieldSource fs = new TableRowFieldSource(tableDefinition, rowForm);

            // 4) Contexte de formulaire pour cette ligne
            EntityFormContext<RecordingUnit> ctx = new EntityFormContext<>(
                    ru,
                    fs,
                    formService,
                    spatialUnitTreeService,
                    spatialUnitService
            );
            ctx.init(false);
            return ctx;
        });
    }

    /**
     * Détermine le CustomForm spécifique à une ligne de RecordingUnit,
     * avec la même logique que dans RecordingUnitPanel.
     */
    protected CustomForm resolveRowFormFor(RecordingUnit ru) {
        Concept type = ru.getType();
        if (type == null) {
            return null;
        }
        return formService.findCustomFormByRecordingUnitTypeAndInstitutionId(
                type,
                sessionSettingsBean.getSelectedInstitution()
        );
    }

    /**
     * Applique la logique min/max sur les champs système pour une ligne donnée.
     * Équivalent de configureSystemFieldsBeforeInit(), mais au niveau table.
     */
    protected void configureRowSystemFields(RecordingUnit ru, CustomForm rowForm) {
        if (rowForm == null || rowForm.getLayout() == null) {
            return;
        }

        for (CustomField field : getAllFieldsFromForm(rowForm)) {

            // Recording unit identifier
            if ("identifier".equals(field.getValueBinding()) && field instanceof CustomFieldInteger cfi) {
                if (ru.getActionUnit() != null) {
                    cfi.setMaxValue(ru.getActionUnit().getMaxRecordingUnitCode());
                    cfi.setMinValue(ru.getActionUnit().getMinRecordingUnitCode());
                }
            }

            // Min and max datetime
            if (field instanceof CustomFieldDateTime dt) {
                if ("openingDate".equals(field.getValueBinding()) && ru.getClosingDate() != null) {
                    dt.setMax(ru.getClosingDate().toLocalDateTime());
                    dt.setMin(LocalDateTime.of(1000, Month.JANUARY, 1, 1, 1));
                }
                if ("closingDate".equals(field.getValueBinding()) && ru.getOpeningDate() != null) {
                    dt.setMin(ru.getOpeningDate().toLocalDateTime());
                    dt.setMax(LocalDateTime.of(9999, Month.DECEMBER, 31, 23, 59));
                }
            }
        }
    }

    /**
     * Helper : récupère tous les CustomField d'un CustomForm (panels → rows → cols).
     */
    protected List<CustomField> getAllFieldsFromForm(CustomForm form) {
        if (form == null || form.getLayout() == null) {
            return List.of();
        }

        Set<CustomField> fields = new LinkedHashSet<>();

        for (CustomFormPanel panel : form.getLayout()) {
            if (panel.getRows() == null) continue;
            for (CustomRow row : panel.getRows()) {
                if (row.getColumns() == null) continue;
                for (CustomCol col : row.getColumns()) {
                    CustomField field = col.getField();
                    if (field != null) {
                        fields.add(field);
                    }
                }
            }
        }

        return new ArrayList<>(fields);
    }

    /**
     * À appeler si tu veux "réinitialiser" les contextes de formulaire,
     * par exemple après un gros refresh de la liste.
     */
    public void resetRowContexts() {
        rowContexts.clear();
    }
}
