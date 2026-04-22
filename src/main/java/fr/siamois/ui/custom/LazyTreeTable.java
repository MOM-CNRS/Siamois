package fr.siamois.ui.custom;

import fr.siamois.dto.entity.AbstractEntityDTO;
import jakarta.faces.context.FacesContext;
import org.primefaces.component.treetable.TreeTable;
import org.primefaces.model.*;
import org.primefaces.util.Callbacks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

@SuppressWarnings("rawtypes")
public class LazyTreeTable extends TreeTable {

    private static final Logger log = LoggerFactory.getLogger(LazyTreeTable.class);
    private transient TreeNode lazyRoot;
    private transient boolean blockFiltering = false;

    enum PropertyKeys {
        lazy,
        rowCount,
        lazyDataModel,
        isLeafMethod,
        loadMethod;
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
    public LazyDataModel<? extends AbstractEntityDTO> getLazyDataModel() {
        return (LazyDataModel<? extends AbstractEntityDTO>) getStateHelper().eval(PropertyKeys.lazyDataModel, null);
    }

    public void setLazyDataModel(LazyDataModel<? extends AbstractEntityDTO> model) {
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
            if (lazyRoot == null) {
                loadLazyData();
            }
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
        this.blockFiltering = true;
    }

    @Override
    public void encodeEnd(FacesContext context) throws IOException {
        this.blockFiltering = true;

        try {
            super.encodeEnd(context);
        } catch (RuntimeException e) {
            log.error(e.getMessage(), e);
        } finally {
            this.blockFiltering = false;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void loadLazyData() {
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
            Map<String, FilterMeta> activeFilters = new HashMap<>();
            Map<String, SortMeta> activeSorts = new HashMap<>();

            if (rawFilterMap != null) {
                for (Map.Entry<String, FilterMeta> entry : rawFilterMap.entrySet()) {
                    if ("globalFilter".equals(entry.getKey())) {
                        continue;
                    }

                    Object val = entry.getValue().getFilterValue();
                    if (val instanceof String strVal) {
                        if (!strVal.trim().isEmpty()) {
                            activeFilters.put(entry.getValue().getFilterBy().getValue(context.getELContext()), entry.getValue());
                        }
                    } else if (val != null) {
                        activeFilters.put(entry.getKey(), entry.getValue());
                    }
                }
            }

            String globalVal = null;

            if (rawFilterMap != null && rawFilterMap.containsKey("globalFilter")) {
                globalVal = (String) rawFilterMap.get("globalFilter").getFilterValue();
            }

            String globalFilterParam = clientId + ":globalFilter";
            if ((globalVal == null || globalVal.trim().isEmpty()) && params.containsKey(globalFilterParam)) {
                globalVal = params.get(globalFilterParam);
            }

            if (globalVal != null && globalVal.trim().length() >= 3) {
                FilterMeta globalMeta = FilterMeta.builder()
                        .field("globalFilter")
                        .filterValue(globalVal.trim())
                        .matchMode(MatchMode.GLOBAL)
                        .build();
                activeFilters.put("globalFilter", globalMeta);
            }

            log.trace("Load appelée avec filtres actifs: {}", activeFilters);
            for (Map.Entry<String, FilterMeta> entry : activeFilters.entrySet()) {
                log.trace("\tFiltre : {} : {}", entry.getKey(), entry.getValue().getFilterValue());
            }

            for (Map.Entry<String, SortMeta> entry : sortMetaMap.entrySet()) {
                if (!entry.getValue().getOrder().isUnsorted()) {
                    activeSorts.put(entry.getValue().getSortBy().getValue(context.getELContext()),  entry.getValue());
                }
            }

            log.trace("Load appelée avec tris actifs: {}", activeSorts);
            for (Map.Entry<String, SortMeta> entry : activeSorts.entrySet()) {
                log.trace("\tTri : {} : {}", entry.getKey(), entry.getValue());
            }

            List<? extends AbstractEntityDTO> data = lazyModel.load(first, rows, activeSorts, activeFilters);

            if (activeFilters.isEmpty()) {
                setRowCount(lazyModel.getRowCount());
            } else {
                setRowCount(lazyModel.count(activeFilters));
            }

            if (data != null) {
                lazyRoot = new RootTreeNode(getRowCount(), first);
                for (AbstractEntityDTO elt : data) {
                    ChildTreeNode child = new ChildTreeNode(elt, getLoadMethod(), getIsLeafMethod());
                    child.setParent(lazyRoot);
                    lazyRoot.getChildren().add(child);
                }
                log.trace("Data generated");
                setValue(lazyRoot);

                log.trace("New root set");
            }

            log.trace("Il doit y avoir {} enfants", getRowCount());
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
        // NE PAS bloquer ici — nécessaire pour l'initialisation du widget JS dans encodeEnd
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
            this.lazyRoot = null;
        }
        super.preEncode(context);
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
}