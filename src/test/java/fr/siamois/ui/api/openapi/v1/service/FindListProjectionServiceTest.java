package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.services.form.CustomFieldAnswerService;
import fr.siamois.domain.services.vocabulary.ConceptLabelBatchResolver;
import fr.siamois.dto.entity.SpecimenDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FindListProjectionServiceTest {

    private final ConceptLabelBatchResolver labels = mock(ConceptLabelBatchResolver.class);
    private final AdditionalAnswersListProjector additional = mock(AdditionalAnswersListProjector.class);
    private final FindListProjectionService service =
            new FindListProjectionService(new SpecimenAnswersProjector(), labels, additional);

    @Test
    void noFieldsParam_meansNoProjectionAtAll() {
        SpecimenDTO dto = new SpecimenDTO();
        dto.setId(1L);

        assertThat(service.build(List.of(dto), null, "fr").answersFor(1L)).isNull();
        verifyNoInteractions(labels, additional);
    }

    @Test
    void additionalAnswersAreMergedForTheSpecimenOwner() {
        SpecimenDTO dto = new SpecimenDTO();
        dto.setId(1L);
        when(additional.merge(any(), eq(CustomFieldAnswerService.ListOwner.SPECIMEN), eq(List.of(1L)), eq("77"), any(), anyString()))
                .thenReturn(Map.of(1L, Map.of("77", "valeur")));

        assertThat(service.build(List.of(dto), "77", "fr").answersFor(1L)).containsEntry("77", "valeur");
    }
}
