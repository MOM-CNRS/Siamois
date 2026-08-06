package fr.siamois.domain.models.form.customform;

import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.CustomFormPanelUiDto;
import fr.siamois.ui.form.dto.CustomRowUiDto;
import fr.siamois.ui.form.dto.FormUiDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rebuilds a {@link FormUiDto} as a base form plus a trailing panel of additional fields.
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
     * @return a new {@link FormUiDto} equal to {@code baseForm} plus one trailing panel containing
     * {@code additionalColumns}, or {@code baseForm} itself when there is nothing to add
     */
    public static FormUiDto withAdditionalFields(FormUiDto baseForm, String additionalPanelName, List<CustomColUiDto> additionalColumns) {
        if (additionalColumns == null || additionalColumns.isEmpty()) {
            return baseForm;
        }

        List<CustomFormPanelUiDto> panels = new ArrayList<>(baseForm.getLayout());
        panels.add(additionalFieldsPanel(additionalPanelName, additionalColumns));

        return new FormUiDto.Builder()
                .addPanels(panels)
                .build();
    }

    /**
     * @param baseForm  the form to start from, left untouched
     * @param panelName name of the existing panel to append to; unknown names are a no-op
     * @param columns   columns to append as a trailing row of that panel; may be empty
     * @return a new {@link FormUiDto} equal to {@code baseForm} with {@code columns} appended to
     * the named panel, for fields that belong with that panel's own rather than in a trailing
     * "additional fields" panel (e.g. measurement fields created from the measurements panel).
     * The named panel is always handed back as an independent, mutable copy — even when
     * {@code columns} is empty — since callers may go on to add fields to it in place (e.g. a
     * user creating a field "on the fly" from that panel); {@code baseForm} itself is only
     * returned as-is when it carries no panel of that name at all. Other panels are left as the
     * same instances {@code baseForm} already carries.
     */
    public static FormUiDto withFieldsInPanel(FormUiDto baseForm, String panelName, List<CustomColUiDto> columns) {
        List<CustomColUiDto> extraColumns = columns == null ? List.of() : columns;
        boolean hasNamedPanel = baseForm.getLayout().stream().anyMatch(panel -> panelName.equals(panel.getName()));
        if (!hasNamedPanel) {
            return baseForm;
        }

        List<CustomFormPanelUiDto> panels = baseForm.getLayout().stream()
                .map(panel -> panelName.equals(panel.getName()) ? withExtraRow(panel, extraColumns) : panel)
                .collect(Collectors.toCollection(ArrayList::new));

        return new FormUiDto.Builder()
                .addPanels(panels)
                .build();
    }

    private static CustomFormPanelUiDto withExtraRow(CustomFormPanelUiDto panel, List<CustomColUiDto> columns) {
        List<CustomRowUiDto> rows = panel.getRows().stream()
                .map(CustomFormComposer::copyOfRow)
                .collect(Collectors.toCollection(ArrayList::new));
        if (!columns.isEmpty()) {
            rows.add(new CustomRowUiDto.Builder().addColumns(columns).build());
        }

        CustomFormPanelUiDto copy = new CustomFormPanelUiDto();
        copy.setName(panel.getName());
        copy.setClassName(panel.getClassName());
        copy.setIsSystemPanel(panel.getIsSystemPanel());
        copy.setCanUserAddFields(panel.getCanUserAddFields());
        copy.setRows(rows);
        return copy;
    }

    private static CustomRowUiDto copyOfRow(CustomRowUiDto row) {
        CustomRowUiDto copy = new CustomRowUiDto();
        copy.setColumns(new ArrayList<>(row.getColumns()));
        return copy;
    }

    private static CustomFormPanelUiDto additionalFieldsPanel(String name, List<CustomColUiDto> additionalColumns) {
        return new CustomFormPanelUiDto.Builder()
                .name(name)
                .isSystemPanel(false)
                .addRow(new CustomRowUiDto.Builder().addColumns(additionalColumns).build())
                .build();
    }

    /**
     * @param baseForm              the form to start from, left untouched
     * @param valueBindingsToRemove {@link CustomColUiDto#getField()}'s {@code valueBinding}s to drop;
     *                              if empty, {@code baseForm} is returned as-is
     * @return a new {@link FormUiDto} with any column whose field's valueBinding is in
     * {@code valueBindingsToRemove} removed (rows left empty by the removal are dropped too), or
     * {@code baseForm} itself when there is nothing to remove
     */
    public static FormUiDto withoutFields(FormUiDto baseForm, Set<String> valueBindingsToRemove) {
        if (valueBindingsToRemove == null || valueBindingsToRemove.isEmpty()) {
            return baseForm;
        }

        List<CustomFormPanelUiDto> panels = baseForm.getLayout().stream()
                .map(panel -> withoutFields(panel, valueBindingsToRemove))
                .toList();

        return new FormUiDto.Builder()
                .addPanels(panels)
                .build();
    }

    private static CustomFormPanelUiDto withoutFields(CustomFormPanelUiDto panel, Set<String> valueBindingsToRemove) {
        List<CustomRowUiDto> rows = panel.getRows().stream()
                .map(row -> withoutFields(row, valueBindingsToRemove))
                .filter(row -> !row.getColumns().isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));

        CustomFormPanelUiDto copy = new CustomFormPanelUiDto();
        copy.setName(panel.getName());
        copy.setClassName(panel.getClassName());
        copy.setIsSystemPanel(panel.getIsSystemPanel());
        copy.setCanUserAddFields(panel.getCanUserAddFields());
        copy.setRows(rows);
        return copy;
    }

    private static CustomRowUiDto withoutFields(CustomRowUiDto row, Set<String> valueBindingsToRemove) {
        List<CustomColUiDto> columns = row.getColumns().stream()
                .filter(col -> !valueBindingsToRemove.contains(col.getField().getValueBinding()))
                .collect(Collectors.toCollection(ArrayList::new));

        CustomRowUiDto copy = new CustomRowUiDto();
        copy.setColumns(columns);
        return copy;
    }
}
