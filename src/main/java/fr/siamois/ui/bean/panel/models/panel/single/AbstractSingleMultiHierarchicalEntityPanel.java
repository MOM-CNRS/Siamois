package fr.siamois.ui.bean.panel.models.panel.single;

import fr.siamois.domain.services.history.HistoryAuditService;
import fr.siamois.ui.bean.dialog.document.DocumentCreationBean;
import fr.siamois.ui.bean.panel.models.panel.single.tab.MultiHierarchyTab;
import fr.siamois.ui.lazydatamodel.BaseLazyDataModel;

import java.io.Serializable;

public abstract class AbstractSingleMultiHierarchicalEntityPanel<T> extends AbstractSingleEntityPanel<T> implements Serializable {

    protected AbstractSingleMultiHierarchicalEntityPanel(String titleCodeOrTitle, String icon, String panelClass, DocumentCreationBean documentCreationBean,
                                                         AbstractSingleEntity.Deps deps, HistoryAuditService historyAuditService) {
        super(titleCodeOrTitle, icon, panelClass, documentCreationBean, deps, historyAuditService);
    }

    public abstract BaseLazyDataModel<T> getLazyDataModelChildren();

    public abstract BaseLazyDataModel<T> getLazyDataModelParents();

    @Override
    public void init() {
        // Init tabs
        MultiHierarchyTab multiHierTab = new MultiHierarchyTab(
                "panel.tab.hierarchy",
                this.getIcon(),
                "hierarchyTab");

        tabs.add(2, multiHierTab);
    }

}


