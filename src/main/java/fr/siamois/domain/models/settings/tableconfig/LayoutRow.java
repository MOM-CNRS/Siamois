package fr.siamois.domain.models.settings.tableconfig;

import fr.siamois.domain.models.form.config.FieldWidth;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

/**
 * One row of the layout screen: either a group header or a field. A field belongs to the closest
 * header above it, so dragging a row anywhere in the list is enough to move a field between groups.
 */
@Getter
@Setter
@NoArgsConstructor
public class LayoutRow implements Serializable {

    /** Identifies the row on screen (a group created on screen has no id yet). */
    private final String key = UUID.randomUUID().toString();
    private boolean group;
    /** The stored group's id; null for a group created on screen and not saved yet. */
    private Long groupId;
    /** The stored label: a message key or free text. */
    private String groupLabel;
    /** What the user sees and edits: the translated label, unless it was changed. */
    private String displayLabel;
    /** Set on a field row. */
    private TypeFieldFormConfig field;
    private FieldWidth width = FieldWidth.QUARTER;
    private boolean hasRules;

    public static LayoutRow of(TypeFieldFormConfig field, FieldWidth width) {
        return of(field, width, false);
    }

    public static LayoutRow header(Long groupId, String label) {
        LayoutRow row = new LayoutRow();
        row.group = true;
        row.groupId = groupId;
        row.groupLabel = label;
        row.displayLabel = label;
        return row;
    }

    public static LayoutRow of(TypeFieldFormConfig field, FieldWidth width, boolean hasRules) {
        LayoutRow row = new LayoutRow();
        row.hasRules = hasRules;
        row.field = field;
        row.width = width;
        return row;
    }

    public boolean isFieldRow() {
        return !group;
    }
}
