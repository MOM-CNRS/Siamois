package fr.siamois.domain.models.form.layout;

import fr.siamois.domain.models.form.config.FieldDefault;
import fr.siamois.domain.models.form.config.FieldUnits;
import fr.siamois.domain.models.form.config.FieldWidth;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.rules.FieldRules;

import java.util.List;

/**
 * The layout of a form for one (project, table, type): ordered groups of fields, each field with
 * its width, whether it is active and mandatory, and its conditional rules. It is the configured
 * form, before it is turned into the panels/rows/columns the clients render.
 */
public record FormLayout(List<Group> groups) {

    /** The group holding the fields a project added on top of the table's own. */
    public static final String ADDITIONAL_GROUP_LABEL = "Champs additionnels";

    /**
     * @param id    the stored group's id, or null for a group that only exists in memory
     * @param label a message key or free text
     * @param allowMeasures whether users may add measures of their own to the section of a record's fiche
     */
    public record Group(Long id, String label, List<Item> items, boolean allowMeasures) {

        /** A group that allows the measures users add on the fly, as every group did before it could be turned off. */
        public Group(Long id, String label, List<Item> items) {
            this(id, label, items, true);
        }
    }

    /**
     * @param units        for a measurement, the units this configuration offers; null keeps the field's own
     * @param defaultValue what a new entity starts with for this field; null for nothing
     */
    public record Item(CustomField field, FieldWidth width, boolean active, boolean mandatory,
                       boolean institutionLocked, FieldRules rules, FieldUnits units, FieldDefault defaultValue) {

        /** An item with no units and no default of its own. */
        public Item(CustomField field, FieldWidth width, boolean active, boolean mandatory,
                    boolean institutionLocked, FieldRules rules) {
            this(field, width, active, mandatory, institutionLocked, rules, null, null);
        }

        /** An item with no default of its own. */
        public Item(CustomField field, FieldWidth width, boolean active, boolean mandatory,
                    boolean institutionLocked, FieldRules rules, FieldUnits units) {
            this(field, width, active, mandatory, institutionLocked, rules, units, null);
        }
    }
}
