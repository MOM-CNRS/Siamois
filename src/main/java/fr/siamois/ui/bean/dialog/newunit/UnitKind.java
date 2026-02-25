package fr.siamois.ui.bean.dialog.newunit;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.actionunit.form.ActionUnitNewForm;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.specimen.Specimen;
import lombok.Getter;

@Getter
public enum UnitKind {
    SPATIAL(new UnitKindConfig(
            "/spatial-unit/new",
            "Nouvelle unité spatiale",
            "spatial-unit-panel",
            "bi bi-pencil-square",
            "spatial-unit-autocomplete",
            "common.entity.spatialUnits.updated",
            "/spatial-unit/",
            SpatialUnit.NEW_UNIT_FORM

    )),
    ACTION(new UnitKindConfig(
            "/action-unit/new",
            "Nouvelle unité d'action",
            "action-unit-panel",
            "bi bi-arrow-down-square",
            "action-unit-autocomplete",
            "common.entity.spatialUnits.updated",
            "/action-unit/",
            ActionUnitNewForm.build()
    )),
    SPECIMEN(new UnitKindConfig(
            "/specimen/new",
            "Nouveau prélèvement",
            "specimen-panel",
            "bi bi-box-2e",
            "specimen-autocomplete",
            "common.entity.specimen.updated",
            "/specimen/",
            Specimen.NEW_UNIT_FORM
    )),
    RECORDING(new UnitKindConfig(
            "/recording-unit/new",
            "Nouvelle unité d'enregistrement",
            "recording-unit-panel",
            "bi bi-pencil-square",
            "recording-unit-autocomplete",
            "common.entity.recordingUnits.updated",
            "/recording-unit/",
            RecordingUnit.NEW_UNIT_FORM
    ));

    private final UnitKindConfig config;

    UnitKind(UnitKindConfig config) {
        this.config = config;
    }

}
