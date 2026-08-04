package fr.siamois.domain.models.form.customfield;

import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldMeasurement;
import fr.siamois.domain.models.form.measurement.UnitDefinition;
import fr.siamois.domain.models.vocabulary.Concept;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CustomFieldIdentityTest {

    @Test
    @DisplayName("Two loads of the same measurement field are the same field, unit instance aside")
    void measurementFieldsOfTheSameRowAreEqual() {
        CustomFieldMeasurement loadedForTheForm = measurement(43L, unit(1L), concept(7L));
        CustomFieldMeasurement loadedForTheConfiguration = measurement(43L, unit(1L), concept(7L));

        assertThat(loadedForTheForm)
                .isEqualTo(loadedForTheConfiguration)
                .hasSameHashCodeAs(loadedForTheConfiguration);
        assertThat(Set.of(loadedForTheConfiguration)).contains(loadedForTheForm);
    }

    /** The failing lookup in the logs: same row, and each load had built its own unit instance. */
    @Test
    @DisplayName("A measurement field is found in a set even when its unit is another instance")
    void measurementFieldIsFoundWhateverTheUnitInstance() {
        CustomFieldMeasurement loadedForTheForm = measurement(43L, unit(1L), concept(7L));
        CustomFieldMeasurement loadedForTheConfiguration = measurement(43L, unit(1L), concept(7L));

        assertThat(Set.of(loadedForTheConfiguration)).contains(loadedForTheForm);
        assertThat(loadedForTheForm.getUnit()).isNotSameAs(loadedForTheConfiguration.getUnit());
    }

    @Test
    @DisplayName("Fields of different rows stay distinct")
    void fieldsOfDifferentRowsAreNotEqual() {
        assertThat(measurement(43L, unit(1L), concept(7L)))
                .isNotEqualTo(measurement(44L, unit(1L), concept(7L)));
    }

    /** The same trap in the other subclasses: their own columns must not enter the comparison. */
    @Test
    @DisplayName("Two loads of the same text field are the same field")
    void textFieldsOfTheSameRowAreEqual() {
        CustomFieldText one = CustomFieldText.builder().id(43L).label("Description").isTextArea(true).build();
        CustomFieldText other = CustomFieldText.builder().id(43L).label("Description").isTextArea(false).build();

        assertThat(Set.of(other)).contains(one);
    }

    private static CustomFieldMeasurement measurement(Long id, UnitDefinition unit, Concept concept) {
        return CustomFieldMeasurement.builder()
                .id(id)
                .label("Hauteur Largeur")
                .unit(unit)
                .concept(concept)
                .build();
    }

    private static UnitDefinition unit(Long id) {
        UnitDefinition unit = new UnitDefinition();
        unit.setId(id);
        unit.setSymbol("m");
        return unit;
    }

    private static Concept concept(Long id) {
        Concept concept = new Concept();
        concept.setId(id);
        return concept;
    }

}
