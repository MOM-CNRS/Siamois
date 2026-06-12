package fr.siamois.ui.lazydatamodel;

import fr.siamois.domain.services.PhaseService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.SortDTO;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.infrastructure.database.repositories.specs.ActionUnitSpec;
import fr.siamois.ui.bean.SessionSettingsBean;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.primefaces.model.FilterMeta;
import org.primefaces.model.SortMeta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;

public class PhaseLazyDataModel extends BasePhaseLazyDataModel {

    private final transient PhaseService phaseService;
    private final transient SessionSettingsBean sessionSettings;

    public PhaseLazyDataModel(PhaseService phaseService, SessionSettingsBean sessionSettings) {
        this.phaseService = phaseService;
        this.sessionSettings = sessionSettings;
    }

    @Override
    protected Page<PhaseDTO> loadData(FilterDTO filter, Pageable pageable) {
        return phaseService.searchPhases(sessionSettings.getSelectedInstitution(), filter, pageable);
    }

    @Override
    protected int countWithFilter(FilterDTO filters) {
        return phaseService.countSearchResults(sessionSettings.getSelectedInstitution(), filters);
    }

    @Override
    protected void prepareFilterDTO(Map<String, FilterMeta> filterBy, FilterDTO filterDTO) {
        if (filterBy != null && !filterBy.isEmpty()) {
            FilterMeta nameMeta = filterBy.get(ActionUnitSpec.NAME_FILTER);
            if (nameMeta != null && nameMeta.getFilterValue() != null) {
                filterDTO.add(ActionUnitSpec.NAME_FILTER, nameMeta.getFilterValue().toString(), FilterDTO.FilterType.CONTAINS);
            }
            FilterMeta globalMeta = filterBy.get(ActionUnitSpec.GLOBAL_FILTER);
            if (globalMeta != null && globalMeta.getFilterValue() != null) {
                filterDTO.add(FilterDTO.GLOBAL_FILTER_KEY, globalMeta.getFilterValue().toString(), FilterDTO.FilterType.CONTAINS);
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
