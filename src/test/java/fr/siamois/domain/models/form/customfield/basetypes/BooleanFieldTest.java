package fr.siamois.domain.models.form.customfield.basetypes;

import fr.siamois.domain.models.form.customfieldanswer.basetypes.CustomFieldAnswerBoolean;
import fr.siamois.domain.models.form.rules.RuleFieldFamily;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BooleanFieldTest {

    @Test
    void aBooleanFieldDoesNotAllowNoAnswerUnlessAskedTo() {
        assertThat(CustomFieldBoolean.builder().build().isNoAnswerAllowed()).isFalse();
        assertThat(CustomFieldBoolean.builder().allowNoAnswer(true).build().isNoAnswerAllowed()).isTrue();
        assertThat(CustomFieldBoolean.builder().allowNoAnswer(null).build().isNoAnswerAllowed()).isFalse();
    }

    @Test
    void aBooleanFieldHasItsOwnIconAndRuleFamily() {
        CustomFieldBoolean field = CustomFieldBoolean.builder().id(1L).build();

        assertThat(field.getIcon()).contains("check");
        assertThat(RuleFieldFamily.of(field)).isEqualTo(RuleFieldFamily.BOOLEAN);
        assertThat(field).isEqualTo(CustomFieldBoolean.builder().id(1L).build()).hasSameHashCodeAs(CustomFieldBoolean.builder().id(1L).build());
    }

    @Test
    void aBooleanAnswerTakesTrueFalseOrNothing() {
        CustomFieldAnswerBoolean answer = new CustomFieldAnswerBoolean();

        answer.setValue(true);
        assertThat(answer.getValue()).isTrue();
        answer.setValue(false);
        assertThat(answer.getValue()).isFalse();
        answer.setValue(null);
        assertThat(answer.getValue()).isNull();
    }

    @Test
    void aBooleanAnswerRefusesAnythingElse() {
        CustomFieldAnswerBoolean answer = new CustomFieldAnswerBoolean();

        assertThatThrownBy(() -> answer.setValue("yes")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void twoBooleanAnswersAreEqualWhenTheyHoldTheSameValue() {
        CustomFieldAnswerBoolean a = new CustomFieldAnswerBoolean();
        CustomFieldAnswerBoolean b = new CustomFieldAnswerBoolean();
        a.setValue(true);
        b.setValue(true);
        CustomFieldAnswerBoolean c = new CustomFieldAnswerBoolean();
        c.setValue(false);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b).isNotEqualTo(c);
    }
}
