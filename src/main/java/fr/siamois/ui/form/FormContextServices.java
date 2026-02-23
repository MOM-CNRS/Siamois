package fr.siamois.ui.form;

import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitTreeService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.ui.bean.LangBean;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@Getter
@RequiredArgsConstructor
public class FormContextServices {

    private final FormService formService;
    private final LangBean langBean;
    private final SpatialUnitTreeService spatialUnitTreeService;
    private final SpatialUnitService spatialUnitService;
    private final SpecimenService specimenService;
    private final RecordingUnitService recordingUnitService;
    private final ActionUnitService actionUnitService;

}