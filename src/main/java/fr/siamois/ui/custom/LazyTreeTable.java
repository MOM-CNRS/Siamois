package fr.siamois.ui.custom;

import fr.siamois.dto.entity.AbstractEntityDTO;
import fr.siamois.ui.lazydatamodel.BaseLazyDataModel;
import jakarta.faces.context.FacesContext;
import org.primefaces.PrimeFaces;
import org.primefaces.component.treetable.TreeTable;
import org.primefaces.model.FilterMeta;
import org.primefaces.model.LazyDataModel;
import org.primefaces.model.SortMeta;
import org.primefaces.model.TreeNode;
import org.primefaces.util.Callbacks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.util.*;

@SuppressWarnings("rawtypes")
public class LazyTreeTable extends TreeTable {

    private static final Logger log = LoggerFactory.getLogger(LazyTreeTable.class);
    public static final String JAVAX_FACES_BEHAVIOR_EVENT = "javax.faces.behavior.event";
    public static final String EVENT_FILTERING = "_filtering";
    public static final String FILTER_BEHAVIOR_EVENT = "filter";
    private boolean blockFiltering = false;

    enum PropertyKeys {
        lazy,
        rowCount,
        lazyDataModel,
        isLeafMethod,
        loadMethod,
        expandedRowKeys,
        columnFilteringEnabled
    }

    @Nullable
    public TreeNode getLazyRoot() {
        BaseLazyDataModel<AbstractEntityDTO> model = getLazyDataModel();
        if (model == null) return null;
        return model.getLazyRoot();
    }

    @SuppressWarnings("unchecked")
    public void setLazyRoot(TreeNode lazyRoot) {
        BaseLazyDataModel baseLazyDataModel = getLazyDataModel();
        if (baseLazyDataModel == null) return;
        baseLazyDataModel.setLazyRoot(lazyRoot);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isColumnFilteringEnabled() {
        return (Boolean) getStateHelper().eval(PropertyKeys.columnFilteringEnabled, false);
    }

    public void setColumnFilteringEnabled(boolean enabled) {
        getStateHelper().put(PropertyKeys.columnFilteringEnabled, enabled);
    }

    public void setIsLeafMethod(Callbacks.SerializableFunction<AbstractEntityDTO, Boolean> isLeafMethod) {
        getStateHelper().put(PropertyKeys.isLeafMethod, isLeafMethod);
    }

    public void setLoadMethod(Callbacks.SerializableFunction<AbstractEntityDTO, List<AbstractEntityDTO>> loadMethod) {
        getStateHelper().put(PropertyKeys.loadMethod, loadMethod);
    }

    @SuppressWarnings("unchecked")
    public Callbacks.SerializableFunction<AbstractEntityDTO, Boolean> getIsLeafMethod() {
        return (Callbacks.SerializableFunction<AbstractEntityDTO, Boolean>) getStateHelper().eval(PropertyKeys.isLeafMethod, null);
    }

    @SuppressWarnings("unchecked")
    public Callbacks.SerializableFunction<AbstractEntityDTO, List<AbstractEntityDTO>> getLoadMethod() {
        return (Callbacks.SerializableFunction<AbstractEntityDTO, List<AbstractEntityDTO>>) getStateHelper().eval(PropertyKeys.loadMethod, null);
    }

    public boolean isLazy() {
        return (boolean) getStateHelper().eval(PropertyKeys.lazy, false);
    }

    public void setLazy(boolean lazy) {
        getStateHelper().put(PropertyKeys.lazy, lazy);
    }

    @SuppressWarnings("unchecked")
    public BaseLazyDataModel<AbstractEntityDTO> getLazyDataModel() {
        return (BaseLazyDataModel<AbstractEntityDTO>) getStateHelper().eval(PropertyKeys.lazyDataModel, null);
    }

    public void setLazyDataModel(BaseLazyDataModel<AbstractEntityDTO> model) {
        getStateHelper().put(PropertyKeys.lazyDataModel, model);
    }

    @Override
    public int getRowCount() {
        if (isLazy()) {
            // Prefer the live count tracked on the displayed tree: mutations
            // (insertAtRoot, insertParentAndReparent, ...) bump it directly via
            // the mutator's bumpRootCount, while the state helper would still
            // show the count captured at the last full load.
            TreeNode root = getLazyRoot();
            if (root != null && root.getChildren() instanceof RootChildList<?> list) {
                return list.size();
            }
            return (Integer) getStateHelper().eval(PropertyKeys.rowCount, 0);
        }
        return super.getRowCount();
    }

    public void setRowCount(int rowCount) {
        getStateHelper().put(PropertyKeys.rowCount, rowCount);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public TreeNode getValue() {
        if (isLazy()) {
            if (getLazyRoot() == null) {
                loadLazyData();
            }
            return getLazyRoot();
        }
        return super.getValue();
    }

    private void clearCache() {
        if (isLazy()) {
            setLazyRoot(null);
            this.setValue(null);
        }
    }

    @Override
    public void setFirst(int first) {
        if (first != this.getFirst()) {
            super.setFirst(first);
            clearCache();
        }
    }

    @Override
    public void setRows(int rows) {
        if (rows != this.getRows()) {
            super.setRows(rows);
            clearCache();
        }
    }

    @Override
    public void encodeBegin(FacesContext context) throws IOException {
        super.encodeBegin(context);
        this.blockFiltering = true;
    }

    @Override
    public void encodeEnd(FacesContext context) throws IOException {
        this.blockFiltering = true;

        try {
            rebuildLoadedRowKeys();
            super.encodeEnd(context);
        } catch (RuntimeException e) {
            log.error(e.getMessage(), e);
        } finally {
            this.blockFiltering = false;
        }
    }

    /**
     * Reassign row keys to every loaded node so they match each node's current
     * position in the tree. Mutations like {@code insertParentAndReparent}
     * shuffle nodes around without touching their previously-computed
     * {@code rowKey}, and PrimeFaces reads that key directly during encoding —
     * a stale or null key produces things like "null_0", which then explode
     * inside {@code UITree.findTreeNode}'s {@code Integer.parseInt}.
     *
     * Walks only what is already loaded in memory: the page-sized actual
     * children of the root, plus the subtrees of any expanded {@link ChildTreeNode}.
     * Unloaded lazy children are left untouched so we never trigger a DB fetch
     * here.
     */
    private void rebuildLoadedRowKeys() {
        if (!isLazy()) return;
        TreeNode lazyRoot = getLazyRoot();
        if (lazyRoot == null) return;
        if (!(lazyRoot.getChildren() instanceof RootChildList<?> list)) return;

        int first = getFirst();
        int idx = 0;
        for (TreeNode<?> child : list) {
            child.setRowKey(String.valueOf(first + idx));
            rebuildSubtreeRowKeys(child);
            idx++;
        }
    }

    private void rebuildSubtreeRowKeys(TreeNode<?> node) {
        if (node instanceof ChildTreeNode<?> ctn && !ctn.isLoaded()) return;
        String parentKey = node.getRowKey();
        if (parentKey == null) return;
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TreeNode<?> child = node.getChildren().get(i);
            child.setRowKey(parentKey + "_" + i);
            rebuildSubtreeRowKeys(child);
        }
    }

    private void loadLazyData() {
        LoadDataUtils.loadLazyData(this, getFacesContext());
    }

    @Override
    public void filterAndSort() {
        log.trace("Filter and sort");
    }

    @Override
    public boolean isFilteringCurrentlyActive() {
        if (isLazy() && blockFiltering) return false;
        return super.isFilteringCurrentlyActive();
    }

    @Override
    public boolean isFilteringEnabled() {
        return super.isFilteringEnabled();
    }

    @Override
    public TreeNode getFilteredValue() {
        if (isLazy()) {
            return getValue();
        }
        return super.getFilteredValue();
    }

    @Override
    public void setFilteredValue(TreeNode<?> filteredValue) {
        if (!isLazy()) {
            super.setFilteredValue(filteredValue);
        }
    }

    @Override
    protected void preEncode(FacesContext context) {
        if (isLazy()) {
            Map<String, String> params = context.getExternalContext().getRequestParameterMap();
            String clientId = getClientId(context);
            String behaviorEvent = params.get(JAVAX_FACES_BEHAVIOR_EVENT);
            boolean isFilterEvent = FILTER_BEHAVIOR_EVENT.equals(behaviorEvent)
                    || params.containsKey(clientId + EVENT_FILTERING);
            boolean isSortEvent = "sort".equals(behaviorEvent)
                    || params.containsKey(clientId + "_sorting");
            boolean isPaginationEvent = params.containsKey(clientId + "_pagination");

            // Only data-changing events should throw away the existing tree.
            // Otherwise we keep the current `lazyRoot` so manual mutations
            // (insert as child / parent / sibling) survive across renders.
            if (isFilterEvent) {
                setLazyRoot(null);
                super.setFirst(0);
                getExpandedRowKeySet().clear();
            } else if (isSortEvent || isPaginationEvent) {
                setLazyRoot(null);
            }

            if (getLazyRoot() == null) {
                loadLazyData();
            }
        }
        super.preEncode(context);
    }

    @SuppressWarnings("unchecked")
    Set<String> getExpandedRowKeySet() {
        Set<String> keys = (Set<String>) getStateHelper().eval(PropertyKeys.expandedRowKeys, null);
        if (keys == null) {
            keys = new LinkedHashSet<>();
            getStateHelper().put(PropertyKeys.expandedRowKeys, keys);
        }
        return keys;
    }

    @Override
    public void processDecodes(FacesContext context) {
        super.processDecodes(context);
        if (isLazy()) {
            Map<String, String> params = context.getExternalContext().getRequestParameterMap();
            String clientId = getClientId(context);
            String behaviorEvent = params.get(JAVAX_FACES_BEHAVIOR_EVENT);
            boolean isFilterEvent = FILTER_BEHAVIOR_EVENT.equals(behaviorEvent)
                    || params.containsKey(clientId + EVENT_FILTERING);
            if (isFilterEvent) {
                super.setFirst(0);
            }
        }
    }

    @Override
    public void decode(FacesContext context) {
        super.decode(context);
        if (isLazy()) {
            Map<String, String> params = context.getExternalContext().getRequestParameterMap();
            String clientId = getClientId(context);
            String expandKey   = params.get(clientId + "_expand");
            String collapseKey = params.get(clientId + "_collapse");
            if (expandKey   != null) getExpandedRowKeySet().add(expandKey);
            if (collapseKey != null) getExpandedRowKeySet().remove(collapseKey);
        }
    }

    @Override
    public void calculateFirst() {
        if (isLazy()) {
            FacesContext context = getFacesContext();
            Map<String, String> params = context.getExternalContext().getRequestParameterMap();
            String clientId = getClientId(context);
            String behaviorEvent = params.get(JAVAX_FACES_BEHAVIOR_EVENT);
            if (FILTER_BEHAVIOR_EVENT.equals(behaviorEvent) || params.containsKey(clientId + EVENT_FILTERING)) {
                super.setFirst(0);
                return;
            }
        }
        super.calculateFirst();
    }

    /**
     * Align the row keys produced during the visit phase with the absolute row
     * keys we wrote into the rendered HTML in {@link #loadLazyData()} (`first + i`).
     *
     * Without this, PrimeFaces' visit walks children using just the iteration
     * index (0..pageSize-1). On page 2 the visit keys would be "0".."9" while
     * the DOM carries "10".."19" — visit never finds the clicked button and
     * the action is silently dropped.
     */
    @Override
    protected String childRowKey(String parentRowKey, int i) {
        if (parentRowKey == null && isLazy()) {
            return String.valueOf(getFirst() + i);
        }
        return super.childRowKey(parentRowKey, i);
    }

    @Override
    public String getStyleClass() {
        String style = super.getStyleClass();
        if (!isColumnFilteringEnabled()) {
            style = style + " filter-table-column-hidden";
        }
        return style;
    }
}