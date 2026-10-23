package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
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
}
