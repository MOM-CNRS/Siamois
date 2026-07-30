package fr.siamois.ui.table.definitions;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.ui.table.TableDefinition;
import fr.siamois.ui.table.column.FormFieldColumn;

import java.util.List;
import java.util.Objects;

/**
 * The system fields of each configurable table, as its table definition factory declares them.
 * <p>
 * The factories are the definition of the system fields: they are no longer seeded as forms in the
 * database. This catalog is what everything else reads them through — the startup initializer that
 * gives each of them its instance-wide {@code custom_field} row, and the field configuration screen
 * that lists them per table.
 */
public final class SystemFieldCatalog {

    private SystemFieldCatalog() {
        throw new UnsupportedOperationException();
    }

    /**
     * The system field columns of a table, in the order its definition lays them out. Columns
     * backed by no field, and the few carrying a non-system field, are left out: this catalog only
     * answers for the fields the application defines itself.
     *
     * @param table the table whose system fields are read
     * @return the table's system field columns, in definition order
     */
    public static List<FormFieldColumn> columnsOf(ConfigurableTable table) {
        return definitionOf(table).getFieldColumns().stream()
                .filter(column -> column.getField() != null
                        && Boolean.TRUE.equals(column.getField().getIsSystemField()))
                .toList();
    }

    /**
     * The system fields of a table, in the order its definition lays them out.
     *
     * @param table the table whose system fields are read
     * @return the table's system fields, in definition order
     */
    public static List<CustomField> fieldsOf(ConfigurableTable table) {
        return columnsOf(table).stream()
                .map(FormFieldColumn::getField)
                .toList();
    }

    /**
     * What identifies a system field across the instance: its label — a message key, which is
     * unique per field of a table — and the entity property it binds to. Neither the database id
     * nor the ids the definitions carry can play that role: those are local to a definition class
     * and the same number stands for different fields from one class to the next.
     * <p>
     * Two tables declaring the same label and binding (the identifier shared by UE and Mobilier)
     * deliberately map to the same field: they are the same field, and configuring it stays
     * per-table anyway, since a configuration belongs to one table's {@code FormConfig}.
     *
     * @param field the field to identify
     * @return a key equal for two declarations of the same system field
     */
    public static String identityOf(CustomField field) {
        return field.getLabel() + " " + field.getValueBinding();
    }

    private static TableDefinition definitionOf(ConfigurableTable table) {
        Objects.requireNonNull(table, "A table is needed to read its system fields");
        return switch (table) {
            case UE -> RecordingUnitTableDefinitionFactory.definition();
            case MOBILIER -> SpecimenTableDefinitionFactory.definition();
            case PHASE -> PhaseTableDefinitionFactory.definition();
            case CONTENANT -> ContainerTableDefinitionFactory.definition();
        };
    }
}
