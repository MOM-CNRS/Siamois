package fr.siamois.ui.bean.panel.models.panel.single.tab;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.ui.table.RecordingUnitTableViewModel;
import lombok.Data;

import java.util.Map;

@Data
public class RecordingTab extends EntityListTab<RecordingUnit> {

    public RecordingTab(String titleCode, String icon, String id,
                        RecordingUnitTableViewModel tableModel, Integer count ) {
        super(titleCode, icon, id, null, count, tableModel);
    }

    @Override
    public String getViewName() {
        return "/panel/tab/recordingUnitsTab.xhtml";
    }

}
