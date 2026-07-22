package fr.siamois.domain.models.form.customform;

import java.util.ArrayList;
import java.util.List;

/**
 * Rebuilds a {@link CustomForm} as a base form plus a trailing panel of additional fields.
 * <p>
 * Never mutates the base form or any of its existing panels/rows/cols — several base forms
 * (e.g. {@code RecordingUnit.DETAILS_FORM}) are {@code public static final} shared singletons,
 * and mutating them in place would corrupt the form for every other session. Only a new list and
 * a new trailing panel are created; the existing panel objects are safe to reference as-is.
 */
public final class CustomFormComposer {

    private CustomFormComposer() {
        throw new UnsupportedOperationException();
    }

    /**
     * @param baseForm             the form to start from, left untouched
     * @param additionalPanelName  name/label key of the trailing panel holding the additional columns
     * @param additionalColumns    columns to append; if empty, {@code baseForm} is returned as-is
     * @return a new {@link CustomForm} equal to {@code baseForm} plus one trailing panel containing
     * {@code additionalColumns}, or {@code baseForm} itself when there is nothing to add
     */
    public static CustomForm withAdditionalFields(CustomForm baseForm, String additionalPanelName, List<CustomCol> additionalColumns) {
        if (additionalColumns == null || additionalColumns.isEmpty()) {
            return baseForm;
        }

        List<CustomFormPanel> panels = new ArrayList<>(baseForm.getLayout());
        panels.add(additionalFieldsPanel(additionalPanelName, additionalColumns));

        return new CustomForm.Builder()
                .name(baseForm.getName())
                .description(baseForm.getDescription())
                .addPanels(panels)
                .build();
    }

    private static CustomFormPanel additionalFieldsPanel(String name, List<CustomCol> additionalColumns) {
        return new CustomFormPanel.Builder()
                .name(name)
                .isSystemPanel(false)
                .addRow(new CustomRow.Builder().addColumns(additionalColumns).build())
                .build();
    }
}
