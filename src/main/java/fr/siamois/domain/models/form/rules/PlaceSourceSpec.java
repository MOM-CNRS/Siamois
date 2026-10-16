package fr.siamois.domain.models.form.rules;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A source a place field suggests from, besides the organization's own places (INSEE communes,
 * GéoPlateforme addresses…), and what narrows its query: each parameter the source declares can be
 * bound to an attribute of the place picked in another field of the form (the addresses of the
 * commune chosen above).
 *
 * @param source    id of a registered place source
 * @param params    source parameter name → what feeds it
 * @param onMissing what to do when a bound parameter has no value (the other field is empty, or the
 *                  place picked has no such attribute): query without that filter (the default: a broader
 *                  search still helps, and the form invites to fill that field), or leave the source out
 */
public record PlaceSourceSpec(String source, Map<String, ParamBinding> params, OnMissing onMissing) implements Serializable {

    /** The place picked in {@code fromField}, and the attribute of it the parameter takes. */
    public record ParamBinding(long fromField, PlaceAttribute attribute) implements Serializable {
    }

    /** What of a place can feed a source parameter. */
    public enum PlaceAttribute {
        /** The place code: the INSEE code of a commune. */
        CODE,
        NAME,
        /** The postcode of the place's address. */
        POSTCODE
    }

    public enum OnMissing {
        /** The source is queried without that parameter, so the search is broader (the default). */
        UNFILTERED,
        /** The source is not queried. */
        SKIP
    }

    public PlaceSourceSpec {
        params = params == null ? Map.of() : Map.copyOf(params);
        onMissing = onMissing == null ? OnMissing.UNFILTERED : onMissing;
    }

    public static PlaceSourceSpec of(String source) {
        return new PlaceSourceSpec(source, Map.of(), OnMissing.UNFILTERED);
    }

    /** The fields whose value this source reads. */
    public Set<Long> fieldIds() {
        Set<Long> out = new LinkedHashSet<>();
        params.values().forEach(binding -> out.add(binding.fromField()));
        return out;
    }
}
