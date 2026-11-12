package fr.siamois.ui.bean.dialog.project;

import fr.siamois.dto.entity.FullAddress;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.response.place.PlaceCreatedResponse;
import fr.siamois.ui.api.openapi.v1.response.spatialunit.PlaceSuggestionItemApi;
import lombok.Value;
import org.springframework.lang.Nullable;

import java.io.Serializable;

/**
 * A place offered by the place pickers of the new project dialog: one of the organisation's places, or
 * the suggestion of an external source (an INSEE commune, a GéoPlateforme address) that becomes a place
 * once picked. A class with getters rather than a record: the EL of the JSF page reads bean properties.
 */
@Value
public class PlaceChoice implements Serializable {

    private static final String EXTERNAL_KEY_PREFIX = "external:";

    /** What identifies the choice in the page: the place id, or the source and code of a suggestion. */
    String key;
    @Nullable Long id;
    String name;
    @Nullable String code;
    String source;
    @Nullable String category;
    @Nullable FullAddress address;

    /**
     * The choice a place suggestion stands for.
     *
     * @param item a suggestion of {@code PlaceSuggestionApiService}
     * @return the choice, keyed like the React pickers key it
     */
    public static PlaceChoice of(PlaceSuggestionItemApi item) {
        return new PlaceChoice(keyOf(item), item.id(), item.name(), item.code(), item.source(),
                categoryOf(item.concept()), item.address());
    }

    /**
     * The choice of a place just created from a suggestion: an existing place from now on.
     *
     * @param created    the place created (or found) for the suggestion
     * @param suggestion the suggestion it was created from, which still knows its source and category
     * @return the choice of the created place
     */
    public static PlaceChoice created(PlaceCreatedResponse.PlaceCreatedItem created, PlaceChoice suggestion) {
        return new PlaceChoice(String.valueOf(created.getId()), created.getId(), created.getName(), created.getCode(),
                suggestion.getSource(), suggestion.getCategory(), null);
    }

    /**
     * Whether the choice is still an external suggestion, to be turned into a place before it is used.
     *
     * @return true when the choice is not a place of the organisation yet
     */
    public boolean isExternal() {
        return id == null;
    }

    private static String keyOf(PlaceSuggestionItemApi item) {
        if (item.id() != null) {
            return String.valueOf(item.id());
        }
        return EXTERNAL_KEY_PREFIX + item.source() + ":" + (item.code() != null ? item.code() : item.name());
    }

    @Nullable
    private static String categoryOf(@Nullable ResolvedConceptResource concept) {
        return concept == null ? null : concept.getResolvedLabel();
    }
}
