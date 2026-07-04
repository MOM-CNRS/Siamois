package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.PhaseRepository;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PhaseSeederTest {

    @Mock PhaseRepository phaseRepository;
    @Mock ConceptRepository conceptRepository;
    @Mock ActionUnitRepository actionUnitRepository;
    @Mock PersonSeeder personSeeder;
    @Mock EntityManager entityManager;

    @Captor ArgumentCaptor<Iterable<Phase>> phasesCaptor;

    @InjectMocks
    PhaseSeeder seeder;

    private static final ActionUnitSeeder.ActionUnitKey AU_KEY =
            new ActionUnitSeeder.ActionUnitKey("UA-001", "INST");

    private ActionUnit actionUnit;
    private Institution institution;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(seeder, "entityManager", entityManager);

        institution = new Institution();
        institution.setId(10L);

        actionUnit = new ActionUnit();
        actionUnit.setId(99L);
        actionUnit.setFullIdentifier("UA-001");
        actionUnit.setCreatedByInstitution(institution);
    }

    private PhaseSeeder.PhaseSpecs spec(String identifier,
                                        ConceptSeeder.ConceptKey type,
                                        String authorEmail) {
        return new PhaseSeeder.PhaseSpecs(
                identifier, "Titre test", type, "Description",
                1, 100, 200, authorEmail, AU_KEY);
    }

    private void stubActionUnitFound() {
        when(actionUnitRepository.findAllByIdentifierInAndCreatedByInstitutionIdentifier(anyCollection(), eq("INST")))
                .thenReturn(List.of(actionUnit));
    }

    private Phase savedPhase() {
        verify(phaseRepository).saveAll(phasesCaptor.capture());
        return phasesCaptor.getValue().iterator().next();
    }

    // ------------------------------------------------------------------
    // Action unit not found
    // ------------------------------------------------------------------

    @Test
    void seed_actionUnitNotFound_throwsWithProjetIntrouvable() {
        // actionUnitRepository left unstubbed -> empty -> action unit not found
        var specs = List.of(spec("PH-01", null, null));
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> seeder.seed(specs));

        assertThat(ex.getMessage()).contains("Projet introuvable");
    }

    // ------------------------------------------------------------------
    // Already exists → saveAll never called
    // ------------------------------------------------------------------

    @Test
    void seed_alreadyExists_saveNeverCalled() {
        stubActionUnitFound();
        Phase existing = new Phase();
        existing.setIdentifier("PH-01");
        when(phaseRepository.findAllByIdentifierInAndActionUnitId(anyCollection(), eq(99L)))
                .thenReturn(List.of(existing));

        seeder.seed(List.of(spec("PH-01", null, null)));

        verify(phaseRepository, never()).saveAll(any());
    }

    // ------------------------------------------------------------------
    // Created — all fields populated
    // ------------------------------------------------------------------

    @Test
    void seed_created_savesPhaseWithAllFields() {
        stubActionUnitFound();
        ConceptSeeder.ConceptKey typeKey = new ConceptSeeder.ConceptKey("th240", "42");
        Concept concept = new Concept();
        concept.setExternalId("42");
        Person author = new Person();

        when(conceptRepository.findAllByExternalVocabularyIdIgnoreCaseAndExternalIdIgnoreCaseIn(eq("th240"), anyCollection()))
                .thenReturn(List.of(concept));
        when(personSeeder.resolveCached(any(), eq("author@site.fr"))).thenReturn(author);
        // phaseRepository bulk-existence lookup left unstubbed -> empty -> not already present

        PhaseSeeder.PhaseSpecs s = new PhaseSeeder.PhaseSpecs(
                "PH-01", "Titre", typeKey, "Desc", 2, 500, 1000, "author@site.fr", AU_KEY);
        seeder.seed(List.of(s));

        Phase saved = savedPhase();
        assertThat(saved.getIdentifier()).isEqualTo("PH-01");
        assertThat(saved.getTitle()).isEqualTo("Titre");
        assertThat(saved.getActionUnit()).isSameAs(actionUnit);
        assertThat(saved.getType()).isSameAs(concept);
        assertThat(saved.getDescription()).isEqualTo("Desc");
        assertThat(saved.getOrderNumber()).isEqualTo(2);
        assertThat(saved.getLowerBound()).isEqualTo(500);
        assertThat(saved.getUpperBound()).isEqualTo(1000);
        assertThat(saved.getCreatedByInstitution()).isSameAs(institution);
        assertThat(saved.getAuthor()).isSameAs(author);
        assertThat(saved.getCreatedBy()).isSameAs(author);
        verify(entityManager, times(1)).flush();
        verify(entityManager, times(1)).clear();
    }

    // ------------------------------------------------------------------
    // Type null → phase saved with null type
    // ------------------------------------------------------------------

    @Test
    void seed_typeNull_savedWithNullType() {
        stubActionUnitFound();

        seeder.seed(List.of(spec("PH-02", null, null)));

        assertThat(savedPhase().getType()).isNull();
        verifyNoInteractions(conceptRepository);
    }

    // ------------------------------------------------------------------
    // Concept not found → exception wraps the cause
    // ------------------------------------------------------------------

    @Test
    void seed_conceptNotFound_throwsWrappingConceptError() {
        stubActionUnitFound();
        ConceptSeeder.ConceptKey typeKey = new ConceptSeeder.ConceptKey("th240", "99");
        // conceptRepository left unstubbed -> empty -> concept not found

        var specs = List.of(spec("PH-03", typeKey, null));
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> seeder.seed(specs));

        assertThat(ex.getMessage()).contains("Concept").contains("introuvable");
    }

    // ------------------------------------------------------------------
    // Author null → phase saved with null author
    // ------------------------------------------------------------------

    @Test
    void seed_authorEmailNull_savedWithNullAuthor() {
        stubActionUnitFound();

        seeder.seed(List.of(spec("PH-04", null, null)));

        Phase saved = savedPhase();
        assertThat(saved.getAuthor()).isNull();
        assertThat(saved.getCreatedBy()).isNull();
        verify(personSeeder, never()).resolveCached(any(), any());
    }

    @Test
    void seed_authorEmailBlank_savedWithNullAuthor() {
        stubActionUnitFound();

        seeder.seed(List.of(spec("PH-05", null, "   ")));

        assertThat(savedPhase().getAuthor()).isNull();
        verify(personSeeder, never()).resolveCached(any(), any());
    }

    // ------------------------------------------------------------------
    // Author resolved → set on both author and createdBy
    // ------------------------------------------------------------------

    @Test
    void seed_authorEmailPresent_authorAndCreatedBySetToPerson() {
        stubActionUnitFound();
        Person author = new Person();
        when(personSeeder.resolveCached(any(), eq("user@example.fr"))).thenReturn(author);

        seeder.seed(List.of(spec("PH-06", null, "user@example.fr")));

        Phase saved = savedPhase();
        assertThat(saved.getAuthor()).isSameAs(author);
        assertThat(saved.getCreatedBy()).isSameAs(author);
    }

    // ------------------------------------------------------------------
    // Error message format
    // ------------------------------------------------------------------

    @Test
    void seed_errorIncludesLineNumberAndIdentifier() {
        // actionUnitRepository left unstubbed -> empty -> action unit not found
        var specs = List.of(spec("PH-ERR", null, null));
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> seeder.seed(specs));

        assertThat(ex.getMessage()).contains("[Phase ligne 1]");
        assertThat(ex.getMessage()).contains("PH-ERR");
    }

    // ------------------------------------------------------------------
    // Multiple specs — each processed independently
    // ------------------------------------------------------------------

    @Test
    void seed_multipleSpecs_twoCreatedOneSaved() {
        stubActionUnitFound();
        Phase existingB = new Phase();
        existingB.setIdentifier("PH-B");
        when(phaseRepository.findAllByIdentifierInAndActionUnitId(anyCollection(), eq(99L)))
                .thenReturn(List.of(existingB));   // PH-B already exists

        seeder.seed(List.of(spec("PH-A", null, null), spec("PH-B", null, null)));

        verify(phaseRepository, times(1)).saveAll(argThat(list -> {
            int count = 0;
            for (var ignored : list) count++;
            return count == 1;
        }));
    }
}
