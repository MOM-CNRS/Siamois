package fr.siamois.domain.models.form.layout;

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
     */
    public record Group(Long id, String label, List<Item> items) {
    }

    public record Item(CustomField field, FieldWidth width, boolean active, boolean mandatory,
                       boolean institutionLocked, FieldRules rules) {
    }
}
