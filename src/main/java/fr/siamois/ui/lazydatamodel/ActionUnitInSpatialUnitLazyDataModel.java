package fr.siamois.ui.lazydatamodel;

import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import lombok.Getter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public class ActionUnitInSpatialUnitLazyDataModel extends BaseActionUnitLazyDataModel {

    private final transient ActionUnitService actionUnitService;
    private final transient SessionSettingsBean sessionSettings;
    private final transient LangBean langBean;

    @Getter
    private final transient SpatialUnitDTO spatialUnit;

    public ActionUnitInSpatialUnitLazyDataModel(ActionUnitService actionUnitService, SessionSettingsBean sessionSettings,
                                                LangBean langBean, SpatialUnitDTO spatialUnit) {
        this.actionUnitService = actionUnitService;
        this.sessionSettings = sessionSettings;
        this.langBean = langBean;
        this.spatialUnit = spatialUnit;
    }

    @Override
    protected Page<ActionUnitDTO> loadActionUnits(String nameFilter, Long[] categoryIds, Long[] personIds, String globalFilter, Pageable pageable) {
        return actionUnitService.findAllByInstitutionAndBySpatialUnitAndByNameContainingAndByCategoriesAndByGlobalContaining(
                sessionSettings.getSelectedInstitution().getId(),
                spatialUnit.getId(),
                nameFilter, categoryIds, personIds, globalFilter,
                langBean.getLanguageCode(),
                pageable);
    }



}
