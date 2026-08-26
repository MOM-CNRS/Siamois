package fr.siamois.ui.bean.panel.models.panel.single.tab;

import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.ui.table.viewmodel.PhaseTableViewModel;
import lombok.Data;

@Data
public class PhaseTab extends EntityListTab<PhaseDTO> {

    public PhaseTab(String titleCode, String icon, String id,
                    PhaseTableViewModel tableModel, Integer count) {
        super(titleCode, icon, id, null, count, tableModel);
    }

    @Override
    public String getViewName() {
        return "/panel/tab/phasesTab.xhtml";
    }

    @Override
    protected String getTableCompositeId() {
        return "phaseList";
    }

}
