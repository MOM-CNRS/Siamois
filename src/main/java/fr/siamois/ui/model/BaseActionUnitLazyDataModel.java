package fr.siamois.ui.model;

import fr.siamois.domain.models.actionunit.ActionUnit;

import lombok.Getter;
import lombok.Setter;

import org.springframework.data.domain.Page;

import org.springframework.data.domain.Pageable;

import java.util.*;

@Getter
@Setter
public abstract class BaseActionUnitLazyDataModel extends BaseLazyDataModel<ActionUnit> {



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
        return "action_unit_id";
    }

    @Override
    protected Page<ActionUnit> loadData(String name, Long[] categoryIds, Long[] personIds, String globalFilter, Pageable pageable) {
        return loadActionUnits(name, categoryIds, personIds, globalFilter, pageable);
    }

    protected abstract Page<ActionUnit> loadActionUnits(
            String nameFilter, Long[] categoryIds, Long[] personIds,
            String globalFilter, Pageable pageable);

}
