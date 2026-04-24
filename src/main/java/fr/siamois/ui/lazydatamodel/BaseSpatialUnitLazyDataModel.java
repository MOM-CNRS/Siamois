package fr.siamois.ui.lazydatamodel;


import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.SortDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.infrastructure.database.repositories.specs.SpatialUnitSpec;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.primefaces.model.FilterMeta;
import org.primefaces.model.SortMeta;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public abstract class BaseSpatialUnitLazyDataModel extends BaseLazyDataModel<SpatialUnitDTO> {

    private static final Map<String, String> FIELD_MAPPING;

    static {
        Map<String, String> map = new HashMap<>();
        map.put("category", "c_label");
        map.put("creationTime", "creation_time");
        map.put("author", "p_lastname");
        FIELD_MAPPING = Collections.unmodifiableMap(map); // Ensure immutability
    }

    @Override
    protected Map<String, String> getFieldMapping() {
        return FIELD_MAPPING;
    }

    @Override
    protected String getDefaultSortField() {
        return "spatial_unit_id";
    }

    @Override
    public String getRowKey(SpatialUnitDTO spatialUnit) {
        return spatialUnit != null ? Long.toString(spatialUnit.getId()) : null;
    }

    @Override
    protected void prepareFilterDTO(Map<String, FilterMeta> filterBy, FilterDTO filterDTO) {
        if (filterBy == null || filterBy.isEmpty()) {
            return;
        }

        FilterMeta nameMeta = filterBy.get(SpatialUnitSpec.NAME_FILTER);
        if (nameMeta != null && nameMeta.getFilterValue() != null) {
            filterDTO.add(SpatialUnitSpec.NAME_FILTER, nameMeta.getFilterValue().toString(), FilterDTO.FilterType.CONTAINS);
        }

        FilterMeta categoryMeta = filterBy.get(SpatialUnitSpec.CATEGORY_FILTER);
        if (categoryMeta != null && categoryMeta.getFilterValue() instanceof List<?> ids && !ids.isEmpty()) {
            filterDTO.add(SpatialUnitSpec.CATEGORY_FILTER, ids, FilterDTO.FilterType.CONTAINS);
        }
    }

    @Override
    protected void prepareSortDTO(@Nullable Map<String, SortMeta> sortBy, @NonNull SortDTO sortDTO) {
        if (sortBy != null && !sortBy.isEmpty()) {
            SortMeta sortMeta = sortBy.get(SpatialUnitSpec.NAME_FILTER);
            if (sortMeta != null) {
                sortDTO.add(SpatialUnitSpec.NAME_FILTER, sortMeta.getOrder());
            }
        }
    }

}
