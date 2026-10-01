package fr.siamois.ui.table.definitions;

import fr.siamois.domain.models.container.form.ContainerForm;
import fr.siamois.domain.models.form.config.SystemFieldSpec;
import fr.siamois.domain.models.phase.form.PhaseForm;
import fr.siamois.domain.models.recordingunit.form.RecordingUnitForm;
import fr.siamois.domain.models.specimen.form.SpecimenForm;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.actionunit.CustomFieldSelectOneActionUnit;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import org.springframework.beans.BeanUtils;

import java.util.List;
import java.util.NoSuchElementException;
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
     * The declared system fields of a table, in their default order, with what is intrinsic to each
     * ({@code hidden}, {@code readOnly}). The field set is declared by the table's {@code *Form}
     * base class ({@code systemFields()}), not derived from a layout: layouts (groups, order, widths)
     * are configuration. The fields are the shared definitions; callers must not mutate them.
     *
     * @param table the table whose system fields are read
     * @return the table's system field specs, in default order
     */
    public static List<SystemFieldSpec> specsOf(ConfigurableTable table) {
        Objects.requireNonNull(table, "A table is needed to read its system fields");
        return switch (table) {
            case UE -> RecordingUnitForm.systemFields();
            case MOBILIER -> SpecimenForm.systemFields();
            case PHASE -> PhaseForm.systemFields();
            case CONTENANT -> ContainerForm.systemFields();
        };
    }

    /**
     * The spec of a system field of a table, if it is one.
     *
     * @param table   the table
     * @param fieldId the field's id (system fields carry a stable negative id)
     * @return the field's spec, or empty when the field is not a system field of that table
     */
    public static java.util.Optional<SystemFieldSpec> specOf(ConfigurableTable table, Long fieldId) {
        return specsOf(table).stream()
                .filter(spec -> Objects.equals(spec.field().getId(), fieldId))
                .findFirst();
    }

    /**
     * The system fields of a table, in default order, each a fresh, independent copy: callers
     * routinely edit them (tests stamp an id on a field to simulate a persisted row), which must
     * never reach the shared definitions.
     *
     * @param table the table whose system fields are read
     * @return the table's system fields, in default order
     */
    public static List<CustomField> fieldsOf(ConfigurableTable table) {
        return specsOf(table).stream().map(spec -> copyOf(spec.field())).toList();
    }

    /**
     * The table's system fields without copying, for read-only use on hot paths (answer projection
     * per row). Never mutate the result.
     *
     * @param table the table whose system fields are read
     * @return the shared field definitions, in default order
     */
    public static List<CustomField> sharedFieldsOf(ConfigurableTable table) {
        return specsOf(table).stream().map(SystemFieldSpec::field).toList();
    }

    /**
     * The field a table's details form declares for a given binding — the same instance every other
     * caller of this catalog gets, so column defaults describe exactly the fields the
     * field-configuration screen and the details form agree exist.
     *
     * @param table        the table the field belongs to
     * @param valueBinding the entity property the field binds to
     * @return the table's system field bound to that property
     */
    public static CustomField fieldBoundTo(ConfigurableTable table, String valueBinding) {
        return fieldsOf(table).stream()
                .filter(field -> valueBinding.equals(field.getValueBinding()))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException(
                        "No system field bound to '" + valueBinding + "' on the details form of " + table));
    }

    /**
     * Whether a field is the project the entity belongs to (a recording unit's, find's, phase's or
     * container's {@code actionUnit}). It is an attribute set once at creation, not a configurable
     * field: it is never offered in a project's field configuration, never editable afterwards, and
     * lists show it through their own "Projet" column rather than as a field column.
     *
     * @param field the field to test
     * @return true for the owning-project field
     */
    public static boolean isOwningProject(CustomField field) {
        return field instanceof CustomFieldSelectOneActionUnit && "actionUnit".equals(field.getValueBinding());
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
}
