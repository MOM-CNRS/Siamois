package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.actionunit.ActionCode;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.SpatialUnitRepository;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionCodeRepository;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActionUnitSeederTest {

    @Mock PersonRepository personRepository;
    @Mock ActionCodeRepository actionCodeRepository;
    @Mock ConceptRepository conceptRepository;
    @Mock InstitutionRepository institutionRepository;
    @Mock SpatialUnitRepository spatialUnitRepository;
    @Mock ActionUnitRepository actionUnitRepository;

    @Captor ArgumentCaptor<ActionUnit> actionUnitCaptor;

    ActionUnitSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new ActionUnitSeeder(
                personRepository,
                actionCodeRepository,
                conceptRepository,
                institutionRepository,
                spatialUnitRepository,
                actionUnitRepository
        );
    }


    private void commonLookupsOk(String vocabExtId, String conceptExtId,
                                 String authorEmail, String institutionIdentifier,
                                 String primaryCode,
                                 Concept concept, Person author, Institution institution, ActionCode code) {
        when(conceptRepository.findConceptByExternalIdIgnoreCase(vocabExtId, conceptExtId))
                .thenReturn(Optional.of(concept));
        when(personRepository.findByEmailIgnoreCase(authorEmail))
                .thenReturn(Optional.of(author));
        when(actionCodeRepository.findById(primaryCode))
                .thenReturn(Optional.of(code));
        when(institutionRepository.findInstitutionByIdentifier(institutionIdentifier))
                .thenReturn(Optional.of(institution));
    }

    @Test
    void seed_creates_whenNotExisting_andSpatialKeysNull() {
        // Given
        Concept concept = new Concept(); concept.setId(1L);
        Person author = new Person();   // set fields if needed
        Institution institution = new Institution(); institution.setId(99L);
        ActionCode code = new ActionCode(); code.setCode("AC001");

        commonLookupsOk("vocA", "conA", "author@x.test", "INST-1", "AC001",
                concept, author, institution, code);

        when(actionUnitRepository.findByFullIdentifier("AU-1"))
                .thenReturn(Optional.empty());

        var begin = OffsetDateTime.parse("2024-01-01T00:00:00Z");
        var end   = OffsetDateTime.parse("2024-12-31T00:00:00Z");

        ActionUnitSeeder.ActionUnitSpecs s = new ActionUnitSeeder.ActionUnitSpecs(
                "AU-1", "Name 1", "ID-1", "AC001",
                "vocA", "conA",
                "author@x.test",
                "INST-1", begin, end,
                null // spatial keys = null
        );

        // When
        seeder.seed(List.of(s));

        // Then
        verify(actionUnitRepository).save(actionUnitCaptor.capture());
        ActionUnit saved = actionUnitCaptor.getValue();

        assertThat(saved.getFullIdentifier()).isEqualTo("AU-1");
        assertThat(saved.getIdentifier()).isEqualTo("ID-1");
        assertThat(saved.getName()).isEqualTo("Name 1");
        assertThat(saved.getPrimaryActionCode()).isEqualTo(code);
        assertThat(saved.getType()).isEqualTo(concept);
        assertThat(saved.getAuthor()).isEqualTo(author);
        assertThat(saved.getCreatedByInstitution()).isEqualTo(institution);
        assertThat(saved.getBeginDate()).isEqualTo(begin);
        assertThat(saved.getEndDate()).isEqualTo(end);
        assertThat(saved.getSpatialContext()).isNotNull().isEmpty();

        verify(spatialUnitRepository, never()).findByNameAndInstitution(anyString(), anyLong());
    }

    @Test
    void seed_doesNotCreate_whenExisting() {
        // Given
        Concept concept = new Concept(); concept.setId(1L);
        Person author = new Person();
        Institution institution = new Institution(); institution.setId(88L);
        ActionCode code = new ActionCode(); code.setCode("AC002");

        commonLookupsOk("vocB", "conB", "a@b.c", "INST-2", "AC002",
                concept, author, institution, code);

        when(actionUnitRepository.findByFullIdentifier("AU-EXIST"))
                .thenReturn(Optional.of(new ActionUnit())); // déjà présent

        ActionUnitSeeder.ActionUnitSpecs s = new ActionUnitSeeder.ActionUnitSpecs(
                "AU-EXIST", "Name X", "IDX", "AC002",
                "vocB", "conB",
                "a@b.c", "INST-2",
                OffsetDateTime.now(), null,
                null
        );

        // When
        seeder.seed(List.of(s));

        // Then
        verify(actionUnitRepository, never()).save(any(ActionUnit.class));
    }

    @Test
    void seed_throws_whenConceptMissing() {
        // Given
        when(conceptRepository.findConceptByExternalIdIgnoreCase("v", "c"))
                .thenReturn(Optional.empty());

        ActionUnitSeeder.ActionUnitSpecs s = new ActionUnitSeeder.ActionUnitSpecs("AU-2", "Name", "ID", "ACX",
                "v", "c",
                "author@x", "INST",
                null, null, null);

        List<ActionUnitSeeder.ActionUnitSpecs> specs = List.of(s);

        // When / Then
        assertThatThrownBy(() -> seeder.seed(specs))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Concept introuvable");

        verifyNoInteractions(personRepository, actionCodeRepository, institutionRepository, spatialUnitRepository, actionUnitRepository);
    }

    @Test
    void seed_throws_whenAuthorMissing() {
        // Given
        Concept concept = new Concept(); concept.setId(1L);
        when(conceptRepository.findConceptByExternalIdIgnoreCase("v", "c"))
                .thenReturn(Optional.of(concept));
        when(personRepository.findByEmailIgnoreCase("missing@x"))
                .thenReturn(Optional.empty());

        ActionUnitSeeder.ActionUnitSpecs s = new ActionUnitSeeder.ActionUnitSpecs("AU-3", "Name", "ID", "ACX",
                "v", "c",
                "missing@x", "INST",
                null, null, null);

        List<ActionUnitSeeder.ActionUnitSpecs> specs = List.of(s);

        assertThatThrownBy(() -> seeder.seed(specs))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Auteur introuvable");

        verifyNoInteractions(actionCodeRepository, institutionRepository, spatialUnitRepository, actionUnitRepository);
    }

    @Test
    void seed_throws_whenActionCodeMissing() {
        // Given
        Concept concept = new Concept(); concept.setId(1L);
        Person author = new Person();

        when(conceptRepository.findConceptByExternalIdIgnoreCase("v", "c"))
                .thenReturn(Optional.of(concept));
        when(personRepository.findByEmailIgnoreCase("a@x")).thenReturn(Optional.of(author));
        when(actionCodeRepository.findById("MISSING")).thenReturn(Optional.empty());

        ActionUnitSeeder.ActionUnitSpecs s = new ActionUnitSeeder.ActionUnitSpecs("AU-4", "Name", "ID", "MISSING",
                "v", "c",
                "a@x", "INST",
                null, null, null);

        List<ActionUnitSeeder.ActionUnitSpecs> specs = List.of(s);

        assertThatThrownBy(() -> seeder.seed(specs))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Action code introuvable");

        verifyNoInteractions(institutionRepository, spatialUnitRepository, actionUnitRepository);
    }

    @Test
    void seed_throws_whenInstitutionMissing() {
        // Given
        Concept concept = new Concept(); concept.setId(1L);
        Person author = new Person();
        ActionCode code = new ActionCode(); code.setCode("ACZ");

        when(conceptRepository.findConceptByExternalIdIgnoreCase("v", "c"))
                .thenReturn(Optional.of(concept));
        when(personRepository.findByEmailIgnoreCase("a@x")).thenReturn(Optional.of(author));
        when(actionCodeRepository.findById("ACZ")).thenReturn(Optional.of(code));
        when(institutionRepository.findInstitutionByIdentifier("MISSING"))
                .thenReturn(Optional.empty());

        ActionUnitSeeder.ActionUnitSpecs s = new ActionUnitSeeder.ActionUnitSpecs("AU-5", "Name", "ID", "ACZ",
                "v", "c",
                "a@x", "MISSING",
                null, null, null);

        List<ActionUnitSeeder.ActionUnitSpecs> specs = List.of(s);

        assertThatThrownBy(() -> seeder.seed(specs))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Institution introuvable");

        verifyNoInteractions(spatialUnitRepository, actionUnitRepository);
    }

    @Test
    void seed_handlesNullSpatialContextKeys_withoutTouchingSpatialRepo() {
        // Given
        Concept concept = new Concept(); concept.setId(1L);
        Person author = new Person();
        Institution institution = new Institution(); institution.setId(77L);
        ActionCode code = new ActionCode(); code.setCode("AC777");

        commonLookupsOk("v77", "c77", "a@77", "INST-77", "AC777",
                concept, author, institution, code);

        when(actionUnitRepository.findByFullIdentifier("AU-77")).thenReturn(Optional.empty());

        ActionUnitSeeder.ActionUnitSpecs s = new ActionUnitSeeder.ActionUnitSpecs("AU-77", "Name77", "ID77", "AC777",
                "v77", "c77",
                "a@77", "INST-77",
                null, null, null);

        List<ActionUnitSeeder.ActionUnitSpecs> specs = List.of(s);

        // When
        seeder.seed(specs);

        // Then
        verify(spatialUnitRepository, never()).findByNameAndInstitution(anyString(), anyLong());
        verify(actionUnitRepository, times(1)).save(any(ActionUnit.class));
    }
}
