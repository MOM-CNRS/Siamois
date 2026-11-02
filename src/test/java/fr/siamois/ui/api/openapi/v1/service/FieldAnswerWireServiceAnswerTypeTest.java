package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldGeometry;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.settings.tableconfig.FieldType;
import fr.siamois.ui.api.openapi.v1.resource.form.FieldResource;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectMultipleFromFieldCode;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOne;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A vocabulary field is SELECT_ONE or SELECT_MULTIPLE on the wire, whatever its list comes from. */
class FieldAnswerWireServiceAnswerTypeTest {

    @Test
    void answerTypeOf_foldsAFieldCodeFieldIntoSelectOne() {
        assertThat(FieldAnswerWireService.answerTypeOf(new CustomFieldSelectOneFromFieldCode())).isEqualTo("SELECT_ONE");
    }

    @Test
    void answerTypeOf_foldsAMultipleFieldCodeFieldIntoSelectMultiple() {
        assertThat(FieldAnswerWireService.answerTypeOf(new CustomFieldSelectMultipleFromFieldCode())).isEqualTo("SELECT_MULTIPLE");
    }

    @Test
    void answerTypeOf_leavesTheOtherTypesAlone() {
        assertThat(FieldAnswerWireService.answerTypeOf(new CustomFieldSelectOne())).isEqualTo("SELECT_ONE");
        assertThat(FieldAnswerWireService.answerTypeOf(new CustomFieldText())).isEqualTo("TEXT");
    }

    @Test
    void aGeometryIsItsOwnReadOnlyType_answeredWithItsWkt() {
        CustomFieldGeometry geometry = CustomFieldGeometry.builder().id(-332L).isSystemField(true).valueBinding("geom").build();

        FieldResource resource = FieldAnswerWireService.fieldResourceOf(geometry, "Géométrie", null);

        assertThat(resource.answerType()).isEqualTo("GEOMETRY");
        assertThat(resource.readOnly()).isTrue();
        assertThat(FieldType.fromDiscriminator("GEOMETRY")).isEqualTo(FieldType.GEOMETRY);
    }

    @Test
    void aTextFieldIsNotMadeReadOnly() {
        CustomFieldText text = CustomFieldText.builder().id(-321L).isSystemField(true).valueBinding("comments").build();

        assertThat(FieldAnswerWireService.fieldResourceOf(text, "Commentaires", null).readOnly()).isNull();
    }
}
