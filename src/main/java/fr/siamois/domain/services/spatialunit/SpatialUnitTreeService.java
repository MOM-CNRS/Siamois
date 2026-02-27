package fr.siamois.domain.services.spatialunit;

import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.ui.bean.SessionSettingsBean;
import org.primefaces.model.CheckboxTreeNode;
import org.primefaces.model.TreeNode;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class SpatialUnitTreeService {

    private final SpatialUnitService spatialUnitService;
    private final SessionSettingsBean sessionSettingsBean;

    public SpatialUnitTreeService(SpatialUnitService spatialUnitService, SessionSettingsBean sessionSettingsBean) {
        this.spatialUnitService = spatialUnitService;
        this.sessionSettingsBean = sessionSettingsBean;
    }

    /** Récursion avec détection de cycle par "chemin" */
    private void buildChildren(TreeNode<SpatialUnitDTO> parentNode, SpatialUnitDTO parent, Set<Long> pathIds) {
        List<SpatialUnitDTO> enfants = spatialUnitService.findDirectChildrensOf(parent.getId());
        if (enfants == null || enfants.isEmpty()) {
            return;
        }
        for (SpatialUnitDTO child : enfants) {
            if (pathIds.contains(child.getId())) {
                // Cycle détecté : on l’affiche en grisé et non sélectionnable
                TreeNode<SpatialUnitDTO> cycle = new CheckboxTreeNode<>("cycle", child, parentNode);
                cycle.setSelectable(false);
                continue;
            }
            TreeNode<SpatialUnitDTO> childNode = new CheckboxTreeNode<>("SpatialUnit", child, parentNode);
            // nouveau "chemin" pour la branche (important avec multi-parents)
            Set<Long> nextPath = new HashSet<>(pathIds);
            nextPath.add(child.getId());
            buildChildren(childNode, child, nextPath);
        }
    }


    /**
     * Returns the tree node of all the spatial units in the active institution
     * @return The tree node
     */
    public TreeNode<SpatialUnitDTO> buildTree() {

        TreeNode<SpatialUnitDTO> root = new CheckboxTreeNode<>(new SpatialUnitDTO(), null);
        List<SpatialUnitDTO> racines = spatialUnitService.findRootsOf(sessionSettingsBean.getSelectedInstitution().getId());

        for (SpatialUnitDTO r : racines) {
            TreeNode<SpatialUnitDTO> rNode = new CheckboxTreeNode<>("SpatialUnit", r, root);
            rNode.setExpanded(false);
            // we memorize path to avoid cycles
            Set<Long> path = new HashSet<>();
            path.add(r.getId());
            buildChildren(rNode, r, path);
        }

        return root;

    }
}
