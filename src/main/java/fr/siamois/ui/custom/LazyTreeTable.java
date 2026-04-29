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
        return getLazyDataModel().getLazyRoot();
    }

    @SuppressWarnings("unchecked")
    public void setLazyRoot(TreeNode lazyRoot) {
        BaseLazyDataModel baseLazyDataModel = getLazyDataModel();
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
    public BaseLazyDataModel<? extends AbstractEntityDTO> getLazyDataModel() {
        return (BaseLazyDataModel<? extends AbstractEntityDTO>) getStateHelper().eval(PropertyKeys.lazyDataModel, null);
    }

    public void setLazyDataModel(BaseLazyDataModel<? extends AbstractEntityDTO> model) {
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void loadLazyData() {
        TreeNode lazyRoot = getLazyRoot();
        if (lazyRoot != null) return;

        FacesContext context = getFacesContext();
        Map<String, String> params = context.getExternalContext().getRequestParameterMap();
        String clientId = getClientId(context);

        String behaviorEvent = params.get("javax.faces.behavior.event");
        boolean isFilterJSCall = params.containsKey(clientId + "_filtering");

        // 1. Gestion des événements de Reset (retour page 1 sur filtre) ou Pagination
        if ("filter".equals(behaviorEvent) || isFilterJSCall) {
            super.setFirst(0);
        } else if (params.containsKey(clientId + "_pagination")) {
            String firstParam = params.get(clientId + "_first");
            String rowsParam = params.get(clientId + "_rows");

            if (firstParam != null) {
                super.setFirst(Integer.parseInt(firstParam));
            }
            if (rowsParam != null) {
                super.setRows(Integer.parseInt(rowsParam));
            }
        }

        LazyDataModel<? extends AbstractEntityDTO> lazyModel = getLazyDataModel();

        if (lazyModel != null) {
            int rows = getRows() > 0 ? getRows() : 10;
            int first = getFirst();

            Map<String, SortMeta> sortMetaMap = getSortByAsMap();
            Map<String, FilterMeta> rawFilterMap = getFilterByAsMap();

            // Si les filtres de colonnes sont désactivés, on passe une map vide au modèle
            if (!isColumnFilteringEnabled()) {
                rawFilterMap = new HashMap<>();
            }

            log.trace("Load appelé avec : {}", lazyModel.getClass().getSimpleName());
            List<? extends AbstractEntityDTO> data = lazyModel.load(first, rows, sortMetaMap, rawFilterMap);

            setRowCount(lazyModel.getRowCount());

            if (PrimeFaces.current() != null) {
                PrimeFaces.current().ajax().addCallbackParam("newRowCount", getRowCount());
            }

            if (data != null) {
                lazyRoot = new RootTreeNode(getRowCount(), first);
                setLazyRoot(lazyRoot);

                Set<String> expandedKeys = getExpandedRowKeySet();
                Set<Long> ancestorClosure = (lazyModel instanceof BaseLazyDataModel<?> blm)
                        ? blm.getAncestorClosure()
                        : null;
                Set<Long> matchIds = (lazyModel instanceof BaseLazyDataModel<?> blm2)
                        ? blm2.getMatchIds()
                        : null;
                boolean filteredMode = ancestorClosure != null;

                for (int i = 0; i < data.size(); i++) {
                    AbstractEntityDTO elt = data.get(i);
                    ChildTreeNode child = new ChildTreeNode(elt, getLoadMethod(), getIsLeafMethod());
                    child.setAncestorClosure(ancestorClosure);
                    child.setMatchIds(matchIds);
                    String rowKey = String.valueOf(first + i);
                    child.setRowKey(rowKey);
                    // In filtered mode, auto-expand only pure ancestors so a
                    // deeper match becomes visible. A top-level row that is
                    // itself a match stays collapsed — the user can expand it
                    // to explore its children.
                    boolean topLevelIsMatch = filteredMode && matchIds != null
                            && elt != null && matchIds.contains(elt.getId());
                    boolean autoExpand = filteredMode && !topLevelIsMatch;
                    child.setExpanded(autoExpand || expandedKeys.contains(rowKey));
                    child.setParent(lazyRoot);
                    lazyRoot.getChildren().add(child);
                }
                setValue(lazyRoot);
            }
        }
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
            String behaviorEvent = params.get("javax.faces.behavior.event");
            boolean isFilterEvent = "filter".equals(behaviorEvent)
                    || params.containsKey(clientId + "_filtering");
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
    private Set<String> getExpandedRowKeySet() {
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
            String behaviorEvent = params.get("javax.faces.behavior.event");
            boolean isFilterEvent = "filter".equals(behaviorEvent)
                    || params.containsKey(clientId + "_filtering");
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
            String behaviorEvent = params.get("javax.faces.behavior.event");
            if ("filter".equals(behaviorEvent) || params.containsKey(clientId + "_filtering")) {
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