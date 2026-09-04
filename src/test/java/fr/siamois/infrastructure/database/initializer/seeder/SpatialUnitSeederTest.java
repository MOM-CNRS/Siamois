package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.misc.SeedCounts;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.dataimport.ImportSchema;
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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.argThat;

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

    private SpatialUnitSeeder.SpatialUnitSpecs spec(String name) {
        return new SpatialUnitSeeder.SpatialUnitSpecs(name, VOCABULARY_ID, "123456", "author@siamois.fr", "test");
    }

    @Test
    void seed_ConceptDoesNotExist() {
        // conceptRepository left unstubbed -> empty -> concept not found
        List<SpatialUnitSeeder.SpatialUnitSpecs> toInsert = List.of(spec("name"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> seeder.seed(toInsert));

        assertThat(ex.getMessage()).contains("Concept introuvable");
    }

    @Test
    void seed_AuthorDoesNotExist() {
        stubConceptFound();
        when(personSeeder.resolveCached(any(), eq("author@siamois.fr")))
                .thenThrow(new IllegalStateException("Auteur introuvable"));

        List<SpatialUnitSeeder.SpatialUnitSpecs> toInsert = List.of(spec("name"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> seeder.seed(toInsert));

        assertThat(ex.getMessage()).contains("Auteur introuvable");
    }

    @Test
    void seed_InstitutionDoesNotExist() {
        stubConceptFound();
        // institutionRepository left unstubbed -> empty -> institution not found
        List<SpatialUnitSeeder.SpatialUnitSpecs> toInsert = List.of(spec("name"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> seeder.seed(toInsert));

        assertThat(ex.getMessage()).contains("Institution introuvable");
    }

    @Test
    void seed_AlreadyExists_updatesExistingInstead() {
        stubConceptFound();
        stubInstitutionFound(1L);

        SpatialUnit existing = new SpatialUnit();
        existing.setName("name");
        when(spatialUnitRepository.findAllByNameInAndInstitution(anyCollection(), eq(1L))).thenReturn(List.of(existing));

        List<SpatialUnitSeeder.SpatialUnitSpecs> toInsert = List.of(spec("name"));
        SeedCounts seedCounts = new SeedCounts();

        Map<String, SpatialUnit> res = seeder.seed(toInsert, seedCounts);

        verify(spatialUnitRepository, times(1)).saveAll(argThat(list -> {
            var it = list.iterator();
            return it.hasNext() && it.next() == existing;
        }));
        assertNotNull(res.get("name"));
        assertThat(res.get("name")).isSameAs(existing);
        assertThat(existing.getCategory()).isNotNull();
        SeedCounts.Counts counts = seedCounts.get(ImportSchema.SPATIAL_UNIT);
        assertThat(counts.created()).isZero();
        assertThat(counts.updated()).isEqualTo(1);
        assertThat(counts.skippedDuplicate()).isZero();
    }

    @Test
    void seed_Created() {
        stubConceptFound();
        stubInstitutionFound(1L);
        // spatialUnitRepository bulk-existence lookup left unstubbed -> empty -> not already present

        List<SpatialUnitSeeder.SpatialUnitSpecs> toInsert = List.of(spec("created"));
        SeedCounts seedCounts = new SeedCounts();

        Map<String, SpatialUnit> res = seeder.seed(toInsert, seedCounts);

        verify(spatialUnitRepository, times(1)).saveAll(argThat(list -> {
            int count = 0;
            for (var ignored : list) count++;
            return count == 1;
        }));
        assertNotNull(res.get("created"));
        SeedCounts.Counts counts = seedCounts.get(ImportSchema.SPATIAL_UNIT);
        assertThat(counts.created()).isEqualTo(1);
        assertThat(counts.updated()).isZero();
        assertThat(counts.skippedDuplicate()).isZero();
    }

    @Test
    void seed_InBatchDuplicate_recordedAsSkipped() {
        stubConceptFound();
        stubInstitutionFound(1L);
        // spatialUnitRepository bulk-existence lookup left unstubbed -> empty -> not already present

        List<SpatialUnitSeeder.SpatialUnitSpecs> specs = List.of(spec("dup"), spec("dup"));
        SeedCounts seedCounts = new SeedCounts();

        seeder.seed(specs, seedCounts);

        SeedCounts.Counts counts = seedCounts.get(ImportSchema.SPATIAL_UNIT);
        assertThat(counts.created()).isEqualTo(1);
        assertThat(counts.updated()).isZero();
        assertThat(counts.skippedDuplicate()).isEqualTo(1);
    }
}
