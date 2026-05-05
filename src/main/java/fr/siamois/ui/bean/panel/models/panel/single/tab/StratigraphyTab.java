package fr.siamois.ui.bean.panel.models.panel.single.tab;

public class StratigraphyTab extends PanelTab {

    public StratigraphyTab(String titleCode, String icon, String id) {
        super(titleCode, icon, id);
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public String getViewName() {
        return "/panel/tab/stratiTab.xhtml";
    }

}
