package fr.siamois.ui.lazydatamodel;


import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.dto.entity.SpatialUnitDTO;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.HashMap;
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
    protected Page<SpatialUnitDTO> loadData(String name, Long[] categoryIds, Long[] personIds, String globalFilter, Pageable pageable) {
        return loadSpatialUnits(name, categoryIds, personIds, globalFilter, pageable);
    }

    @Override
    public String getRowKey(SpatialUnitDTO spatialUnit) {
        return spatialUnit != null ? Long.toString(spatialUnit.getId()) : null;
    }

    protected abstract Page<SpatialUnitDTO> loadSpatialUnits(String nameFilter, Long[] categoryIds, Long[] personIds, String globalFilter, Pageable pageable);

}
