package fr.siamois.ui.custom;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.mapper.ActionUnitMapper;
import org.primefaces.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TestActionLazyDataModel extends LazyDataModel<TreeNode<ActionUnitDTO>> {

    private final InstitutionDTO institution;
    private final ActionUnitService actionUnitService;
    private final ActionUnitMapper actionUnitMapper;

    public TestActionLazyDataModel(InstitutionDTO institution, ActionUnitService actionUnitService, ActionUnitMapper actionUnitMapper) {
        this.institution = institution;
        this.actionUnitService = actionUnitService;
        this.actionUnitMapper = actionUnitMapper;
    }

    @Override
    public int getRowCount() {
        return actionUnitService.countRootsInInstitution(institution.getId());
    }

    @Override
    public int count(Map<String, FilterMeta> filterBy) {
        return actionUnitService.countRootsInInstitution(institution.getId());
    }

    private boolean isLeaf(ActionUnitDTO dto) {
        return true;
    }

    private List<ActionUnitDTO> loadChildrens(ActionUnitDTO parent) {
        if (parent == null)
            return new ArrayList<>();
        return new ArrayList<>();
    }

    @Override
    public List<TreeNode<ActionUnitDTO>> load(int first, int pageSize, Map<String, SortMeta> sortBy, Map<String, FilterMeta> filterBy) {
        return actionUnitService.findRootsByInstitution(institution.getId(), first, pageSize)
                .stream()
                .map(actionUnitMapper::convert)
                .map(r -> (TreeNode<ActionUnitDTO>) new LazyDefaultTreeNode<>(r, this::loadChildrens, this::isLeaf))
                .toList();
    }
}
