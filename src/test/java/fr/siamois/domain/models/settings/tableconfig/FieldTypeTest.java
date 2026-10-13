package fr.siamois.domain.models.settings.tableconfig;

import fr.siamois.domain.models.form.customfield.CustomField;
import jakarta.persistence.DiscriminatorValue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldTypeTest {

    /** Every persisted field type must have a constant: the screen can never read a field as another type. */
    @Test
    void everyCustomFieldDiscriminatorHasAFieldType() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(CustomField.class));

        Set<String> discriminators = new TreeSet<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("fr.siamois.domain.models.form.customfield")) {
            Class<?> type = Class.forName(candidate.getBeanClassName());
            DiscriminatorValue value = type.getAnnotation(DiscriminatorValue.class);
            if (value != null) discriminators.add(value.value());
        }

        assertThat(discriminators).isNotEmpty();
        for (String discriminator : discriminators) {
            assertThat(FieldType.fromDiscriminator(discriminator)).as(discriminator).isNotNull();
        }
    }

    @Test
    void aFieldCodeVocabularyIsTheSameTypeAsAVocabulary() {
        assertThat(FieldType.fromDiscriminator("SELECT_ONE_FROM_FIELD_CODE")).isEqualTo(FieldType.SELECT_ONE);
        assertThat(FieldType.fromDiscriminator("SELECT_MULTIPLE_FROM_FIELD_CODE")).isEqualTo(FieldType.SELECT_MULTIPLE);
    }

    @Test
    void anUnknownDiscriminatorIsRefusedInsteadOfReadAsText() {
        assertThatThrownBy(() -> FieldType.fromDiscriminator("HOLOGRAM")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void multipleVariantsPairUpWithTheirSingleSibling() {
        assertThat(FieldType.SELECT_ONE.supportsMultiple()).isTrue();
        assertThat(FieldType.SELECT_ONE.multipleVariant()).isEqualTo(FieldType.SELECT_MULTIPLE);
        assertThat(FieldType.SELECT_MULTIPLE.singleVariant()).isEqualTo(FieldType.SELECT_ONE);
        assertThat(FieldType.SELECT_MULTIPLE.family()).isEqualTo(FieldType.SELECT_ONE);
        assertThat(FieldType.SELECT_MULTIPLE.isMultiple()).isTrue();
        assertThat(FieldType.SELECT_MULTIPLE.supportsMultiple()).isFalse();
    }

    @Test
    void scalarTypesHaveNoMultipleVariant() {
        for (FieldType scalar : new FieldType[]{FieldType.TEXT, FieldType.INTEGER, FieldType.DECIMAL,
                FieldType.DATETIME, FieldType.BOOLEAN, FieldType.MEASUREMENT}) {
            assertThat(scalar.supportsMultiple()).as(scalar.name()).isFalse();
            assertThat(scalar.multipleVariant()).isEqualTo(scalar);
        }
    }

    @Test
    void theCreatableTypesAreTheOnesTheScreenCanBuild() {
        assertThat(FieldType.userCreatableTypes()).containsExactly(FieldType.TEXT, FieldType.INTEGER,
                FieldType.DECIMAL, FieldType.DATETIME, FieldType.BOOLEAN, FieldType.MEASUREMENT,
                FieldType.SELECT_ONE, FieldType.SELECT_MULTIPLE);
    }
}
