package fr.siamois.domain.models.spatialunit;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.ui.form.fieldsource.PanelFieldSource;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

class SpatialUnitDetailsFormTest {

    private static Collection<CustomField> fieldsOf(String typeExternalId) {
        return new PanelFieldSource(SpatialUnit.detailsFormFor(typeExternalId)).getAllFields();
    }

    @Test
    void aCommuneHasNoAddress() {
        assertThat(fieldsOf("1164"))
                .contains(SpatialUnit.INSEE_CODE_FIELD)
                .doesNotContain(SpatialUnit.ADDRESS_FIELD, SpatialUnit.ADDRESS_STREET_FIELD, SpatialUnit.PARCEL_SECTION_FIELD);
    }

    @Test
    void aParcelHasItsCadastralReference() {
        assertThat(fieldsOf("1165"))
                .contains(SpatialUnit.PARCEL_SECTION_FIELD, SpatialUnit.PARCEL_NUMBER_FIELD, SpatialUnit.ADDRESS_CITY_FIELD)
                .doesNotContain(SpatialUnit.ADDRESS_STREET_FIELD);
    }

    @Test
    void anAddressHasItsComponents() {
        assertThat(fieldsOf("1167"))
                .contains(SpatialUnit.ADDRESS_STREET_FIELD, SpatialUnit.ADDRESS_POSTCODE_FIELD, SpatialUnit.ADDRESS_CITY_FIELD)
                .doesNotContain(SpatialUnit.PARCEL_SECTION_FIELD);
    }

    @Test
    void anyOtherTypeKeepsTheFullForm() {
        assertThat(SpatialUnit.detailsFormFor("9999")).isSameAs(SpatialUnit.DETAILS_FORM);
        assertThat(SpatialUnit.detailsFormFor(null)).isSameAs(SpatialUnit.DETAILS_FORM);
    }
}
