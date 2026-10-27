package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.infrastructure.database.repositories.DocumentRepository;
import fr.siamois.ui.api.openapi.v1.resource.find.FindResource;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FirstImageServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @InjectMocks
    private FirstImageService service;

    private static RecordingUnitResource unit(String id) {
        RecordingUnitResource resource = new RecordingUnitResource();
        resource.setId(id);
        return resource;
    }

    @Test
    void markRecordingUnits_setsTheImageOfEachRowThatHasOneInASingleQuery() {
        RecordingUnitResource withImage = unit("1");
        RecordingUnitResource withoutImage = unit("2");
        when(documentRepository.findFirstImageIdsOfRecordingUnits(any()))
                .thenReturn(List.<Object[]>of(new Object[]{1L, 42L}));

        service.markRecordingUnits(List.of(withImage, withoutImage));

        assertEquals("42", withImage.getFirstImageDocumentId());
        assertNull(withoutImage.getFirstImageDocumentId());
        verify(documentRepository).findFirstImageIdsOfRecordingUnits(List.of(1L, 2L));
    }

    @Test
    void markFinds_usesTheSpecimenLinks() {
        FindResource find = new FindResource();
        find.setId("5");
        when(documentRepository.findFirstImageIdsOfSpecimens(any()))
                .thenReturn(List.<Object[]>of(new Object[]{5L, 7L}));

        service.markFinds(List.of(find));

        assertEquals("7", find.getFirstImageDocumentId());
    }

    @Test
    void mark_doesNothingWhenNoRowHasAnId() {
        RecordingUnitResource unsaved = unit(null);

        service.markRecordingUnits(List.of(unsaved));

        verifyNoInteractions(documentRepository);
        assertNull(unsaved.getFirstImageDocumentId());
    }

    @Test
    void mark_doesNothingForAnEmptyPage() {
        service.markRecordingUnits(List.of());

        verifyNoInteractions(documentRepository);
    }
}
