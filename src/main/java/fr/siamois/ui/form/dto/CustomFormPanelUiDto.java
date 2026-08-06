package fr.siamois.ui.form.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class CustomFormPanelUiDto implements Serializable {

    private String className;
    private String name;
    private List<CustomRowUiDto> rows;
    private Boolean canUserAddFields;
    private Boolean isSystemPanel; // define by system or user

    private boolean showEditor = false;

    public static class Builder {

        private final CustomFormPanelUiDto panel = new CustomFormPanelUiDto();
        private final List<CustomRowUiDto> rows = new ArrayList<>();

        public Builder name(String name) {
            panel.setName(name);
            return this;
        }

        public Builder className(String className) {
            panel.setClassName(className);
            return this;
        }

        public Builder isSystemPanel(boolean isSystem) {
            panel.setIsSystemPanel(isSystem);
            return this;
        }

        public Builder canUserAddField(boolean canUserAddField) {
            panel.setCanUserAddFields(canUserAddField);
            return this;
        }

        public Builder addRow(CustomRowUiDto row) {
            rows.add(row);
            return this;
        }

        public Builder addRows(CustomRowUiDto... rowArray) {
            rows.addAll(List.of(rowArray));
            return this;
        }

        public Builder addRows(List<CustomRowUiDto> rowList) {
            rows.addAll(rowList);
            return this;
        }

        public CustomFormPanelUiDto build() {
            panel.setRows(rows);
            return panel;
        }
    }

}
