package fr.siamois.ui.lazydatamodel;

import fr.siamois.dto.SortDTO;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.infrastructure.database.repositories.specs.ActionUnitSpec;
import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public abstract class BaseContainerLazyDataModel extends BaseLazyDataModel<ContainerDTO> {

    private static final Map<String, String> FIELD_MAPPING;

    static {
        Map<String, String> map = new HashMap<>();
        FIELD_MAPPING = Collections.unmodifiableMap(map); // Ensure immutability
    }

    @Override
    protected SortDTO getDefaultSortDTO() {
        SortDTO sortDTO = new SortDTO();
        sortDTO.add(ActionUnitSpec.ID_FILTER, SortDTO.SortOrder.ASC);
        return sortDTO;
    }

    @Override
    protected Map<String, String> getFieldMapping() {
        return FIELD_MAPPING;
    }

    @Override
    public String getRowKey(ContainerDTO containerDTO) {
        return containerDTO != null ? Long.toString(containerDTO.getId()) : null;
    }

}
