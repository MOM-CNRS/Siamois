package fr.siamois.ui.bean.panel.models.panel.single.tab;

import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.ui.table.SpecimenTableViewModel;


public class SpecimenTab extends EntityListTab<Specimen> {

    public SpecimenTab(String titleCode, String icon, String id,
                       SpecimenTableViewModel tableViewModel, Integer count) {
        super(titleCode, icon, id,  null, count, tableViewModel);
    }

    @Override
    public String getViewName() {
        return "/panel/tab/specimenTab.xhtml";
    }

}
