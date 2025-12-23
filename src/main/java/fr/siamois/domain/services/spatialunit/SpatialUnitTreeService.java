package fr.siamois.domain.services.spatialunit;

import fr.siamois.domain.models.spatialunit.SpatialUnit;
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
    private void buildChildren(TreeNode<SpatialUnit> parentNode, SpatialUnit parent, Set<Long> pathIds) {
        List<SpatialUnit> enfants = spatialUnitService.findDirectChildrensOf(parent.getId());
        if (enfants == null || enfants.isEmpty()) {
            return;
        }
        for (SpatialUnit child : enfants) {
            if (pathIds.contains(child.getId())) {
                // Cycle détecté : on l’affiche en grisé et non sélectionnable
                TreeNode<SpatialUnit> cycle = new CheckboxTreeNode<>("cycle", child, parentNode);
                cycle.setSelectable(false);
                continue;
            }
            TreeNode<SpatialUnit> childNode = new CheckboxTreeNode<>("SpatialUnit", child, parentNode);
            // nouveau "chemin" pour la branche (important avec multi-parents)
            Set<Long> nextPath = new HashSet<>(pathIds);
            nextPath.add(child.getId());
            buildChildren(childNode, child, nextPath);
        }
    }



    public TreeNode<SpatialUnit> buildTree() {

        TreeNode<SpatialUnit> root = new CheckboxTreeNode<>(new SpatialUnit(), null);
        List<SpatialUnit> racines = spatialUnitService.findRootsOf(sessionSettingsBean.getSelectedInstitution().getId());

        for (SpatialUnit r : racines) {
            TreeNode<SpatialUnit> rNode = new CheckboxTreeNode<>("SpatialUnit", r, root);
            rNode.setExpanded(false);
            // on mémorise le chemin (ids vus) pour éviter les cycles
            Set<Long> path = new HashSet<>();
            path.add(r.getId());
            buildChildren(rNode, r, path);
        }

        return root;

    }
}
