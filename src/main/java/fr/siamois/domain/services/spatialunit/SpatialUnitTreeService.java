package fr.siamois.domain.services.spatialunit;

import fr.siamois.dto.entity.SpatialUnitSummaryDTO;
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
    private void buildChildren(TreeNode<SpatialUnitSummaryDTO> parentNode, SpatialUnitSummaryDTO parent, Set<Long> pathIds) {
        List<SpatialUnitSummaryDTO> enfants = spatialUnitService.findDirectChildrensSummaryOf(parent.getId());
        if (enfants == null || enfants.isEmpty()) {
            return;
        }
        for (SpatialUnitSummaryDTO child : enfants) {
            if (pathIds.contains(child.getId())) {
                // Cycle détecté : on l’affiche en grisé et non sélectionnable
                TreeNode<SpatialUnitSummaryDTO> cycle = new CheckboxTreeNode<>("cycle", child, parentNode);
                cycle.setSelectable(false);
                continue;
            }
            TreeNode<SpatialUnitSummaryDTO> childNode = new CheckboxTreeNode<>("SpatialUnit", child, parentNode);
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
    public TreeNode<SpatialUnitSummaryDTO> buildTree() {

        TreeNode<SpatialUnitSummaryDTO> root = new CheckboxTreeNode<>(new SpatialUnitSummaryDTO(), null);
        List<SpatialUnitSummaryDTO> racines = spatialUnitService.findSummaryRootsOf(sessionSettingsBean.getSelectedInstitution().getId());

        for (SpatialUnitSummaryDTO r : racines) {
            TreeNode<SpatialUnitSummaryDTO> rNode = new CheckboxTreeNode<>("SpatialUnit", r, root);
            rNode.setExpanded(false);
            // we memorize path to avoid cycles
            Set<Long> path = new HashSet<>();
            path.add(r.getId());
            buildChildren(rNode, r, path);
        }

        return root;

    }
}
