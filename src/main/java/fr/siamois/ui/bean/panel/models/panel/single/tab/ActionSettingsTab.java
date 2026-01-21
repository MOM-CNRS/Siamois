package fr.siamois.ui.bean.panel.models.panel.single.tab;

public class ActionSettingsTab extends PanelTab {

    public ActionSettingsTab(String titleCode, String icon, String id) {
        super(titleCode, icon, id);
    }

    @Override
    public String getViewName() {
        return "/panel/tab/settings/actionSettingsTab.xhtml";
    }
}
