package fr.siamois.ui.bean.panel.models.panel.single.tab;

import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.ui.lazydatamodel.BaseSpecimenLazyDataModel;


public class SpecimenTab extends EntityListTab<Specimen> {

    public SpecimenTab(String titleCode, String icon, String id,
                       BaseSpecimenLazyDataModel specimenListLazyDataModel, Integer count) {
        super(titleCode, icon, id,  specimenListLazyDataModel, count);
    }

    @Override
    public String getViewName() {
        return "/panel/tab/specimenTab.xhtml";
    }

}
