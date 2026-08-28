package fr.siamois.domain.services;

import fr.siamois.domain.models.exceptions.spatialunit.SpatialUnitNotFoundException;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.dto.entity.SpecimenDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceInstitutionServiceTest {

    @Mock
    private SpatialUnitService spatialUnitService;

    @Mock
    private ActionUnitService actionUnitService;

    @Mock
    private RecordingUnitService recordingUnitService;

    @Mock
    private SpecimenService specimenService;

    @Mock
    private ContainerService containerService;

    @Mock
    private PhaseService phaseService;

    @InjectMocks
    private ResourceInstitutionService resourceInstitutionService;

    private InstitutionDTO institution;

    @BeforeEach
    void setUp() {
        institution = new InstitutionDTO();
        institution.setId(12L);
        institution.setName("Org");
    }

    @Test
    void findInstitutionOf_shouldReturnInstitutionOfSpatialUnit() {
        SpatialUnitDTO spatialUnit = new SpatialUnitDTO();
        spatialUnit.setCreatedByInstitution(institution);
        when(spatialUnitService.findById(4L)).thenReturn(spatialUnit);

        Optional<InstitutionDTO> result = resourceInstitutionService.findInstitutionOf("spatial-unit", 4L);

        assertEquals(Optional.of(institution), result);
    }

    @Test
    void findInstitutionOf_shouldReturnInstitutionOfRecordingUnit() {
        RecordingUnitDTO recordingUnit = new RecordingUnitDTO();
        recordingUnit.setCreatedByInstitution(institution);
        when(recordingUnitService.findById(7L)).thenReturn(recordingUnit);

        Optional<InstitutionDTO> result = resourceInstitutionService.findInstitutionOf("recording-unit", 7L);

        assertEquals(Optional.of(institution), result);
    }

    @Test
    void findInstitutionOf_shouldReturnInstitutionOfSpecimen() {
        SpecimenDTO specimen = new SpecimenDTO();
        specimen.setCreatedByInstitution(institution);
        when(specimenService.findById(9L)).thenReturn(specimen);

        Optional<InstitutionDTO> result = resourceInstitutionService.findInstitutionOf("specimen", 9L);

        assertEquals(Optional.of(institution), result);
    }

    @Test
    void findInstitutionOf_shouldReturnInstitutionOfContainer() {
        ContainerDTO container = new ContainerDTO();
        container.setCreatedByInstitution(institution);
        when(containerService.findById(11L)).thenReturn(container);

        Optional<InstitutionDTO> result = resourceInstitutionService.findInstitutionOf("container", 11L);

        assertEquals(Optional.of(institution), result);
    }

    @Test
    void findInstitutionOf_shouldReturnInstitutionOfPhase() {
        PhaseDTO phase = new PhaseDTO();
        phase.setCreatedByInstitution(institution);
        when(phaseService.findById(13L)).thenReturn(phase);

        Optional<InstitutionDTO> result = resourceInstitutionService.findInstitutionOf("phase", 13L);

        assertEquals(Optional.of(institution), result);
    }

    @Test
    void findInstitutionOf_shouldReturnEmptyWhenIdIsNull() {
        Optional<InstitutionDTO> result = resourceInstitutionService.findInstitutionOf("spatial-unit", null);

        assertTrue(result.isEmpty());
        verifyNoInteractions(spatialUnitService);
    }

    @Test
    void findInstitutionOf_shouldReturnEmptyWhenTypeIsUnknown() {
        Optional<InstitutionDTO> result = resourceInstitutionService.findInstitutionOf("welcome", 1L);

        assertTrue(result.isEmpty());
        verifyNoInteractions(spatialUnitService, actionUnitService, recordingUnitService,
                specimenService, containerService, phaseService);
    }

    @Test
    void findInstitutionOf_shouldReturnEmptyWhenEntityIsMissing() {
        when(containerService.findById(404L)).thenReturn(null);

        Optional<InstitutionDTO> result = resourceInstitutionService.findInstitutionOf("container", 404L);

        assertTrue(result.isEmpty());
    }

    @Test
    void findInstitutionOf_shouldReturnEmptyWhenLookupFails() {
        when(spatialUnitService.findById(404L)).thenThrow(new SpatialUnitNotFoundException("not found"));

        Optional<InstitutionDTO> result = resourceInstitutionService.findInstitutionOf("spatial-unit", 404L);

        assertTrue(result.isEmpty());
    }

}
