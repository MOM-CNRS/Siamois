package fr.siamois.ui.bean.panel.models.panel.single.tab;

import lombok.Data;

@Data
public class PanelTab {

    private String titleCode;
    private String icon;
    private String id;
    private static final String ROOT = "singlePanelUnitForm:singlePanelUnitTabs";

    public String getRoot() {
        return ROOT;
    }

    public PanelTab(String titleCode, String icon, String id) {
        this.titleCode = titleCode;
        this.icon = icon;
        this.id = id;
    }
}
