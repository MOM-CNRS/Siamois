package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.SpatialUnitRepository;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpatialUnitSeederTest {

    @Mock
    SpatialUnitRepository spatialUnitRepository;
    @Mock
    InstitutionRepository institutionRepository;
    @Mock
    ConceptRepository conceptRepository;
    @Mock
    PersonSeeder personSeeder;

    @InjectMocks
    SpatialUnitSeeder seeder;

    private static final String VOCABULARY_ID = "th240";

    private void stubConceptFound() {
        Concept c = new Concept();
        c.setExternalId("123456");
        when(conceptRepository.findAllByExternalVocabularyIdIgnoreCaseAndExternalIdIgnoreCaseIn(eq(VOCABULARY_ID), anyCollection()))
                .thenReturn(List.of(c));
    }

    private void stubInstitutionFound(long id) {
        Institution i = new Institution();
        i.setId(id);
        i.setIdentifier("test");
        when(institutionRepository.findAllByIdentifierIn(anyCollection())).thenReturn(List.of(i));
    }

    private SpatialUnitSeeder.SpatialUnitSpecs spec(String name, Set<SpatialUnitSeeder.SpatialUnitKey> children) {
        return new SpatialUnitSeeder.SpatialUnitSpecs(name, VOCABULARY_ID, "123456", "author@siamois.fr", "test", children);
    }

    @Test
    void seed_ConceptDoesNotExist() {
        // conceptRepository left unstubbed -> empty -> concept not found
        List<SpatialUnitSeeder.SpatialUnitSpecs> toInsert = List.of(spec("name", Set.of()));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> seeder.seed(toInsert));

        assertThat(ex.getMessage()).contains("Concept introuvable");
    }

    @Test
    void seed_AuthorDoesNotExist() {
        stubConceptFound();
        when(personSeeder.resolveCached(any(), eq("author@siamois.fr")))
                .thenThrow(new IllegalStateException("Auteur introuvable"));

        List<SpatialUnitSeeder.SpatialUnitSpecs> toInsert = List.of(spec("name", Set.of()));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> seeder.seed(toInsert));

        assertThat(ex.getMessage()).contains("Auteur introuvable");
    }

    @Test
    void seed_InstitutionDoesNotExist() {
        stubConceptFound();
        // institutionRepository left unstubbed -> empty -> institution not found
        List<SpatialUnitSeeder.SpatialUnitSpecs> toInsert = List.of(spec("name", Set.of()));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> seeder.seed(toInsert));

        assertThat(ex.getMessage()).contains("Institution introuvable");
    }

    @Test
    void seed_ChildDoesNotExist() {
        stubConceptFound();
        stubInstitutionFound(1L);
        // "Name" isn't the name of any spec in this batch -> child unresolvable
        List<SpatialUnitSeeder.SpatialUnitSpecs> toInsert = List.of(
                spec("name", Set.of(new SpatialUnitSeeder.SpatialUnitKey("Name"))));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> seeder.seed(toInsert));

        assertThat(ex.getMessage()).contains("Enfant introuvable");
    }

    @Test
    void seed_ChildFromSameBatch_resolved() {
        stubConceptFound();
        stubInstitutionFound(1L);
        // "child" appears earlier in the list than "parent" referencing it as a child —
        // this must work regardless of ordering since children are resolved in-memory
        // across the whole batch, not via a DB lookup.
        List<SpatialUnitSeeder.SpatialUnitSpecs> toInsert = List.of(
                spec("parent", Set.of(new SpatialUnitSeeder.SpatialUnitKey("child"))),
                spec("child", Set.of()));

        Map<String, SpatialUnit> res = seeder.seed(toInsert);

        Set<SpatialUnit> children = res.get("parent").getChildren();
        assertThat(children.size()).isEqualTo(1);
        assertThat(children.iterator().next().getName()).isEqualTo("child");
        verify(spatialUnitRepository, times(1)).saveAll(argThat(list -> {
            int count = 0;
            for (var ignored : list) count++;
            return count == 2;
        }));
    }

    @Test
    void seed_AlreadyExists() {
        stubConceptFound();
        stubInstitutionFound(1L);

        SpatialUnit existing = new SpatialUnit();
        existing.setName("name");
        when(spatialUnitRepository.findAllByNameInAndInstitution(anyCollection(), eq(1L))).thenReturn(List.of(existing));

        List<SpatialUnitSeeder.SpatialUnitSpecs> toInsert = List.of(spec("name", Set.of()));

        Map<String, SpatialUnit> res = seeder.seed(toInsert);

        verify(spatialUnitRepository, never()).saveAll(any());
        assertNotNull(res.get("name"));
        assertThat(res.get("name")).isSameAs(existing);
    }

    @Test
    void seed_Created() {
        stubConceptFound();
        stubInstitutionFound(1L);
        // spatialUnitRepository bulk-existence lookup left unstubbed -> empty -> not already present

        List<SpatialUnitSeeder.SpatialUnitSpecs> toInsert = List.of(spec("created", Set.of()));

        Map<String, SpatialUnit> res = seeder.seed(toInsert);

        verify(spatialUnitRepository, times(1)).saveAll(argThat(list -> {
            int count = 0;
            for (var ignored : list) count++;
            return count == 1;
        }));
        assertNotNull(res.get("created"));
    }
}
