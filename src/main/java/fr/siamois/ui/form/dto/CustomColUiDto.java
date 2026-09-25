package fr.siamois.ui.form.dto;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customform.DependsOnJson;
import fr.siamois.domain.models.form.customform.EnabledWhenJson;
import lombok.Data;

import java.io.Serializable;

@Data
public class CustomColUiDto implements Serializable {

    private boolean readOnly = false;
    private boolean isRequired = false;
    private boolean canBeRemoved = false;
    private CustomField field;
    private String className;
    // Structured alternative to `className` — see ColumnWidth's own doc. Only
    // ActionUnitDetailsForm/RecordingUnitDetailsForm set these today; every other form still sets
    // `className` directly, which getClassName() falls back to when `width` is null.
    private ColumnWidth width;
    private boolean hidden = false;
    private EnabledWhenJson enabledWhenSpec;
    private DependsOnJson dependsOnSpec;

    /**
     * The PrimeFaces styleClass {@code customFormPanelContent.xhtml} binds via {@code col.className}
     * — computed from {@link #width}/{@link #hidden} when a column was built with
     * {@code .width(...)}, falling back to the raw {@link #className} string for every column still
     * built the old way. Either path, JSF's own template needs no change: `col.className` keeps
     * meaning the same thing.
     */
    public String getClassName() {
        if (width == null) {
            return className;
        }
        return hidden ? width.toPrimeFacesClassName() + " d-none" : width.toPrimeFacesClassName();
    }

    public static class Builder {

        private final CustomColUiDto col = new CustomColUiDto();

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

        public Builder width(ColumnWidth width) {
            col.setWidth(width);
            return this;
        }

        public Builder hidden(boolean hidden) {
            col.setHidden(hidden);
            return this;
        }

        public Builder enabledWhenSpec(EnabledWhenJson enabledWhenSpec) {
            col.setEnabledWhenSpec(enabledWhenSpec);
            return this;
        }

        public Builder dependsOnSpec(DependsOnJson dependsOnSpec) {
            col.setDependsOnSpec(dependsOnSpec);
            return this;
        }

        public CustomColUiDto build() {
            return col;
        }
    }

}
