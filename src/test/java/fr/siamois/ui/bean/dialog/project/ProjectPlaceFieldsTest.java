package fr.siamois.ui.bean.dialog.project;

import fr.siamois.ui.api.openapi.v1.resource.form.FieldResource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The place fields the new project dialog finds in the project form, and what they narrow. */
class ProjectPlaceFieldsTest {

    @Test
    void of_shouldFindTheMainLocationByItsBinding_andTheSpatialContextByItsAnswerType() {
        FieldResource name = field("1", "Nom", "TEXT", "name");
        FieldResource mainLocation = field("10", "Commune", "SELECT_ONE_SPATIAL_UNIT", "mainLocation");
        FieldResource spatialContext = field("11", "Localisations", "SELECT_MULTIPLE_SPATIAL_UNIT_TREE", "spatialContext");

        ProjectPlaceFields fields = ProjectPlaceFields.of(List.of(name, mainLocation, spatialContext));

        assertThat(fields.getMainLocation()).isEqualTo(new PlaceField(10L, "Commune"));
        assertThat(fields.getSpatialContext()).isEqualTo(new PlaceField(11L, "Localisations"));
    }

    @Test
    void of_shouldOfferNoPlaceField_whenTheFormHasNone() {
        ProjectPlaceFields fields = ProjectPlaceFields.of(List.of(field("1", "Nom", "TEXT", "name")));

        assertThat(fields).isEqualTo(ProjectPlaceFields.NONE);
    }

    @Test
    void of_shouldIgnoreAFieldWhoseIdIsNotNumeric() {
        ProjectPlaceFields fields = ProjectPlaceFields.of(List.of(field("abc", "Commune", "SELECT_ONE_SPATIAL_UNIT", "mainLocation")));

        assertThat(fields.getMainLocation()).isNull();
    }

    @Test
    void dependenciesOn_shouldGiveTheMainLocationPicked_byItsFieldId() {
        ProjectPlaceFields fields = new ProjectPlaceFields(new PlaceField(10L, "Commune"), null);

        assertThat(fields.dependenciesOn(new PlaceChoice("4", 4L, "Lyon", "69123", "SIAMOIS", null, null)))
                .isEqualTo(Map.of(10L, 4L));
    }

    @Test
    void dependenciesOn_shouldGiveNothing_whileTheMainLocationIsNotAPlace() {
        ProjectPlaceFields fields = new ProjectPlaceFields(new PlaceField(10L, "Commune"), null);
        PlaceChoice suggestion = new PlaceChoice("external:INSEE:69123", null, "Lyon", "69123", "INSEE", null, null);

        assertThat(fields.dependenciesOn(null)).isEmpty();
        assertThat(fields.dependenciesOn(suggestion)).isEmpty();
        assertThat(ProjectPlaceFields.NONE.dependenciesOn(new PlaceChoice("4", 4L, "Lyon", null, "SIAMOIS", null, null))).isEmpty();
    }

    private static FieldResource field(String id, String label, String answerType, String valueBinding) {
        FieldResource field = mock(FieldResource.class);
        when(field.id()).thenReturn(id);
        when(field.label()).thenReturn(label);
        when(field.answerType()).thenReturn(answerType);
        when(field.valueBinding()).thenReturn(valueBinding);
        return field;
    }
}
