package fr.siamois.ui.custom;

import jakarta.faces.context.FacesContext;
import org.primefaces.component.treetable.TreeTable;
import org.primefaces.model.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class LazyTreeTable extends TreeTable {

    private transient TreeNode<?> lazyRoot;

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
            loadLazyData();
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
            loadLazyData();
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
        super.setFirst(first);
        clearCache();
    }

    @Override
    public void setRows(int rows) {
        super.setRows(rows);
        clearCache();
    }

    @Override
    public void encodeBegin(FacesContext context) throws IOException {
        clearCache();
        super.encodeBegin(context);
    }

    // 3. Une méthode de chargement ultra-lisible
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void loadLazyData() {
        if (lazyRoot != null) return;

        FacesContext context = getFacesContext();
        Map<String, String> params = context.getExternalContext().getRequestParameterMap();
        String clientId = getClientId(context);

        if (params.containsKey(clientId + "_pagination")) {
            String firstParam = params.get(clientId + "_first");
            if (firstParam != null) {
                super.setFirst(Integer.parseInt(firstParam));
            }
        }

        LazyDataModel<TreeNode<?>> lazyModel = getLazyDataModel();
        lazyRoot = new DefaultTreeNode<>(null, null);

        if (lazyModel != null) {
            int rows = getRows() > 0 ? getRows() : 10;
            int first = getFirst();

            List<TreeNode<?>> data = lazyModel.load(first, rows, getSortByAsMap(), getFilterByAsMap());

            setRowCount(lazyModel.getRowCount());

            if (data != null) {
                while (lazyRoot.getChildren().size() < first) {
                    lazyRoot.getChildren().add(new DefaultTreeNode(null, null));
                }

                for (TreeNode elt : data) {
                    lazyRoot.getChildren().add(elt);
                }
            }
        }
    }
}