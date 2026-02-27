package fr.siamois.ui.viewmodel;

import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.dto.entity.SpatialUnitDTO;
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
    private transient TreeNode<SpatialUnitDTO> root;
    private Set<SpatialUnitDTO> selection = new HashSet<>();
    private Map<Long, CheckboxTreeNode<SpatialUnitDTO>> index = new HashMap<>();
    // getters/setters
}