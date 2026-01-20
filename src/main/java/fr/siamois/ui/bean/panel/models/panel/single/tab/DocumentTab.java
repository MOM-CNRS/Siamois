package fr.siamois.ui.bean.panel.models.panel.single.tab;

public class DocumentTab extends PanelTab {

    public DocumentTab(String titleCode, String icon, String id) {
        super(titleCode, icon, id);
    }

    @Override
    public String getViewName() {
        return "/panel/tab/documentsTab.xhtml";
    }



}
