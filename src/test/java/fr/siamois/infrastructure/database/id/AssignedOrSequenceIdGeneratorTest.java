package fr.siamois.infrastructure.database.id;

import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AssignedOrSequenceIdGeneratorTest {

    @Mock
    private SharedSessionContractImplementor session;

    private final AssignedOrSequenceIdGenerator generator = new AssignedOrSequenceIdGenerator();

    @Test
    void generate_shouldReturnEntityId_whenCustomFieldHasAnAssignedId() {
        CustomFieldText field = new CustomFieldText();
        field.setId(-5L);

        Object generatedId = generator.generate(session, field);

        assertThat(generatedId).isEqualTo(-5L);
        verifyNoInteractions(session);
    }

    @Test
    void generate_shouldFallBackToSequence_whenCustomFieldHasNoId() {
        CustomFieldText field = new CustomFieldText();
        field.setId(null);

        assertThatThrownBy(() -> generator.generate(session, field))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void generate_shouldFallBackToSequence_whenObjectIsNotACustomField() {
        Object notACustomField = new Object();

        assertThatThrownBy(() -> generator.generate(session, notACustomField))
                .isInstanceOf(RuntimeException.class);
    }
}
