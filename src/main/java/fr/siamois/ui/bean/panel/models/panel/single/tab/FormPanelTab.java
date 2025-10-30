package fr.siamois.ui.bean.panel.models.panel.single.tab;

import lombok.Data;


@Data
public class FormPanelTab extends PanelTab {

    public FormPanelTab(String titleCode, String icon, String id) {
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
}
