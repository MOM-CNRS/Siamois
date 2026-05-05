package fr.siamois.ui.custom;

import fr.siamois.dto.entity.AbstractEntityDTO;
import fr.siamois.ui.lazydatamodel.BaseLazyDataModel;
import jakarta.faces.context.FacesContext;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.PrimeFaces;
import org.primefaces.model.FilterMeta;
import org.primefaces.model.LazyDataModel;
import org.primefaces.model.SortMeta;
import org.primefaces.model.TreeNode;
import org.springframework.lang.NonNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static fr.siamois.ui.custom.LazyTreeTable.*;

@Slf4j
public class LoadDataUtils {

    private LoadDataUtils() {
        throw new UnsupportedOperationException("LoadDataUtils can't be instantiated");
    }

    @SuppressWarnings({"rawtypes"})
    public static void loadLazyData(@NonNull LazyTreeTable baseTable, FacesContext context) {
        TreeNode lazyRoot = baseTable.getLazyRoot();
        if (lazyRoot != null) return;

        Map<String, String> params = context.getExternalContext().getRequestParameterMap();
        String clientId = baseTable.getClientId();

        String behaviorEvent = params.get(JAVAX_FACES_BEHAVIOR_EVENT);
        boolean isFilterJSCall = params.containsKey(clientId + EVENT_FILTERING);

        processEventsOnReset(baseTable, behaviorEvent, isFilterJSCall, params, clientId);

        LazyDataModel<? extends AbstractEntityDTO> lazyModel = baseTable.getLazyDataModel();

        if (lazyModel != null) {
            int rows = baseTable.getRows() > 0 ? baseTable.getRows() : 10;
            int first = baseTable.getFirst();

            Map<String, SortMeta> sortMetaMap = baseTable.getSortByAsMap();
            Map<String, FilterMeta> rawFilterMap = baseTable.getFilterByAsMap();

            // Si les filtres de colonnes sont désactivés, on passe une map vide au modèle
            if (!baseTable.isColumnFilteringEnabled()) {
                rawFilterMap = new HashMap<>();
            }

            log.trace("Load appelé avec : {}", lazyModel.getClass().getSimpleName());
            List<? extends AbstractEntityDTO> data = lazyModel.load(first, rows, sortMetaMap, rawFilterMap);

            changeRowCount(baseTable, lazyModel);

            processData(baseTable, data, first, (BaseLazyDataModel<?>) lazyModel);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void processData(LazyTreeTable baseTable, List<? extends AbstractEntityDTO> data, int first, BaseLazyDataModel<?> lazyModel) {
        TreeNode lazyRoot;
        if (data == null) return;

        lazyRoot = new RootTreeNode(baseTable.getRowCount(), first);
        baseTable.setLazyRoot(lazyRoot);

        Set<String> expandedKeys = baseTable.getExpandedRowKeySet();
        Set<Long> ancestorClosure = lazyModel.getAncestorClosure();
        Set<Long> matchIds = lazyModel.getMatchIds();
        boolean filteredMode = ancestorClosure != null;

        for (int i = 0; i < data.size(); i++) {
            AbstractEntityDTO elt = data.get(i);
            ChildTreeNode child = new ChildTreeNode(elt, baseTable.getLoadMethod(), baseTable.getIsLeafMethod());
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
        baseTable.setValue(lazyRoot);
    }

    private static void changeRowCount(LazyTreeTable baseTable, LazyDataModel<? extends AbstractEntityDTO> lazyModel) {
        baseTable.setRowCount(lazyModel.getRowCount());

        if (PrimeFaces.current() != null) {
            PrimeFaces.current().ajax().addCallbackParam("newRowCount", baseTable.getRowCount());
        }
    }

    private static void processEventsOnReset(LazyTreeTable baseTable, String behaviorEvent, boolean isFilterJSCall, Map<String, String> params, String clientId) {
        if (FILTER_BEHAVIOR_EVENT.equals(behaviorEvent) || isFilterJSCall) {
            baseTable.setFirst(0);
        } else if (params.containsKey(clientId + "_pagination")) {
            String firstParam = params.get(clientId + "_first");
            String rowsParam = params.get(clientId + "_rows");

            if (firstParam != null) {
                baseTable.setFirst(Integer.parseInt(firstParam));
            }
            if (rowsParam != null) {
                baseTable.setRows(Integer.parseInt(rowsParam));
            }
        }
    }

}
