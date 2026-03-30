package fr.siamois.ui.viewmodel;

import fr.siamois.dto.entity.SpatialUnitSummaryDTO;
import lombok.Data;
import org.primefaces.model.CheckboxTreeNode;
import org.primefaces.model.TreeNode;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Data
public class TreeUiStateViewModel implements Serializable {
    private transient TreeNode<SpatialUnitSummaryDTO> root;
    private Set<SpatialUnitSummaryDTO> selection = new HashSet<>();
    private Map<Long, CheckboxTreeNode<SpatialUnitSummaryDTO>> index = new HashMap<>();
    // getters/setters
}