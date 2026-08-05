package fr.siamois.ui.form.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class FormUiDto {

    private List<CustomFormPanelUiDto> layout;

    public static class Builder {

        private final FormUiDto form = new FormUiDto();
        private final List<CustomFormPanelUiDto> panels = new ArrayList<>();

        public Builder addPanel(CustomFormPanelUiDto panel) {
            panels.add(panel);
            return this;
        }

        public Builder addPanels(CustomFormPanelUiDto... panelArray) {
            panels.addAll(List.of(panelArray));
            return this;
        }

        public Builder addPanels(List<CustomFormPanelUiDto> panelList) {
            panels.addAll(panelList);
            return this;
        }

        public FormUiDto build() {
            form.setLayout(panels);
            return form;
        }
    }

}
