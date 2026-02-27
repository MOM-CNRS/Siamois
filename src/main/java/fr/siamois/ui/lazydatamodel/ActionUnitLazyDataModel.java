package fr.siamois.ui.lazydatamodel;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public class ActionUnitLazyDataModel extends BaseActionUnitLazyDataModel {

    private final transient ActionUnitService actionUnitService;
    private final transient SessionSettingsBean sessionSettings;
    private final transient LangBean langBean;

    public ActionUnitLazyDataModel(ActionUnitService actionUnitService, SessionSettingsBean sessionSettings, LangBean langBean) {
        this.actionUnitService = actionUnitService;
        this.sessionSettings = sessionSettings;
        this.langBean = langBean;
    }

    @Override
    protected Page<ActionUnitDTO> loadActionUnits(String nameFilter, Long[] categoryIds, Long[] personIds, String globalFilter, Pageable pageable) {
        return actionUnitService.findAllByInstitutionAndByNameContainingAndByCategoriesAndByGlobalContaining(
                sessionSettings.getSelectedInstitution().getId(),
                nameFilter, categoryIds, personIds, globalFilter,
                langBean.getLanguageCode(),
                pageable);
    }



}
