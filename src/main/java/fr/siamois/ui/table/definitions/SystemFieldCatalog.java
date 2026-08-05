package fr.siamois.ui.table.definitions;

import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.FormUiDto;
import org.springframework.beans.BeanUtils;

import java.util.List;
import java.util.Objects;

/**
 * The system fields of each configurable table, as its entity's real details form declares them.
 * <p>
 * The details form (e.g. {@link RecordingUnit#DETAILS_FORM}) is the source of truth for which
 * fields exist for a type — not the table's own column definition ({@code *TableDefinitionFactory}),
 * whose job is only to decide which of those fields show up as table columns, and how (order,
 * visible by default, sortable, filterable). This catalog is what everything else reads the field
 * set through — the startup initializer that gives each field its instance-wide {@code custom_field}
 * row, and the field configuration screen that lists them per table.
 */
public final class SystemFieldCatalog {

    private SystemFieldCatalog() {
        throw new UnsupportedOperationException();
    }

    /**
     * The system field columns of a table's details form, in the order the form lays them out.
     * Columns backed by no field, and the few carrying a non-system field, are left out: this
     * catalog only answers for the fields the application defines itself.
     * <p>
     * Hands out a fresh copy of each field rather than the details form's own instance: the form
     * (e.g. {@link RecordingUnit#DETAILS_FORM}) is a shared, static singleton, so returning its
     * fields directly would let any caller that edits one (tests routinely stamp an id on a field
     * to simulate a persisted row) corrupt that singleton for the rest of the JVM's lifetime.
     *
     * @param table the table whose system fields are read
     * @return the table's system field columns, in form order, each a fresh, independent copy
     */
    public static List<CustomColUiDto> systemColumnsOf(ConfigurableTable table) {
        Objects.requireNonNull(table, "A table is needed to read its system fields");
        return detailsFormOf(table).getLayout().stream()
                .flatMap(panel -> panel.getRows().stream())
                .flatMap(row -> row.getColumns().stream())
                .filter(column -> column.getField() != null
                        && Boolean.TRUE.equals(column.getField().getIsSystemField()))
                .map(SystemFieldCatalog::copyOf)
                .toList();
    }

    private static CustomColUiDto copyOf(CustomColUiDto column) {
        return new CustomColUiDto.Builder()
                .field(copyOf(column.getField()))
                .className(column.getClassName())
                .readOnly(column.isReadOnly())
                .isRequired(column.isRequired())
                .enabledWhenSpec(column.getEnabledWhenSpec())
                .dependsOnSpec(column.getDependsOnSpec())
                .build();
    }

    private static CustomField copyOf(CustomField field) {
        try {
            CustomField copy = field.getClass().getDeclaredConstructor().newInstance();
            BeanUtils.copyProperties(field, copy);
            return copy;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot copy system field " + field.getClass().getName(), e);
        }
    }

    /**
     * The system fields of a table, in the order its details form lays them out.
     *
     * @param table the table whose system fields are read
     * @return the table's system fields, in form order
     */
    public static List<CustomField> fieldsOf(ConfigurableTable table) {
        return systemColumnsOf(table).stream().map(CustomColUiDto::getField).toList();
    }

    /**
     * What identifies a system field within a table: its label — a message key, which is unique
     * per field of a table — and the entity property it binds to. A definition's id is now stable
     * and persisted as-is (see {@code SystemFieldInitializer}), so it is what tells two tables'
     * fields apart even when they share a label and binding (e.g. the identifier field, declared
     * once per table with its own id): each table's declaration is its own row, configured
     * independently, since a configuration belongs to one table's {@code FormConfig} anyway.
     *
     * @param field the field to identify
     * @return a key equal for two declarations of the same system field within one table
     */
    public static String identityOf(CustomField field) {
        return field.getLabel() + " " + field.getValueBinding();
    }

    private static FormUiDto detailsFormOf(ConfigurableTable table) {
        Objects.requireNonNull(table, "A table is needed to read its system fields");
        return switch (table) {
            case UE -> RecordingUnit.DETAILS_FORM;
            case MOBILIER -> Specimen.DETAILS_FORM;
            case PHASE -> Phase.DETAILS_FORM;
            case CONTENANT -> Container.DETAILS_FORM;
        };
    }
}
