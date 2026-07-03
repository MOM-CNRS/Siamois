package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectDataSeederTest {

    @Mock RecordingUnitSeeder recordingUnitSeeder;
    @Mock SpecimenSeeder specimenSeeder;
    @Mock RecordingUnitRelSeeder recordingUnitRelSeeder;
    @Mock RecordingUnitStratiRelSeeder recordingUnitStratiRelSeeder;
    @Mock SpatialUnitSeeder spatialUnitSeeder;
    @Mock PhaseSeeder phaseSeeder;

    @InjectMocks
    ProjectDataSeeder seeder;

    private ActionUnitDTO project;
    private ImportSpecs emptySpecs;

    @BeforeEach
    void setUp() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(42L);

        project = new ActionUnitDTO();
        project.setFullIdentifier("UA-FULL-001");
        project.setCreatedByInstitution(institution);

        emptySpecs = new ImportSpecs(
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of());
    }

    // ------------------------------------------------------------------
    // All seeders are called
    // ------------------------------------------------------------------

    @Test
    void seedAll_callsAllSixSeeders() {
        seeder.seedAll(emptySpecs, project);

        verify(spatialUnitSeeder).seed(emptySpecs.spatialUnits());
        verify(phaseSeeder).seed(emptySpecs.phaseSpecs());
        verify(recordingUnitSeeder).seed(emptySpecs.recordingUnits());
        verify(specimenSeeder).seed(emptySpecs.specimenSpecs(), 42L);
        verify(recordingUnitStratiRelSeeder).seed(emptySpecs.recordingUnitStratiRelSpecs(), 42L, "UA-FULL-001");
        verify(recordingUnitRelSeeder).seed(emptySpecs.recordingUnitRelSpecs(), 42L, "UA-FULL-001");
    }

    // ------------------------------------------------------------------
    // Correct institution ID and full identifier forwarded
    // ------------------------------------------------------------------

    @Test
    void seedAll_forwardsInstitutionIdToSpecimenSeeder() {
        seeder.seedAll(emptySpecs, project);

        verify(specimenSeeder).seed(any(), eq(42L));
    }

    @Test
    void seedAll_forwardsInstitutionIdAndFullIdentifierToStratiRelSeeder() {
        seeder.seedAll(emptySpecs, project);

        verify(recordingUnitStratiRelSeeder).seed(any(), eq(42L), eq("UA-FULL-001"));
    }

    @Test
    void seedAll_forwardsInstitutionIdAndFullIdentifierToRelSeeder() {
        seeder.seedAll(emptySpecs, project);

        verify(recordingUnitRelSeeder).seed(any(), eq(42L), eq("UA-FULL-001"));
    }

    // ------------------------------------------------------------------
    // Correct list instances forwarded
    // ------------------------------------------------------------------

    @Test
    void seedAll_forwardsCorrectListsToEachSeeder() {
        seeder.seedAll(emptySpecs, project);

        verify(spatialUnitSeeder).seed(same(emptySpecs.spatialUnits()));
        verify(phaseSeeder).seed(same(emptySpecs.phaseSpecs()));
        verify(recordingUnitSeeder).seed(same(emptySpecs.recordingUnits()));
        verify(specimenSeeder).seed(same(emptySpecs.specimenSpecs()), eq(42L));
        verify(recordingUnitStratiRelSeeder).seed(same(emptySpecs.recordingUnitStratiRelSpecs()), anyLong(), anyString());
        verify(recordingUnitRelSeeder).seed(same(emptySpecs.recordingUnitRelSpecs()), anyLong(), anyString());
    }

    // ------------------------------------------------------------------
    // Call order: spatial → phase → recordingUnit → specimen → stratiRel → rel
    // ------------------------------------------------------------------

    @Test
    void seedAll_callsAllSeedersInCorrectOrder() {
        seeder.seedAll(emptySpecs, project);

        InOrder order = inOrder(
                spatialUnitSeeder, phaseSeeder, recordingUnitSeeder,
                specimenSeeder, recordingUnitStratiRelSeeder, recordingUnitRelSeeder);

        order.verify(spatialUnitSeeder).seed(any());
        order.verify(phaseSeeder).seed(any());
        order.verify(recordingUnitSeeder).seed(any());
        order.verify(specimenSeeder).seed(any(), anyLong());
        order.verify(recordingUnitStratiRelSeeder).seed(any(), anyLong(), anyString());
        order.verify(recordingUnitRelSeeder).seed(any(), anyLong(), anyString());
    }

    // ------------------------------------------------------------------
    // Exception from any seeder propagates immediately
    // ------------------------------------------------------------------

    @Test
    void seedAll_spatialUnitSeederThrows_exceptionPropagates() {
        doThrow(new IllegalStateException("spatial error"))
                .when(spatialUnitSeeder).seed(any());

        assertThrows(SeedException.class, () -> seeder.seedAll(emptySpecs, project));

        verifyNoInteractions(phaseSeeder, recordingUnitSeeder, specimenSeeder,
                recordingUnitStratiRelSeeder, recordingUnitRelSeeder);
    }

    @Test
    void seedAll_phaseSeederThrows_exceptionPropagatesAndSubsequentSeedersNotCalled() {
        doThrow(new IllegalStateException("phase error"))
                .when(phaseSeeder).seed(any());

        assertThrows(SeedException.class, () -> seeder.seedAll(emptySpecs, project));

        verify(spatialUnitSeeder).seed(any());
        verifyNoInteractions(recordingUnitSeeder, specimenSeeder,
                recordingUnitStratiRelSeeder, recordingUnitRelSeeder);
    }

    @Test
    void seedAll_recordingUnitSeederThrows_exceptionPropagatesAndSubsequentSeedersNotCalled() {
        doThrow(new IllegalStateException("ru error"))
                .when(recordingUnitSeeder).seed(any());

        assertThrows(SeedException.class, () -> seeder.seedAll(emptySpecs, project));

        verify(spatialUnitSeeder).seed(any());
        verify(phaseSeeder).seed(any());
        verifyNoInteractions(specimenSeeder, recordingUnitStratiRelSeeder, recordingUnitRelSeeder);
    }

    @Test
    void seedAll_specimenSeederThrows_exceptionPropagatesAndSubsequentSeedersNotCalled() {
        doThrow(new IllegalStateException("specimen error"))
                .when(specimenSeeder).seed(any(), anyLong());

        assertThrows(SeedException.class, () -> seeder.seedAll(emptySpecs, project));

        verify(spatialUnitSeeder).seed(any());
        verify(phaseSeeder).seed(any());
        verify(recordingUnitSeeder).seed(any());
        verifyNoInteractions(recordingUnitStratiRelSeeder, recordingUnitRelSeeder);
    }
}
