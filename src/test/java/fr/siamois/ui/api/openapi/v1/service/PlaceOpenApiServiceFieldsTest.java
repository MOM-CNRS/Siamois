package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The place list's columns are the details form's fields; the {@code fields=} parameter picks some.
 */
class PlaceOpenApiServiceFieldsTest {

    private static final String NAME = String.valueOf(SpatialUnit.NAME_FIELD.getId());
    private static final String CODE = String.valueOf(SpatialUnit.CODE_FIELD.getId());
    private static final String TYPE = String.valueOf(SpatialUnit.SPATIAL_UNIT_TYPE_FIELD.getId());
    private static final String NUMBER = String.valueOf(SpatialUnit.PLACE_NUMBER_FIELD.getId());
    private static final String ADDRESS = String.valueOf(SpatialUnit.ADDRESS_FIELD.getId());

    @Test
    void aPlaceListShowsEveryFormFieldButTheAddress() {
        List<String> ids = PlaceOpenApiService.listableFields().stream()
                .map(CustomField::getId).map(String::valueOf).toList();

        assertThat(ids).containsExactlyInAnyOrder(NAME, CODE, TYPE, NUMBER).doesNotContain(ADDRESS);
    }

    @Test
    void noFieldsParameterMeansNoProjection() {
        assertThat(PlaceOpenApiService.requestedFieldIds(null)).isNull();
        assertThat(PlaceOpenApiService.requestedFieldIds("")).isNull();
        assertThat(PlaceOpenApiService.requestedFieldIds("   ")).isNull();
    }

    @Test
    void allMeansEveryListableField() {
        assertThat(PlaceOpenApiService.requestedFieldIds("all")).containsExactlyInAnyOrder(NAME, CODE, TYPE, NUMBER);
        assertThat(PlaceOpenApiService.requestedFieldIds(" ALL ")).containsExactlyInAnyOrder(NAME, CODE, TYPE, NUMBER);
    }

    @Test
    void aListOfIdsKeepsTheKnownOnesAndIgnoresTheRest() {
        Set<String> requested = PlaceOpenApiService.requestedFieldIds(CODE + ", 999999," + NUMBER + "," + ADDRESS);

        // The address has no list value, so it is never projected either.
        assertThat(requested).containsExactly(CODE, NUMBER);
    }

    @Test
    void onlyUnknownIdsProjectNothing() {
        assertThat(PlaceOpenApiService.requestedFieldIds("999999")).isEmpty();
    }
}
