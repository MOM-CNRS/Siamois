package fr.siamois.domain.models.form.customform;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDateTime;
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
 * <p>
 * Callers that need to apply runtime-only, per-view constraints to a resolved form's fields
 * (e.g. constraining a date field's min/max from another field's current value) must call
 * {@link #deepCopy(FormUiDto)} first and mutate the copy — never a form/panel/col/field handed
 * back by this class or a static {@code DETAILS_FORM} constant directly.
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

    /**
     * @param form the form to copy, left untouched; {@code null} is returned as {@code null}
     * @return an independent deep copy of {@code form} (panels, rows and cols are all fresh
     * objects) safe to mutate at runtime — e.g. to apply a per-view constraint to a date field —
     * without corrupting {@code form} itself or any other form/row/col sharing its objects.
     */
    public static FormUiDto deepCopy(FormUiDto form) {
        if (form == null) {
            return null;
        }
        List<CustomFormPanelUiDto> panels = form.getLayout().stream()
                .map(CustomFormComposer::deepCopyPanel)
                .collect(Collectors.toCollection(ArrayList::new));

        return new FormUiDto.Builder()
                .addPanels(panels)
                .build();
    }

    private static CustomFormPanelUiDto deepCopyPanel(CustomFormPanelUiDto panel) {
        CustomFormPanelUiDto copy = new CustomFormPanelUiDto();
        copy.setName(panel.getName());
        copy.setClassName(panel.getClassName());
        copy.setIsSystemPanel(panel.getIsSystemPanel());
        copy.setCanUserAddFields(panel.getCanUserAddFields());
        copy.setRows(panel.getRows().stream()
                .map(CustomFormComposer::deepCopyRow)
                .collect(Collectors.toCollection(ArrayList::new)));
        return copy;
    }

    private static CustomRowUiDto deepCopyRow(CustomRowUiDto row) {
        CustomRowUiDto copy = new CustomRowUiDto();
        copy.setColumns(row.getColumns().stream()
                .map(CustomFormComposer::deepCopyCol)
                .collect(Collectors.toCollection(ArrayList::new)));
        return copy;
    }

    private static CustomColUiDto deepCopyCol(CustomColUiDto col) {
        CustomColUiDto copy = new CustomColUiDto();
        copy.setReadOnly(col.isReadOnly());
        copy.setRequired(col.isRequired());
        copy.setCanBeRemoved(col.isCanBeRemoved());
        copy.setField(deepCopyField(col.getField()));
        copy.setClassName(col.getClassName());
        copy.setEnabledWhenSpec(col.getEnabledWhenSpec());
        copy.setDependsOnSpec(col.getDependsOnSpec());
        return copy;
    }

    /**
     * Only {@link CustomFieldDateTime} is ever mutated at runtime (its min/max), so only it needs
     * an independent copy; every other field type is safe to keep sharing.
     */
    private static CustomField deepCopyField(CustomField field) {
        if (field instanceof CustomFieldDateTime dt) {
            return dt.toBuilder().build();
        }
        return field;
    }
}
