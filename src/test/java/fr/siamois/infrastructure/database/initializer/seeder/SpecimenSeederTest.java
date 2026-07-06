package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.infrastructure.database.repositories.specimen.SpecimenRepository;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpecimenSeederTest {

    @Mock
    InstitutionRepository institutionRepository;
    @Mock
    ConceptRepository conceptRepository;
    @Mock
    SpecimenRepository specimenRepository;
    @Mock
    PersonSeeder personSeeder;
    @Mock
    RecordingUnitSeeder recordingUnitSeeder;
    @Mock
    EntityManager entityManager;

    @InjectMocks
    SpecimenSeeder seeder;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(seeder, "entityManager", entityManager);
    }

    private static final String VOCABULARY_ID = "th240";

    List<SpecimenSeeder.SpecimenSpecs> toInsert = List.of(
            new SpecimenSeeder.SpecimenSpecs(
                    "chartres-C309_01-1100-1",
                    1,
                    new ConceptSeeder.ConceptKey(VOCABULARY_ID, "123456"),
                    new ConceptSeeder.ConceptKey(VOCABULARY_ID, "4286252"),
                    new ConceptSeeder.ConceptKey(VOCABULARY_ID, "4286252"),
                    "author@siamois.fr",
                    "chartres",
                    List.of("author@siamois.fr"),
                    List.of("author@siamois.fr"),
                    OffsetDateTime.of(2012, 6, 22, 0, 0, 0, 0, ZoneOffset.UTC),
                    new RecordingUnitSeeder.RecordingUnitKey("chartres-C309_01-1100", "")
            )
    );

    private void stubCategoryFound() {
        Concept c = new Concept();
        c.setExternalId("4286252");
        when(conceptRepository.findAllByExternalVocabularyIdIgnoreCaseAndExternalIdIgnoreCaseIn(eq(VOCABULARY_ID), anyCollection()))
                .thenReturn(List.of(c));
    }

    private void stubInstitutionFound() {
        Institution inst = new Institution();
        inst.setIdentifier("chartres");
        when(institutionRepository.findAllByIdentifierIn(anyCollection())).thenReturn(List.of(inst));
    }

    private RecordingUnit stubRecordingUnitFound() {
        ActionUnit au = new ActionUnit();
        au.setFullIdentifier("action-full-id");
        RecordingUnit ru = new RecordingUnit();
        ru.setFullIdentifier("chartres-C309_01-1100");
        ru.setActionUnit(au);
        when(recordingUnitSeeder.bulkGetRecordingUnitsFromKeys(anyCollection(), eq(1L)))
                .thenReturn(Map.of(new RecordingUnitSeeder.RecordingUnitKey("chartres-C309_01-1100", ""), ru));
        return ru;
    }

    @Test
    void seed_CategoryDoesNotExist() {
        // conceptRepository left unstubbed -> empty -> category not found
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> seeder.seed(toInsert, 1L));

        assertThat(ex.getMessage()).contains("Concept").contains("introuvable");
    }

    @Test
    void seed_AuthorDoesNotExist() {
        stubCategoryFound();
        when(personSeeder.resolveCached(any(), eq("author@siamois.fr")))
                .thenThrow(new IllegalStateException("Person introuvable"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> seeder.seed(toInsert, 1L));

        assertThat(ex.getMessage()).contains("Person introuvable");
    }

    @Test
    void seed_InstitutionDoesNotExist() {
        stubCategoryFound();
        // institutionRepository left unstubbed -> empty -> institution not found
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> seeder.seed(toInsert, 1L));

        assertThat(ex.getMessage()).contains("Institution introuvable");
    }

    @Test
    void seed_RecordingUnitDoesNotExist() {
        stubCategoryFound();
        stubInstitutionFound();
        // recordingUnitSeeder bulk lookup left unstubbed -> empty -> recording unit not found
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> seeder.seed(toInsert, 1L));

        assertThat(ex.getMessage()).contains("Recording unit introuvable");
    }

    @Test
    void seed_AlreadyExists() {
        stubCategoryFound();
        stubInstitutionFound();
        RecordingUnit ru = stubRecordingUnitFound();

        SpecimenRepository.ExistingSpecimenKey existing = mock(SpecimenRepository.ExistingSpecimenKey.class);
        when(existing.getFullIdentifier()).thenReturn("chartres-C309_01-1100-1");
        when(existing.getRecordingUnitFullIdentifier()).thenReturn(ru.getFullIdentifier());
        when(specimenRepository.findAllKeysByInstitutionIdAndActionUnitFullIdentifier(
                any(), eq(ru.getActionUnit().getFullIdentifier())))
                .thenReturn(List.of(existing));

        seeder.seed(toInsert, 1L);

        verify(specimenRepository, never()).saveAll(any());
    }

    @Test
    void seed_Created() {
        stubCategoryFound();
        stubInstitutionFound();
        stubRecordingUnitFound();
        // specimenRepository bulk-existence lookup left unstubbed -> empty -> not already present

        seeder.seed(toInsert, 1L);

        verify(specimenRepository, times(1)).saveAll(argThat(list -> {
            int count = 0;
            for (var ignored : list) count++;
            return count == 1;
        }));
        verify(entityManager, times(1)).flush();
        verify(entityManager, times(1)).clear();
    }
}
