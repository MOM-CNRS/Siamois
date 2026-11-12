package fr.siamois.ui.bean.dialog.project;

import fr.siamois.ui.api.openapi.v1.resource.form.FieldResource;
import lombok.Value;
import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The place fields of the project form that the new project dialog offers, found the way the React create
 * form finds them: the main location by its value binding, the spatial context by its answer type. Either
 * may be missing from the organisation's form.
 */
@Value
public class ProjectPlaceFields implements Serializable {

    /** No place field: the dialog offers none. */
    public static final ProjectPlaceFields NONE = new ProjectPlaceFields(null, null);

    private static final String MAIN_LOCATION_BINDING = "mainLocation";
    private static final String SPATIAL_CONTEXT_ANSWER_TYPE = "SELECT_MULTIPLE_SPATIAL_UNIT_TREE";

    @Nullable PlaceField mainLocation;
    @Nullable PlaceField spatialContext;

    /**
     * The place fields among the fields of the project form.
     *
     * @param fields the field catalog of the project form
     * @return the place fields found
     */
    public static ProjectPlaceFields of(Collection<FieldResource> fields) {
        PlaceField mainLocation = fields.stream()
                .filter(field -> MAIN_LOCATION_BINDING.equals(field.valueBinding()))
                .findFirst()
                .flatMap(ProjectPlaceFields::placeFieldOf)
                .orElse(null);
        PlaceField spatialContext = fields.stream()
                .filter(field -> SPATIAL_CONTEXT_ANSWER_TYPE.equals(field.answerType()))
                .findFirst()
                .flatMap(ProjectPlaceFields::placeFieldOf)
                .orElse(null);
        return new ProjectPlaceFields(mainLocation, spatialContext);
    }

    /**
     * The places the sources of a place field may be narrowed by: the main location, once it is a place.
     * The server reads only the ones its sources are bound to.
     *
     * @param mainLocationPick the main location picked, if any
     * @return the picked place by field id
     */
    public Map<Long, Long> dependenciesOn(@Nullable PlaceChoice mainLocationPick) {
        if (mainLocation == null || mainLocationPick == null || mainLocationPick.isExternal()) {
            return Map.of();
        }
        return Map.of(mainLocation.getId(), Objects.requireNonNull(mainLocationPick.getId()));
    }

    private static Optional<PlaceField> placeFieldOf(FieldResource field) {
        try {
            return Optional.of(new PlaceField(Long.parseLong(field.id()), field.label()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
