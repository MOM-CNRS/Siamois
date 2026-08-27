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
        String localFullIdentifierFilter;
        if (filterBy != null && !filterBy.isEmpty()) {
            FilterMeta nameMeta = filterBy.get(ActionUnitSpec.NAME_FILTER);
            if (nameMeta != null && nameMeta.getFilterValue() != null) {
                localNameFilter = nameMeta.getFilterValue().toString();
                filterDTO.add(ActionUnitSpec.NAME_FILTER, localNameFilter, FilterDTO.FilterType.CONTAINS);
            }

            FilterMeta fullIdentifierMeta = filterBy.get(ActionUnitSpec.FULL_IDENTIFIER_FILTER);
            if (fullIdentifierMeta != null && fullIdentifierMeta.getFilterValue() != null) {
                localFullIdentifierFilter = fullIdentifierMeta.getFilterValue().toString();
                filterDTO.add(ActionUnitSpec.FULL_IDENTIFIER_FILTER, localFullIdentifierFilter, FilterDTO.FilterType.CONTAINS);
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

            SortMeta fullIdentifierSortMeta = sortBy.get(ActionUnitSpec.FULL_IDENTIFIER_FILTER);
            if (fullIdentifierSortMeta != null) {
                sortDTO.add(ActionUnitSpec.FULL_IDENTIFIER_FILTER, fullIdentifierSortMeta.getOrder());
            }

            SortMeta recordingUnitCountSortMeta = sortBy.get(ActionUnitSpec.RECORDING_UNIT_COUNT_SORT);
            if (recordingUnitCountSortMeta != null) {
                sortDTO.add(ActionUnitSpec.RECORDING_UNIT_COUNT_SORT, recordingUnitCountSortMeta.getOrder());
            }
        }
    }
}
