package fr.siamois.ui.lazydatamodel;

import fr.siamois.dto.SortDTO;
import fr.siamois.dto.entity.PhaseDTO;
import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public abstract class BasePhaseLazyDataModel extends BaseLazyDataModel<PhaseDTO> {

    private static final Map<String, String> FIELD_MAPPING;

    static {
        Map<String, String> map = new HashMap<>();
        FIELD_MAPPING = Collections.unmodifiableMap(map);
    }

    @Override
    protected SortDTO getDefaultSortDTO() {
        SortDTO sortDTO = new SortDTO();
        sortDTO.add("id", SortDTO.SortOrder.ASC);
        return sortDTO;
    }

    @Override
    protected Map<String, String> getFieldMapping() {
        return FIELD_MAPPING;
    }

    @Override
    public String getRowKey(PhaseDTO phaseDTO) {
        return phaseDTO != null ? Long.toString(phaseDTO.getId()) : null;
    }
}
