package fr.siamois.ui.bean.panel.models.panel.single;

import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.ui.bean.panel.models.panel.single.tab.MultiHierarchyTab;
import fr.siamois.ui.table.EntityTableViewModel;
import org.primefaces.model.menu.DefaultMenuModel;
import org.primefaces.model.menu.MenuModel;
import org.springframework.context.ApplicationContext;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class AbstractSingleMultiHierarchicalEntityPanel<T extends TraceableEntity>
        extends AbstractSingleEntityPanel<T> implements Serializable {



    /*
    Find all the direct parents of a given unit
     */
    abstract List<T> findDirectParentsOf(Long id);



    protected List<List<T>> findAllParentPathsToRoot() {
        List<List<T>> allPaths = new ArrayList<>();
        List<T> parents = findDirectParentsOf(idunit);

        if (parents == null || parents.isEmpty()) {
            return allPaths;
        }

        for (T parent : parents) {
            List<T> currentPath = new ArrayList<>();
            currentPath.add(parent);
            findPathsRecursively(parent.getId(), currentPath, allPaths);
        }

        return allPaths;
    }

    private void findPathsRecursively(Long unitId, List<T> currentPath, List<List<T>> allPaths) {
        List<T> parents = findDirectParentsOf(unitId);

        if (parents == null || parents.isEmpty()) {
            allPaths.add(new ArrayList<>(currentPath));
        } else {
            for (T parent : parents) {
                currentPath.add(parent);
                findPathsRecursively(parent.getId(), currentPath, allPaths);
                currentPath.remove(currentPath.size() - 1);
            }
        }
    }

    @Override
    public List<MenuModel> getAllParentBreadcrumbModels() {
        List<List<T>> allPaths = findAllParentPathsToRoot();
        List<MenuModel> breadcrumbModels = new ArrayList<>();
        T currentUnit = findUnitById(idunit);

        if (allPaths.isEmpty()) {
            MenuModel breadcrumbModel = new DefaultMenuModel();
            breadcrumbModel.getElements().add(createHomeItem());
            breadcrumbModel.getElements().add(createRootTypeItem());

            if (currentUnit != null) {
                breadcrumbModel.getElements().add(createUnitItem(currentUnit));
            }

            breadcrumbModels.add(breadcrumbModel);
        } else {
            for (List<T> path : allPaths) {
                MenuModel breadcrumbModel = new DefaultMenuModel();
                breadcrumbModel.getElements().add(createHomeItem());
                breadcrumbModel.getElements().add(createRootTypeItem());

                List<T> reversedPath = new ArrayList<>(path);
                Collections.reverse(reversedPath);

                for (T unit : reversedPath) {
                    breadcrumbModel.getElements().add(createUnitItem(unit));
                }

                if (currentUnit != null) {
                    breadcrumbModel.getElements().add(createUnitItem(currentUnit));
                }

                breadcrumbModels.add(breadcrumbModel);
            }
        }

        return breadcrumbModels;
    }


    protected AbstractSingleMultiHierarchicalEntityPanel(
            String titleCodeOrTitle,
            String icon,
            String panelClass,
            ApplicationContext context) {
        super(titleCodeOrTitle, icon, panelClass, context);
    }

    public abstract EntityTableViewModel<T, Long> getChildTableModel();

    @Override
    public void init() {
        // Init tabs
        MultiHierarchyTab multiHierTab = new MultiHierarchyTab(
                "panel.tab.hierarchy",
                this.getIcon(),
                "hierarchyTab",
                getChildTableModel());

        tabs.add(2, multiHierTab);
    }

}


