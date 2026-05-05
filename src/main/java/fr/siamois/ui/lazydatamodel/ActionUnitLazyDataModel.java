package fr.siamois.ui.lazydatamodel;

import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.SortDTO;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.infrastructure.database.repositories.specs.ActionUnitSpec;
import fr.siamois.ui.bean.SessionSettingsBean;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.primefaces.model.FilterMeta;
import org.primefaces.model.SortMeta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;


public class ActionUnitLazyDataModel extends BaseActionUnitLazyDataModel {

    private final transient ActionUnitService actionUnitService;
    private final transient SessionSettingsBean sessionSettings;

    public ActionUnitLazyDataModel(ActionUnitService actionUnitService, SessionSettingsBean sessionSettings) {
        this.actionUnitService = actionUnitService;
        this.sessionSettings = sessionSettings;
    }


    @Override
    protected Page<ActionUnitDTO> loadData(FilterDTO filter, Pageable pageable) {
        return actionUnitService.searchActionUnits(sessionSettings.getSelectedInstitution(), filter, pageable);
    }

    @Override
    protected int countWithFilter(FilterDTO filters) {
        return actionUnitService.countSearchResults(sessionSettings.getSelectedInstitution(), filters);
    }

    @Override
    protected void prepareFilterDTO(Map<String, FilterMeta> filterBy, FilterDTO filterDTO) {
        String localNameFilter;
        String localGlobalFilter;
        if (filterBy != null && !filterBy.isEmpty()) {
            FilterMeta nameMeta = filterBy.get(ActionUnitSpec.NAME_FILTER);
            if (nameMeta != null && nameMeta.getFilterValue() != null) {
                localNameFilter = nameMeta.getFilterValue().toString();
                filterDTO.add(ActionUnitSpec.NAME_FILTER, localNameFilter, FilterDTO.FilterType.CONTAINS);
            }

            FilterMeta globalMeta = filterBy.get(ActionUnitSpec.GLOBAL_FILTER);
            if (globalMeta != null && globalMeta.getFilterValue() != null) {
                localGlobalFilter = globalMeta.getFilterValue().toString();
                filterDTO.add(FilterDTO.GLOBAL_FILTER_KEY, localGlobalFilter, FilterDTO.FilterType.CONTAINS);
            }
        }
    }

    @Override
    protected void prepareSortDTO(@Nullable Map<String, SortMeta> sortBy, @NonNull SortDTO sortDTO) {
        if (sortBy != null && !sortBy.isEmpty()) {
            SortMeta sortMeta = sortBy.get(ActionUnitSpec.NAME_FILTER);
            if (sortMeta != null) {
                sortDTO.add(ActionUnitSpec.NAME_FILTER, sortMeta.getOrder());
            }
        }
    }
}
