package fr.siamois.ui.bean.panel;

import fr.siamois.dto.entity.SearchResultDTO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchBeanTest {

    @Test
    void aResultOpensTheFicheOfTheEntityItNames() {
        SearchResultDTO recordingUnit = new SearchResultDTO();
        recordingUnit.setRecordingUnitId(4L);
        SearchResultDTO place = new SearchResultDTO();
        place.setSpatialUnitId(7L);
        SearchResultDTO project = new SearchResultDTO();
        project.setActionUnitId(12L);
        SearchResultDTO find = new SearchResultDTO();
        find.setSpecimenId(3L);

        assertThat(SearchBean.resourceUriOf(recordingUnit)).isEqualTo("/recording-unit/4");
        assertThat(SearchBean.resourceUriOf(place)).isEqualTo("/spatial-unit/7");
        assertThat(SearchBean.resourceUriOf(project)).isEqualTo("/action-unit/12");
        assertThat(SearchBean.resourceUriOf(find)).isEqualTo("/specimen/3");
        assertThat(SearchBean.resourceUriOf(new SearchResultDTO())).isNull();
        assertThat(SearchBean.resourceUriOf(null)).isNull();
    }
}
