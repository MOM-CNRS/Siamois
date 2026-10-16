package fr.siamois.domain.models.settings.tableconfig;

import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.util.Set;

/**
 * What a field carries besides its name, type and description, for the types that have more to
 * configure: a yes/no field may allow "no answer", a measurement says what it measures and picks the
 * units it is entered in. Options a type has no use for are ignored when the field is saved.
 *
 * @param allowNoAnswer       BOOLEAN: the answer may be left unanswered, a third state beside yes and no
 * @param defaultUnitId       MEASUREMENT: the unit a new answer starts in; one of {@code allowedUnitIds}
 * @param allowedUnitIds      MEASUREMENT: the units an answer can be entered in, all of the same dimension
 * @param measureTypeConceptId   MEASUREMENT: the concept of what is measured (null to leave it as it is)
 * @param measureNatureConceptId MEASUREMENT: the concept of the nature of the measure (null for none)
 */
public record FieldOptions(boolean allowNoAnswer, @Nullable Long defaultUnitId, Set<Long> allowedUnitIds,
                           @Nullable Long measureTypeConceptId, @Nullable Long measureNatureConceptId)
        implements Serializable {

    /** No option: the state a field has when its type has none. */
    public static final FieldOptions NONE = new FieldOptions(false, null, Set.of(), null, null);

    public FieldOptions {
        allowedUnitIds = allowedUnitIds == null ? Set.of() : Set.copyOf(allowedUnitIds);
    }

    public static FieldOptions noAnswer(boolean allowNoAnswer) {
        return new FieldOptions(allowNoAnswer, null, Set.of(), null, null);
    }

    public static FieldOptions units(@Nullable Long defaultUnitId, Set<Long> allowedUnitIds) {
        return new FieldOptions(false, defaultUnitId, allowedUnitIds, null, null);
    }

    /** The same options for a measurement that also says what it measures. */
    public FieldOptions measuring(@Nullable Long typeConceptId, @Nullable Long natureConceptId) {
        return new FieldOptions(allowNoAnswer, defaultUnitId, allowedUnitIds, typeConceptId, natureConceptId);
    }
}
