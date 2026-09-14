package fr.siamois.domain.models.settings.tableconfig;

import fr.siamois.domain.models.form.customfield.CustomField;

import java.util.List;
import java.util.Set;

/**
 * The two projections of a type's effective fields that {@code EffectiveFormResolver} needs to
 * compose a system form with a project's configuration: the value bindings of the system fields it
 * deactivated, and the additional fields it activated. Resolved together in a single pass over
 * {@code effectiveFields} rather than one {@code TableFieldConfigService} call each — the two calls
 * used to each walk the whole field resolution (system fields + stored {@code FormConfig} lookups)
 * independently, doubling the work for a result the caller only ever used together.
 *
 * @param inactiveValueBindings the {@link CustomField#getValueBinding()} of every system field the
 *                               type deactivated
 * @param activeAdditionalFields the active, non-system fields configured for the type, in display
 *                                order
 */
public record EffectiveFormFields(Set<String> inactiveValueBindings, List<CustomField> activeAdditionalFields) {
}
