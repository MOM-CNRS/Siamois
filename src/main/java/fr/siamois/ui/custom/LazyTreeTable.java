package fr.siamois.ui.custom;

import fr.siamois.dto.entity.ActionUnitDTO;
import jakarta.faces.context.FacesContext;
import org.primefaces.component.treetable.TreeTable;
import org.primefaces.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LazyTreeTable extends TreeTable {

    private static final Logger log = LoggerFactory.getLogger(LazyTreeTable.class);
    private transient TreeNode lazyRoot;

    enum PropertyKeys {
        lazy,
        rowCount,
        lazyDataModel
    }

    public boolean isLazy() {
        return (boolean) getStateHelper().eval(PropertyKeys.lazy, false);
    }

    public void setLazy(boolean lazy) {
        getStateHelper().put(PropertyKeys.lazy, lazy);
    }

    @SuppressWarnings("unchecked")
    public LazyDataModel<TreeNode<?>> getLazyDataModel() {
        return (LazyDataModel<TreeNode<?>>) getStateHelper().eval(PropertyKeys.lazyDataModel, null);
    }

    public void setLazyDataModel(LazyDataModel<TreeNode<?>> model) {
        getStateHelper().put(PropertyKeys.lazyDataModel, model);
    }

    @Override
    public int getRowCount() {
        if (isLazy()) {
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
            return lazyRoot;
        }
        return super.getValue();
    }

    private void clearCache() {
        if (isLazy()) {
            this.lazyRoot = null;
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
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void loadLazyData() {
        if (lazyRoot != null) return;

        FacesContext context = getFacesContext();
        Map<String, String> params = context.getExternalContext().getRequestParameterMap();
        String clientId = getClientId(context);

        String behaviorEvent = params.get("javax.faces.behavior.event");
        if ("filter".equals(behaviorEvent)) {
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

        LazyDataModel<TreeNode<?>> lazyModel = getLazyDataModel();

        if (lazyModel != null) {
            int rows = getRows() > 0 ? getRows() : 10;
            int first = getFirst();

            Map<String, SortMeta> sortMetaMap = getSortByAsMap();
            Map<String, FilterMeta> rawFilterMap = getFilterByAsMap();

            Map<String, FilterMeta> activeFilters = new HashMap<>();
            if (rawFilterMap != null) {
                for (Map.Entry<String, FilterMeta> entry : rawFilterMap.entrySet()) {
                    Object val = entry.getValue().getFilterValue();
                    if (val instanceof String strVal) {
                        if (strVal.trim().length() >= 3) {
                            activeFilters.put(entry.getKey(), entry.getValue());
                        }
                    } else if (val != null) {
                        activeFilters.put(entry.getKey(), entry.getValue());
                    }
                }
            }

            log.trace("Load appelée avec filtres actifs: {}", activeFilters);

            List<TreeNode<?>> data = lazyModel.load(first, rows, sortMetaMap, activeFilters);

            lazyRoot = new RootTreeNode<>();

            if (activeFilters.isEmpty()) {
                setRowCount(lazyModel.getRowCount());
            } else {
                setRowCount(lazyModel.count(activeFilters));
            }

            if (data != null) {
                while (lazyRoot.getChildren().size() < first) {
                    lazyRoot.getChildren().add(new DefaultTreeNode<>(null, null));
                }

                for (TreeNode elt : data) {
                    lazyRoot.getChildren().add(elt);
                }

                while (lazyRoot.getChildren().size() < getRowCount()) {
                    lazyRoot.getChildren().add(new DefaultTreeNode<>(null, null));
                }
                log.trace("Data generated");
                setValue(lazyRoot);
                log.trace("New root set");
            }

            log.trace("Il doit y avoir {} enfants", getRowCount());
        }
    }

    @Override
    protected void preEncode(FacesContext context) {
        loadLazyData();
        super.preEncode(context);
    }

    @Override
    public boolean isFilteringEnabled() {
        if (isLazy()) {
            FacesContext context = getFacesContext();
            if (context != null && context.getCurrentPhaseId() == jakarta.faces.event.PhaseId.RENDER_RESPONSE) {
                return false;
            }
        }
        return super.isFilteringEnabled();
    }

    @Override
    public void filterAndSort() {
        // Laisser vide, c'est géré manuellement
    }
}