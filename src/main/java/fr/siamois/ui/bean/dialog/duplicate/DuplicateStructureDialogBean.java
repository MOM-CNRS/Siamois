package fr.siamois.ui.bean.dialog.duplicate;

import fr.siamois.domain.models.exceptions.recordingunit.RecordingUnitIdentifierAlreadyExistsException;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.recordingunit.RecordingUnitStructureDuplicationResult;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.dialog.newunit.NewUnitContext;
import fr.siamois.ui.bean.dialog.newunit.UnitKind;
import fr.siamois.ui.table.viewmodel.RecordingUnitTableViewModel;
import fr.siamois.utils.MessageUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.PrimeFaces;
import org.primefaces.model.DefaultTreeNode;
import org.primefaces.model.TreeNode;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Backs the "Dupliquer la structure" modal: builds the checkbox tree of an entity and its
 * descendants, collects how many exemplars the user wants, and dispatches the actual duplication
 * to {@link RecordingUnitService#duplicateStructure}, then reflects the result in the table that
 * opened it.
 */
@Slf4j
@Scope("session")
@Component
@Getter
@Setter
public class DuplicateStructureDialogBean implements Serializable {

    private static final int MAX_NODES = 500;

    private final transient RecordingUnitService recordingUnitService;
    private final transient LangBean langBean;

    private transient RecordingUnitDTO root;
    private transient RecordingUnitTableViewModel callerTable;

    /** Bound to {@code p:tree}'s {@code value}: PrimeFaces treats this as an invisible container
     *  and never renders it, only its children — so the duplicated entity itself must be a CHILD
     *  of this node, not this node, or it silently disappears from the tree. */
    private transient TreeNode<RecordingUnitDTO> rootNode;
    /** The visible node for the duplicated entity itself (child of {@link #rootNode}). */
    private transient TreeNode<RecordingUnitDTO> ruNode;
    private transient List<TreeNode<RecordingUnitDTO>> selectedNodes = new ArrayList<>();

    private Integer count = 1;

    public DuplicateStructureDialogBean(RecordingUnitService recordingUnitService, LangBean langBean) {
        this.recordingUnitService = recordingUnitService;
        this.langBean = langBean;
    }

    /**
     * Opens the modal for {@code ru}: builds its descendant tree (all checked by default) and
     * shows the dialog. {@code table} is called back on {@link #confirm()} to reflect the newly
     * created entities.
     */
    public void openFor(RecordingUnitDTO ru, RecordingUnitTableViewModel table) {
        this.root = ru;
        this.callerTable = table;
        this.count = 1;
        this.rootNode = buildTree(ru);
        // Nothing pre-selected: duplicating just the clicked unit is the common case, and
        // opting descendants in is safer than having to opt them out.
        this.selectedNodes = new ArrayList<>();
        applySelection(ruNode, false);

        PrimeFaces.current().ajax().update("duplicateStructureForm");
        PrimeFaces.current().executeScript("PF('duplicateStructureDiag').show()");
    }

    private TreeNode<RecordingUnitDTO> buildTree(RecordingUnitDTO ru) {
        TreeNode<RecordingUnitDTO> invisibleRoot = new DefaultTreeNode<>();
        TreeNode<RecordingUnitDTO> node = new DefaultTreeNode<>(ru, invisibleRoot);
        node.setExpanded(true);
        // The root is always duplicated regardless of its checkbox state, so it isn't offered one.
        node.setSelectable(false);
        Set<Long> visited = new HashSet<>();
        visited.add(ru.getId());
        buildChildren(node, ru.getId(), visited);
        this.ruNode = node;
        return invisibleRoot;
    }

    private void buildChildren(TreeNode<RecordingUnitDTO> parentNode, Long parentId, Set<Long> visited) {
        if (visited.size() >= MAX_NODES) return;
        for (RecordingUnitDTO child : recordingUnitService.findAllByParentRecordingUnit(parentId)) {
            if (!visited.add(child.getId())) continue;
            TreeNode<RecordingUnitDTO> childNode = new DefaultTreeNode<>(child, parentNode);
            childNode.setExpanded(true);
            if (visited.size() >= MAX_NODES) return;
            buildChildren(childNode, child.getId(), visited);
        }
    }

    /** Every node under {@code node}, excluding {@code node} itself (the root has no checkbox). */
    private List<TreeNode<RecordingUnitDTO>> collectDescendants(TreeNode<RecordingUnitDTO> node) {
        List<TreeNode<RecordingUnitDTO>> all = new ArrayList<>();
        for (TreeNode<RecordingUnitDTO> child : node.getChildren()) {
            all.add(child);
            all.addAll(collectDescendants(child));
        }
        return all;
    }

    /**
     * Check/uncheck every descendant of {@code node}. PrimeFaces renders each checkbox from the
     * TreeNode's own {@code selected} flag, so replacing {@link #selectedNodes} alone leaves the
     * rendered tree unchanged — the flags have to be set too. {@code partialSelected} is cleared
     * as well, otherwise ancestors keep the indeterminate "some children checked" state.
     */
    private void applySelection(TreeNode<RecordingUnitDTO> node, boolean selected) {
        if (node == null) return;
        node.setPartialSelected(false);
        for (TreeNode<RecordingUnitDTO> child : node.getChildren()) {
            child.setSelected(selected);
            applySelection(child, selected);
        }
    }

    public boolean isRoot(RecordingUnitDTO ru) {
        return root != null && Objects.equals(root.getId(), ru.getId());
    }

    /** Descendant count (root excluded, it has no checkbox to toggle). */
    private int countDescendants(TreeNode<RecordingUnitDTO> node) {
        int total = 0;
        for (TreeNode<RecordingUnitDTO> child : node.getChildren()) {
            total += 1 + countDescendants(child);
        }
        return total;
    }

    public boolean isAllSelected() {
        if (ruNode == null) return false;
        int total = countDescendants(ruNode);
        // With no descendants at all there is nothing to check, so "select all" is the honest label.
        if (total == 0) return false;
        return selectedDescendantCount() >= total;
    }

    public String getToggleAllLabel() {
        return isAllSelected() ? langBean.msg("duplicateStructure.deselectAll") : langBean.msg("duplicateStructure.selectAll");
    }

    public void toggleAll() {
        if (ruNode == null) return;
        boolean select = !isAllSelected();
        selectedNodes = select ? collectDescendants(ruNode) : new ArrayList<>();
        applySelection(ruNode, select);
    }

    /**
     * Selected descendants of the root, root excluded. The root itself is never toggleable and is
     * not guaranteed to be reflected in {@link #selectedNodes} by the checkbox tree (it isn't
     * selectable), so it's always counted separately, both here and in {@link #confirm()}.
     */
    private int selectedDescendantCount() {
        if (selectedNodes == null) return 0;
        return (int) selectedNodes.stream()
                .filter(n -> n.getData() != null && !isRoot(n.getData()))
                .count();
    }

    public String getSummary() {
        int selected = 1 + selectedDescendantCount();
        int copies = Math.max(1, count);
        return langBean.msg("duplicateStructure.summary", selected, copies, selected * copies);
    }

    public void confirm() {
        if (root == null || callerTable == null) {
            return;
        }

        Set<Long> selectedIds = new HashSet<>();
        for (TreeNode<RecordingUnitDTO> node : selectedNodes) {
            RecordingUnitDTO data = node.getData();
            if (data != null && !Objects.equals(data.getId(), root.getId())) {
                selectedIds.add(data.getId());
            }
        }

        RecordingUnitStructureDuplicationResult result;
        try {
            result = recordingUnitService.duplicateStructure(root, selectedIds, count);
        } catch (RecordingUnitIdentifierAlreadyExistsException e) {
            log.error("Recording-unit structure duplication failed", e);
            MessageUtils.displayWarnMessage(langBean, "duplicateStructure.error.identifierExists", e.getIdentifier());
            return;
        } catch (RuntimeException e) {
            log.error("Recording-unit structure duplication failed", e);
            MessageUtils.displayWarnMessage(langBean, "duplicateStructure.error");
            return;
        }

        insertIntoTable(result);

        PrimeFaces.current().executeScript("PF('duplicateStructureDiag').hide()");
        int copies = Math.max(1, count);
        if (copies == 1) {
            MessageUtils.displayInfoMessage(langBean, "duplicateStructure.success.single",
                    result.rootCopies().get(0).getFullIdentifier());
        } else {
            MessageUtils.displayInfoMessage(langBean, "duplicateStructure.success.multiple",
                    root.getFullIdentifier(), copies);
        }

        // callerTable is kept around: the "clear highlight" remote command fired a few
        // seconds later (see the dialog's confirm button) still needs it.
        root = null;
        rootNode = null;
        ruNode = null;
        selectedNodes = new ArrayList<>();
    }

    private void insertIntoTable(RecordingUnitStructureDuplicationResult result) {
        List<Long> highlightIds = result.allCreated().stream().map(RecordingUnitDTO::getId).toList();
        callerTable.markRecentlyCreated(highlightIds);

        List<RecordingUnitDTO> toInsert = callerTable.isTreeMode() ? result.rootCopies() : result.allCreated();

        // Insert in reverse so the final top-to-bottom order matches copy 1, 2, ... N.
        for (int i = toInsert.size() - 1; i >= 0; i--) {
            RecordingUnitDTO copy = toInsert.get(i);
            NewUnitContext ctx = NewUnitContext.builder()
                    .kindToCreate(UnitKind.RECORDING)
                    .trigger(NewUnitContext.Trigger.cell(UnitKind.RECORDING, root.getId(), "parents"))
                    .insertPolicy(NewUnitContext.UiInsertPolicy.builder()
                            .listInsert(NewUnitContext.ListInsert.TOP)
                            .treeInsert(NewUnitContext.TreeInsert.SIBLING_BELOW)
                            .build())
                    .build();
            callerTable.onAnyEntityCreated(copy, ctx);
        }
    }
}
