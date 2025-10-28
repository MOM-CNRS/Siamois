package fr.siamois.ui.bean.panel.models.panel.single;

import fr.siamois.domain.services.history.HistoryAuditService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.FieldService;
import fr.siamois.ui.bean.dialog.document.DocumentCreationBean;
import fr.siamois.ui.bean.panel.models.panel.single.tab.MultiHierarchyTab;
import fr.siamois.ui.lazydatamodel.BaseLazyDataModel;
import org.springframework.context.ApplicationContext;

import java.io.Serializable;

public abstract class AbstractSingleMultiHierarchicalEntityPanel<T> extends AbstractSingleEntityPanel<T> implements Serializable {

    protected AbstractSingleMultiHierarchicalEntityPanel(
            String titleCodeOrTitle,
            String icon,
            String panelClass,
            ApplicationContext context) {
        super(titleCodeOrTitle, icon, panelClass, context);
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


