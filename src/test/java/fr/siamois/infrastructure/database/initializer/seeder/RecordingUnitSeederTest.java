package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.PhaseRepository;
import fr.siamois.infrastructure.database.repositories.SpatialUnitRepository;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecordingUnitSeederTest {


    @Mock
    RecordingUnitRepository recordingUnitRepository;
    @Mock
    SpatialUnitRepository spatialUnitRepository;
    @Mock
    ActionUnitRepository actionUnitRepository;
    @Mock
    PersonSeeder personSeeder;
    @Mock
    PhaseRepository phaseRepository;
    @Mock
    InstitutionRepository institutionRepository;
    @Mock
    ConceptRepository conceptRepository;
    @Mock
    EntityManager entityManager;

    @InjectMocks
    RecordingUnitSeeder seeder;

    @BeforeEach
    void setUp() {
        // @PersistenceContext field, not part of the Lombok constructor -- @InjectMocks doesn't wire it
        ReflectionTestUtils.setField(seeder, "entityManager", entityManager);
    }

    private static final String VOCABULARY_ID = "th240";

    /** Stubs concept resolution (type/geoCycle/geoAgent/interpretation) as all found — used by tests exercising a later failure point. */
    private void stubConceptsFound() {
        List<Concept> found = new ArrayList<>();
        for (String id : List.of("123456", "4287539", "4287541")) {
            Concept c = new Concept();
            c.setExternalId(id);
            found.add(c);
        }
        when(conceptRepository.findAllByExternalVocabularyIdIgnoreCaseAndExternalIdIgnoreCaseIn(eq(VOCABULARY_ID), anyCollection()))
                .thenReturn(found);
    }

    private void stubInstitutionFound() {
        Institution inst = new Institution();
        inst.setIdentifier("chartres");
        when(institutionRepository.findAllByIdentifierIn(anyCollection())).thenReturn(List.of(inst));
    }

    private void stubSpatialUnitFound() {
        SpatialUnit su = new SpatialUnit();
        su.setName("Spatial");
        when(spatialUnitRepository.findAllByNameInAndInstitution(anyCollection(), any())).thenReturn(List.of(su));
    }

    private void stubActionUnitFound() {
        ActionUnit au = new ActionUnit();
        au.setFullIdentifier("action-01");
        when(actionUnitRepository.findAllByIdentifierInAndCreatedByInstitutionIdentifier(anyCollection(), eq("chartres")))
                .thenReturn(List.of(au));
    }

    private List<RecordingUnitSeeder.RecordingUnitSpecs> oneSpec() {
        return List.of(
                new RecordingUnitSeeder.RecordingUnitSpecs(
                        "chartres-C309_01-1100",
                        1100,
                        new ConceptSeeder.ConceptKey(VOCABULARY_ID, "123456"),
                        new ConceptSeeder.ConceptKey(VOCABULARY_ID, "4287539"),
                        new ConceptSeeder.ConceptKey(VOCABULARY_ID, "4287541"),
                        new ConceptSeeder.ConceptKey(VOCABULARY_ID, "4287541"),
                        "author@siamois.fr",
                        "chartres",
                        "author@siamois.fr",
                        "author@siamois.fr",
                        List.of("author2@siamois.fr", "author3@siamois.fr"),
                        OffsetDateTime.of(2012, 6, 22, 0, 0, 0, 0, ZoneOffset.UTC),
                        null,
                        null,
                        new SpatialUnitSeeder.SpatialUnitKey("Spatial"),
                        new ActionUnitSeeder.ActionUnitKey("action-01", "chartres"),
                        "",
                        "",
                        "",
                        "",
                        null
                )
        );
    }

    @Test
    void seed_ConceptDoesNotExist() {
        // conceptRepository left unstubbed -> returns empty list by default -> concept not found
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> seeder.seed(oneSpec())
        );

        assertThat(ex.getMessage()).contains("Concept").contains("introuvable");
    }

    @Test
    void seed_InstitutionDoesNotExist() {
        stubConceptsFound();
        // institutionRepository left unstubbed -> returns empty list by default -> institution not found

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> seeder.seed(oneSpec())
        );

        assertThat(ex.getMessage()).contains("Institution introuvable");
    }

    @Test
    void seed_AuthorDoesNotExist() {
        stubConceptsFound();
        stubInstitutionFound();

        when(personSeeder.resolveCached(any(), eq("author@siamois.fr")))
                .thenThrow(new IllegalStateException("Person introuvable"));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> seeder.seed(oneSpec())
        );

        assertThat(ex.getMessage()).contains("Person introuvable");
    }

    @Test
    void seed_SpatialDoesNotExist() {
        stubConceptsFound();
        stubInstitutionFound();
        // spatialUnitRepository left unstubbed -> returns empty list by default -> spatial unit not found

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> seeder.seed(oneSpec())
        );

        assertThat(ex.getMessage()).contains("Lieu Spatial introuvable");
    }

    @Test
    void seed_ActionDoesNotExist() {
        stubConceptsFound();
        stubInstitutionFound();
        stubSpatialUnitFound();
        // actionUnitRepository left unstubbed -> returns empty list by default -> action unit not found

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> seeder.seed(oneSpec())
        );

        assertThat(ex.getMessage()).contains("Action introuvable");
    }

    @Test
    void seed_AlreadyExists() {
        stubConceptsFound();
        stubInstitutionFound();
        stubSpatialUnitFound();
        stubActionUnitFound();

        RecordingUnit existing = new RecordingUnit();
        existing.setFullIdentifier("chartres-C309_01-1100");
        when(recordingUnitRepository.findAllByFullIdentifierInAndInstitutionIdAndActionUnitFullIdentifier(
                anyCollection(), any(), eq("action-01")))
                .thenReturn(List.of(existing));

        seeder.seed(oneSpec());

        verify(recordingUnitRepository, never()).saveAll(any());
    }

    @Test
    void seed_Created() {
        stubConceptsFound();
        stubInstitutionFound();
        stubSpatialUnitFound();
        stubActionUnitFound();
        // recordingUnitRepository bulk-existence lookup left unstubbed -> empty -> not already present

        seeder.seed(oneSpec());

        verify(recordingUnitRepository, times(1)).saveAll(argThat(list -> {
            List<RecordingUnit> asList = new ArrayList<>();
            list.forEach(asList::add);
            return asList.size() == 1;
        }));
        verify(entityManager, times(1)).flush();
        verify(entityManager, times(1)).clear();
    }

    @Test
    void getRecordingUnitFromKey_existingRecordingUnit_returnsIt() {
        // Given
        RecordingUnitSeeder.RecordingUnitKey key =
                new RecordingUnitSeeder.RecordingUnitKey("RU-001", "");

        RecordingUnit recordingUnit = new RecordingUnit(); recordingUnit.setFullIdentifier("RU-001");

        when(recordingUnitRepository.findByFullIdentifierAndInstitutionIdAndActionUnitFullIdentifier("RU-001", null, ""))
                .thenReturn(Optional.of(recordingUnit));

        // When
        RecordingUnit result = seeder.getRecordingUnitFromKey(key, null);

        // Then
        assertThat(result).isSameAs(recordingUnit);

        verify(recordingUnitRepository)
                .findByFullIdentifierAndInstitutionIdAndActionUnitFullIdentifier("RU-001", null, "");
    }

    @Test
    void getRecordingUnitFromKey_missingRecordingUnit_throwsException() {
        // Given
        RecordingUnitSeeder.RecordingUnitKey key =
                new RecordingUnitSeeder.RecordingUnitKey("RU-404", "");

        when(recordingUnitRepository.findByFullIdentifierAndInstitutionIdAndActionUnitFullIdentifier("RU-404", 1L, ""))
                .thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> seeder.getRecordingUnitFromKey(key, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Recording unit introuvable");

        verify(recordingUnitRepository)
                .findByFullIdentifierAndInstitutionIdAndActionUnitFullIdentifier("RU-404", 1L, "");
    }


}
