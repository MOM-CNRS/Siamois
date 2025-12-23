package fr.siamois.ui.bean.panel.models.panel.single.tab;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.ui.table.ActionUnitTableViewModel;
import lombok.Data;

@Data
public class ActionTab extends EntityListTab<ActionUnit> {

    public ActionTab(String titleCode, String icon, String id,
                     Integer count, ActionUnitTableViewModel tableModel) {
        super(titleCode, icon, id, null, count, tableModel);
    }

}
