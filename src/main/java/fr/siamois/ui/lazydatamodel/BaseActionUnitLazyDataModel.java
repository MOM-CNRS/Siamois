package fr.siamois.ui.lazydatamodel;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.dto.entity.ActionUnitDTO;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public abstract class BaseActionUnitLazyDataModel extends BaseLazyDataModel<ActionUnitDTO> {

    private static final Map<String, String> FIELD_MAPPING;

    static {
        Map<String, String> map = new HashMap<>();
        map.put("category", "c_label");
        map.put("creationTime", "creation_time");
        map.put("author", "p_lastname");
        FIELD_MAPPING = Collections.unmodifiableMap(map); // Ensure immutability
    }

    @Override
    protected Page<ActionUnitDTO> loadData(String name, Long[] categoryIds, Long[] personIds, String globalFilter, Pageable pageable) {
        return loadActionUnits(name, categoryIds, personIds, globalFilter, pageable);
    }

    protected abstract Page<ActionUnitDTO> loadActionUnits(
            String nameFilter, Long[] categoryIds, Long[] personIds,
            String globalFilter, Pageable pageable);

    @Override
    protected String getDefaultSortField() {
        return "action_unit_id";
    }

    @Override
    protected Map<String, String> getFieldMapping() {
        return FIELD_MAPPING;
    }

    @Override
    public String getRowKey(ActionUnitDTO actionUnit) {
        return actionUnit != null ? Long.toString(actionUnit.getId()) : null;
    }



}
