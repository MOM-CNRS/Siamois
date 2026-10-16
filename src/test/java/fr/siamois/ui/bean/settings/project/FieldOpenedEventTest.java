package fr.siamois.ui.bean.settings.project;

import fr.siamois.domain.models.settings.tableconfig.TypeFieldFormConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FieldOpenedEventTest {

    @Test
    void carriesTheFieldThatWasOpened() {
        TypeFieldFormConfig field = TypeFieldFormConfig.builder().id(1L).name("depth").build();

        assertThat(new FieldOpenedEvent(field).field()).isSameAs(field);
    }
}
