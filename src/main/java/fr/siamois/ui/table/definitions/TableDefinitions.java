package fr.siamois.ui.table.definitions;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.measurement.UnitDefinition;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.ui.table.TableDefinition;
import fr.siamois.ui.table.column.CommandLinkColumn;
import fr.siamois.ui.table.column.FormFieldColumn;
import fr.siamois.ui.table.column.TableColumnAction;

import java.util.NoSuchElementException;

import static fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity.SYSTEM_THESO;

/**
 * What the table definition factories would otherwise each spell out again: the concepts of the
 * system vocabulary, the chip column opening an entity's panel, and the columns of a field.
 * <p>
 * Only the shape is shared. What a table actually lays out — which fields, in which order, shown or
 * hidden, sortable or not — stays in its own factory, where it is read as one list.
 */
final class TableDefinitions {

    static final String IDENTIFIER = "identifier";

    private static final String IDENTIFIER_COLUMN_ID = "identifierCol";
    private static final String THIS = "@this";
    private static final String FLOW = "flow";
    private static final String SHOW_CONTENT_JS = "PF('buiContent').show()";
    private static final String HIDE_CONTENT_JS = "PF('buiContent').hide();";

    private TableDefinitions() {
        throw new UnsupportedOperationException();
    }

    /**
     * A concept of the system thesaurus, which is the vocabulary every system field is described in.
     * The concept is built rather than read: it names the field, it is not one of the values the
     * user picks from.
     *
     * @param externalId the concept's id in the thesaurus
     * @return a concept of the system thesaurus
     */
    static Concept systemConcept(String externalId) {
        return new Concept.Builder().vocabulary(SYSTEM_THESO).externalId(externalId).build();
    }

    /**
     * A unit of a measurement field, expressed against the base unit of its dimension.
     *
     * @param label        the unit's name
     * @param symbol       the unit's symbol
     * @param dimension    what the unit measures
     * @param factorToBase how many base units one of these is worth
     * @return the unit definition
     */
    static UnitDefinition unit(String label, String symbol, UnitDefinition.Dimension dimension, double factorToBase) {
        return new UnitDefinition(null, null, label, symbol, dimension, factorToBase, false);
    }

    /**
     * The leading chip column: it shows the entity's identifier and opens its panel when clicked.
     * Never hidden — the row would then have nothing to be opened by. Sortable and filterable by
     * that same identifier.
     *
     * @param headerKey  i18n key of the column header
     * @param iconClass  icon shown inside the chip
     * @param chipColor  CSS colour of the chip
     * @param action     what the click opens
     * @param editable   whether the chip's identifier can be edited inline (pencil icon)
     * @return the link column
     */
    static CommandLinkColumn panelLinkColumn(String headerKey, String iconClass, String chipColor,
                                             TableColumnAction action, boolean editable) {
        return CommandLinkColumn.builder()
                .id(IDENTIFIER_COLUMN_ID)
                .headerKey(headerKey)
                .visible(true)
                .toggleable(false)
                .sortable(true)
                .filterable(true)
                .sortField(IDENTIFIER)
                .iconClass(iconClass)
                .chipColor(chipColor)
                .valueKey(IDENTIFIER)
                .action(action)
                .editable(editable)
                .processExpr(THIS)
                .updateExpr(FLOW)
                .onstartJs(SHOW_CONTENT_JS)
                .oncompleteJs(HIDE_CONTENT_JS)
                .build();
    }

    /**
     * The field a table's details form declares for a given binding — the same instance
     * {@link SystemFieldCatalog} would hand any other caller, so a factory's columns describe
     * exactly the fields the field-configuration screen and the details form agree exist, instead
     * of redeclaring their own copy (own label, own literal id) that could drift from it.
     *
     * @param table        the table the field belongs to
     * @param valueBinding the entity property the field binds to
     * @return the table's system field bound to that property
     */
    static CustomField systemField(ConfigurableTable table, String valueBinding) {
        return SystemFieldCatalog.fieldsOf(table).stream()
                .filter(field -> valueBinding.equals(field.getValueBinding()))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException(
                        "No system field bound to '" + valueBinding + "' on the details form of " + table));
    }

    /**
     * A column of a field, named and titled after the field itself: a column's id is the property
     * its field binds to, and its header the field's label.
     * <p>
     * Everything a column may be — shown, sortable, filterable, required — it is not until the
     * caller says so, so a definition only spells out what sets a column apart.
     *
     * @param field the field the column shows
     * @return a builder for that field's column
     */
    static FormFieldColumn.FormFieldColumnBuilder<?, ?> column(CustomField field) {
        return FormFieldColumn.builder()
                .id(field.getValueBinding())
                .headerKey(field.getLabel())
                .field(field);
    }

    /**
     * Adds the columns to a definition, in the order given.
     *
     * @param definition the definition to fill
     * @param columns    the columns to add
     */
    static void addColumns(TableDefinition definition, FormFieldColumn... columns) {
        for (FormFieldColumn column : columns) {
            definition.addColumn(column);
        }
    }
}
