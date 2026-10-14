package fr.siamois.domain.services;

import fr.siamois.domain.models.study.Study;
import fr.siamois.dto.entity.StudyDTO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EntityDTORegistryTest {

    @Test
    void aStudyDtoIsBackedByTheStudyEntity() {
        assertThat(new EntityDTORegistry().getEntityClass(StudyDTO.class)).isEqualTo(Study.class);
    }
}
