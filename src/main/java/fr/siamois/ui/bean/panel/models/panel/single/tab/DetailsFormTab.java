package fr.siamois.ui.bean.panel.models.panel.single.tab;

public class DetailsFormTab extends FormPanelTab {
    public DetailsFormTab(String titleCode, String icon, String id) {
        super(titleCode, icon, id);
    }

    @Override
    public String getViewName() {
        return "/panel/tab/detailsTab.xhtml";
    }

}
