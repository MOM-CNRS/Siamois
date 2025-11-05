package fr.siamois.domain.models.form.customform;

import fr.siamois.domain.models.form.customfield.CustomField;
import lombok.Data;

import java.io.Serializable;


@Data
public class CustomCol implements Serializable {

    private boolean readOnly = false;
    private boolean isRequired = false;
    private CustomField field;
    private String className;
    private EnabledWhenJson enabledWhenSpec;

    public static class Builder {

        private final CustomCol col = new CustomCol();

        public Builder readOnly(boolean readOnly) {
            col.setReadOnly(readOnly);
            return this;
        }

        public Builder isRequired(boolean isRequired) {
            col.setRequired(isRequired);
            return this;
        }

        public Builder field(CustomField field) {
            col.setField(field);
            return this;
        }

        public Builder className(String className) {
            col.setClassName(className);
            return this;
        }

        public Builder enabledWhenSpec(EnabledWhenJson enabledWhenSpec) {
            col.setEnabledWhenSpec(enabledWhenSpec);
            return this;
        }

        public CustomCol build() {
            return col;
        }
    }

}
