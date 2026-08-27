package fr.siamois.ui.bean.panel.models.panel.single.tab;

import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.ui.table.viewmodel.ContainerTableViewModel;
import lombok.Data;

@Data
public class ContainerTab extends EntityListTab<ContainerDTO> {

    public ContainerTab(String titleCode, String icon, String id,
                        ContainerTableViewModel tableModel, Integer count) {
        super(titleCode, icon, id, null, count, tableModel);
    }

    @Override
    public String getViewName() {
        return "/panel/tab/containersTab.xhtml";
    }

    @Override
    protected String getTableCompositeId() {
        return "containerList";
    }

}
