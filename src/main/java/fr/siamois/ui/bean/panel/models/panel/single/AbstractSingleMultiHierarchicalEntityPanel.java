package fr.siamois.ui.bean.panel.models.panel.single;

import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.ui.bean.panel.models.panel.single.tab.MultiHierarchyTab;
import fr.siamois.ui.lazydatamodel.BaseLazyDataModel;
import fr.siamois.ui.table.EntityTableViewModel;
import fr.siamois.ui.table.SpatialUnitTableViewModel;
import org.springframework.context.ApplicationContext;

import java.io.Serializable;

public abstract class AbstractSingleMultiHierarchicalEntityPanel<T extends TraceableEntity>
        extends AbstractSingleEntityPanel<T> implements Serializable {

    protected AbstractSingleMultiHierarchicalEntityPanel(
            String titleCodeOrTitle,
            String icon,
            String panelClass,
            ApplicationContext context) {
        super(titleCodeOrTitle, icon, panelClass, context);
    }


    public abstract EntityTableViewModel<T, Long> getParentTableModel();
    public abstract EntityTableViewModel<T, Long> getChildTableModel();

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


