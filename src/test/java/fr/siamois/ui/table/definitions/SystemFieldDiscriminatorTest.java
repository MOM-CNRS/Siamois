package fr.siamois.ui.table.definitions;

import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.ui.api.openapi.v1.service.FieldAnswerWireService;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every system field SystemFieldInitializer persists gets its type written to
 * {@code custom_field.answer_type}, a varchar(31) (JPA's default discriminator length): a longer
 * answer type fails the insert and takes the whole application startup down with it.
 */
class SystemFieldDiscriminatorTest {

    @Test
    void everyPersistedSystemFieldTypeFitsTheDiscriminatorColumn() {
        assertThat(Arrays.stream(ConfigurableTable.values())
                .flatMap(table -> SystemFieldCatalog.fieldsOf(table).stream())
                .map(FieldAnswerWireService::answerTypeOf))
                .allSatisfy(answerType -> assertThat(answerType).hasSizeLessThanOrEqualTo(31));
    }
}
