package fr.siamois.domain.models.form.customform;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

    /**
     * @param baseForm              the form to start from, left untouched
     * @param valueBindingsToRemove {@link CustomCol#getField()}'s {@code valueBinding}s to drop;
     *                              if empty, {@code baseForm} is returned as-is
     * @return a new {@link CustomForm} with any column whose field's valueBinding is in
     * {@code valueBindingsToRemove} removed (rows left empty by the removal are dropped too), or
     * {@code baseForm} itself when there is nothing to remove
     */
    public static CustomForm withoutFields(CustomForm baseForm, Set<String> valueBindingsToRemove) {
        if (valueBindingsToRemove == null || valueBindingsToRemove.isEmpty()) {
            return baseForm;
        }

        List<CustomFormPanel> panels = baseForm.getLayout().stream()
                .map(panel -> withoutFields(panel, valueBindingsToRemove))
                .toList();

        return new CustomForm.Builder()
                .name(baseForm.getName())
                .description(baseForm.getDescription())
                .addPanels(panels)
                .build();
    }

    private static CustomFormPanel withoutFields(CustomFormPanel panel, Set<String> valueBindingsToRemove) {
        List<CustomRow> rows = panel.getRows().stream()
                .map(row -> withoutFields(row, valueBindingsToRemove))
                .filter(row -> !row.getColumns().isEmpty())
                .toList();

        CustomFormPanel copy = new CustomFormPanel();
        copy.setName(panel.getName());
        copy.setClassName(panel.getClassName());
        copy.setIsSystemPanel(panel.getIsSystemPanel());
        copy.setCanUserAddFields(panel.getCanUserAddFields());
        copy.setRows(rows);
        return copy;
    }

    private static CustomRow withoutFields(CustomRow row, Set<String> valueBindingsToRemove) {
        List<CustomCol> columns = row.getColumns().stream()
                .filter(col -> !valueBindingsToRemove.contains(col.getField().getValueBinding()))
                .toList();

        CustomRow copy = new CustomRow();
        copy.setColumns(columns);
        return copy;
    }
}
