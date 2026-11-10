package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldInteger;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.ui.api.openapi.v1.resource.form.FieldResource;
import fr.siamois.ui.table.definitions.SystemFieldCatalog;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An integer field is a signed integer (#828): the client lets the minus sign be typed only when the
 * field sends no lower bound at or above zero.
 */
class FieldAnswerWireServiceIntegerBoundsTest {

    @Test
    void anIntegerFieldWithItsDefaultBoundsSendsNoLowerBound() {
        CustomFieldInteger field = CustomFieldInteger.builder().id(42L).isSystemField(false).build();

        FieldResource resource = FieldAnswerWireService.fieldResourceOf(field, "Altitude", null);

        assertThat(resource.constraints()).isNull();
    }

    @Test
    void anIntegerFieldBoundedBelowZeroSendsThatBound() {
        CustomFieldInteger field = CustomFieldInteger.builder().id(42L).isSystemField(false).minValue(-500).build();

        FieldResource resource = FieldAnswerWireService.fieldResourceOf(field, "Altitude", null);

        assertThat(resource.constraints()).isNotNull();
        assertThat(resource.constraints().min()).isEqualTo(-500.0);
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "UE, -315",       // TAQ
            "UE, -316",       // TPQ
            "MOBILIER, -417", // TAQ
            "MOBILIER, -418", // TPQ
            "PHASE, -506",    // borne basse
            "PHASE, -507",    // borne haute
    })
    void aYearFieldAcceptsYearsBeforeOurEra(ConfigurableTable table, long fieldId) {
        CustomField field = SystemFieldCatalog.fieldsOf(table).stream()
                .filter(f -> f.getId() == fieldId)
                .findFirst()
                .orElseThrow();

        FieldResource.Constraints constraints = FieldAnswerWireService.fieldResourceOf(field, "Année", null).constraints();

        assertThat(constraints == null ? null : constraints.min()).isNull();
    }
}
