package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.PhaseRepository;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PhaseSeederTest {

    @Mock PhaseRepository phaseRepository;
    @Mock ConceptSeeder conceptSeeder;
    @Mock ActionUnitRepository actionUnitRepository;
    @Mock PersonSeeder personSeeder;

    @Captor ArgumentCaptor<Phase> phaseCaptor;

    @InjectMocks
    PhaseSeeder seeder;

    private static final ActionUnitSeeder.ActionUnitKey AU_KEY =
            new ActionUnitSeeder.ActionUnitKey("UA-001", "INST");

    private ActionUnit actionUnit;
    private Institution institution;

    @BeforeEach
    void setUp() {
        institution = new Institution();
        institution.setId(10L);

        actionUnit = new ActionUnit();
        actionUnit.setId(99L);
        actionUnit.setCreatedByInstitution(institution);
    }

    private PhaseSeeder.PhaseSpecs spec(String identifier,
                                        ConceptSeeder.ConceptKey type,
                                        String authorEmail) {
        return new PhaseSeeder.PhaseSpecs(
                identifier, "Titre test", type, "Description",
                1, 100, 200, authorEmail, AU_KEY);
    }

    // ------------------------------------------------------------------
    // Action unit not found
    // ------------------------------------------------------------------

    @Test
    void seed_actionUnitNotFound_throwsWithProjetIntrouvable() {
        when(actionUnitRepository.findByIdentifierAndCreatedByInstitutionIdentifier("UA-001", "INST"))
                .thenReturn(Optional.empty());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> seeder.seed(List.of(spec("PH-01", null, null))));

        assertThat(ex.getMessage()).contains("Projet introuvable");
    }

    // ------------------------------------------------------------------
    // Already exists → save never called
    // ------------------------------------------------------------------

    @Test
    void seed_alreadyExists_saveNeverCalled() {
        when(actionUnitRepository.findByIdentifierAndCreatedByInstitutionIdentifier("UA-001", "INST"))
                .thenReturn(Optional.of(actionUnit));
        when(phaseRepository.findByIdentifierAndActionUnitId("PH-01", 99L))
                .thenReturn(Optional.of(new Phase()));

        seeder.seed(List.of(spec("PH-01", null, null)));

        verify(phaseRepository, never()).save(any(Phase.class));
    }

    // ------------------------------------------------------------------
    // Created — all fields populated
    // ------------------------------------------------------------------

    @Test
    void seed_created_savesPhaseWithAllFields() {
        ConceptSeeder.ConceptKey typeKey = new ConceptSeeder.ConceptKey("th240", "42");
        Concept concept = new Concept();
        Person author = new Person();

        when(actionUnitRepository.findByIdentifierAndCreatedByInstitutionIdentifier("UA-001", "INST"))
                .thenReturn(Optional.of(actionUnit));
        when(conceptSeeder.findConceptOrThrow(typeKey)).thenReturn(concept);
        when(personSeeder.findOrCreatePerson("author@site.fr")).thenReturn(author);
        when(phaseRepository.findByIdentifierAndActionUnitId("PH-01", 99L))
                .thenReturn(Optional.empty());

        PhaseSeeder.PhaseSpecs s = new PhaseSeeder.PhaseSpecs(
                "PH-01", "Titre", typeKey, "Desc", 2, 500, 1000, "author@site.fr", AU_KEY);
        seeder.seed(List.of(s));

        verify(phaseRepository).save(phaseCaptor.capture());
        Phase saved = phaseCaptor.getValue();
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
    }

    // ------------------------------------------------------------------
    // Type null → phase saved with null type
    // ------------------------------------------------------------------

    @Test
    void seed_typeNull_savedWithNullType() {
        when(actionUnitRepository.findByIdentifierAndCreatedByInstitutionIdentifier("UA-001", "INST"))
                .thenReturn(Optional.of(actionUnit));
        when(phaseRepository.findByIdentifierAndActionUnitId("PH-02", 99L))
                .thenReturn(Optional.empty());

        seeder.seed(List.of(spec("PH-02", null, null)));

        verify(phaseRepository).save(phaseCaptor.capture());
        assertThat(phaseCaptor.getValue().getType()).isNull();
        verifyNoInteractions(conceptSeeder);
    }

    // ------------------------------------------------------------------
    // Concept not found → exception wraps the cause
    // ------------------------------------------------------------------

    @Test
    void seed_conceptNotFound_throwsWrappingConceptError() {
        ConceptSeeder.ConceptKey typeKey = new ConceptSeeder.ConceptKey("th240", "99");

        when(actionUnitRepository.findByIdentifierAndCreatedByInstitutionIdentifier("UA-001", "INST"))
                .thenReturn(Optional.of(actionUnit));
        when(conceptSeeder.findConceptOrThrow(typeKey))
                .thenThrow(new IllegalStateException("Concept introuvable"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> seeder.seed(List.of(spec("PH-03", typeKey, null))));

        assertThat(ex.getMessage()).contains("Concept introuvable");
    }

    // ------------------------------------------------------------------
    // Author null → phase saved with null author
    // ------------------------------------------------------------------

    @Test
    void seed_authorEmailNull_savedWithNullAuthor() {
        when(actionUnitRepository.findByIdentifierAndCreatedByInstitutionIdentifier("UA-001", "INST"))
                .thenReturn(Optional.of(actionUnit));
        when(phaseRepository.findByIdentifierAndActionUnitId("PH-04", 99L))
                .thenReturn(Optional.empty());

        seeder.seed(List.of(spec("PH-04", null, null)));

        verify(phaseRepository).save(phaseCaptor.capture());
        assertThat(phaseCaptor.getValue().getAuthor()).isNull();
        assertThat(phaseCaptor.getValue().getCreatedBy()).isNull();
        verifyNoInteractions(personSeeder);
    }

    @Test
    void seed_authorEmailBlank_savedWithNullAuthor() {
        when(actionUnitRepository.findByIdentifierAndCreatedByInstitutionIdentifier("UA-001", "INST"))
                .thenReturn(Optional.of(actionUnit));
        when(phaseRepository.findByIdentifierAndActionUnitId("PH-05", 99L))
                .thenReturn(Optional.empty());

        seeder.seed(List.of(spec("PH-05", null, "   ")));

        verify(phaseRepository).save(phaseCaptor.capture());
        assertThat(phaseCaptor.getValue().getAuthor()).isNull();
        verifyNoInteractions(personSeeder);
    }

    // ------------------------------------------------------------------
    // Author resolved → set on both author and createdBy
    // ------------------------------------------------------------------

    @Test
    void seed_authorEmailPresent_authorAndCreatedBySetToPerson() {
        Person author = new Person();
        when(actionUnitRepository.findByIdentifierAndCreatedByInstitutionIdentifier("UA-001", "INST"))
                .thenReturn(Optional.of(actionUnit));
        when(personSeeder.findOrCreatePerson("user@example.fr")).thenReturn(author);
        when(phaseRepository.findByIdentifierAndActionUnitId("PH-06", 99L))
                .thenReturn(Optional.empty());

        seeder.seed(List.of(spec("PH-06", null, "user@example.fr")));

        verify(phaseRepository).save(phaseCaptor.capture());
        Phase saved = phaseCaptor.getValue();
        assertThat(saved.getAuthor()).isSameAs(author);
        assertThat(saved.getCreatedBy()).isSameAs(author);
    }

    // ------------------------------------------------------------------
    // Error message format
    // ------------------------------------------------------------------

    @Test
    void seed_errorIncludesLineNumberAndIdentifier() {
        when(actionUnitRepository.findByIdentifierAndCreatedByInstitutionIdentifier("UA-001", "INST"))
                .thenReturn(Optional.empty());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> seeder.seed(List.of(spec("PH-ERR", null, null))));

        assertThat(ex.getMessage()).contains("[Phase ligne 1]");
        assertThat(ex.getMessage()).contains("PH-ERR");
    }

    // ------------------------------------------------------------------
    // Multiple specs — each processed independently
    // ------------------------------------------------------------------

    @Test
    void seed_multipleSpecs_twoCreatedOneSaved() {
        when(actionUnitRepository.findByIdentifierAndCreatedByInstitutionIdentifier("UA-001", "INST"))
                .thenReturn(Optional.of(actionUnit));
        when(phaseRepository.findByIdentifierAndActionUnitId("PH-A", 99L))
                .thenReturn(Optional.empty());
        when(phaseRepository.findByIdentifierAndActionUnitId("PH-B", 99L))
                .thenReturn(Optional.of(new Phase()));   // already exists

        seeder.seed(List.of(spec("PH-A", null, null), spec("PH-B", null, null)));

        verify(phaseRepository, times(1)).save(any(Phase.class));
    }
}
