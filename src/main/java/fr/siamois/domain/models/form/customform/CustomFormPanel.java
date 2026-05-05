package fr.siamois.domain.models.form.customform;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class CustomFormPanel implements Serializable {

    private String className;
    private String name;
    private List<CustomRow> rows;
    private Boolean isSystemPanel; // define by system or user?
    private Boolean canUserAddFields;

    public static class Builder {

        private final CustomFormPanel panel = new CustomFormPanel();
        private final List<CustomRow> rows = new ArrayList<>();

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

        public Builder addRow(CustomRow row) {
            rows.add(row);
            return this;
        }

        public Builder addRows(CustomRow... rowArray) {
            rows.addAll(List.of(rowArray));
            return this;
        }

        public Builder addRows(List<CustomRow> rowList) {
            rows.addAll(rowList);
            return this;
        }

        public CustomFormPanel build() {
            panel.setRows(rows);
            return panel;
        }
    }


}
