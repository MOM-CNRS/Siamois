package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.SpatialUnitRepository;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecordingUnitSeederTest {


    @Mock
    InstitutionSeeder institutionSeeder;
    @Mock
    ConceptSeeder conceptSeeder;
    @Mock
    RecordingUnitRepository recordingUnitRepository;
    @Mock
    SpatialUnitRepository spatialUnitRepository;
    @Mock
    ActionUnitRepository actionUnitRepository;
    @Mock
    PersonSeeder personSeeder;

    @InjectMocks
    RecordingUnitSeeder seeder;

    @Test
    void seed_ConceptDoesNotExist() {

        final String VOCABULARY_ID = "th240";

        List<RecordingUnitSeeder.RecordingUnitSpecs> toInsert = List.of(
                new RecordingUnitSeeder.RecordingUnitSpecs(
                        "chartres-C309_01-1100",
                        1100,
                        new ConceptSeeder.ConceptKey(VOCABULARY_ID, "123456"),
                        new ConceptSeeder.ConceptKey(VOCABULARY_ID, "4287539"),
                        new ConceptSeeder.ConceptKey(VOCABULARY_ID, "4287541"),
                        "author@siamois.fr",
                        "chartres",
                        List.of("author@siamois.fr"),
                        List.of("author@siamois.fr", "author@siamois.fr"),
                        OffsetDateTime.of(2012, 6, 22, 0, 0, 0, 0, ZoneOffset.UTC),
                        null,
                        null,
                        new SpatialUnitSeeder.SpatialUnitKey("Spatial"),
                        new ActionUnitSeeder.ActionUnitKey("action-01")
                )
        );

        when(conceptSeeder.findConceptOrThrow(new ConceptSeeder.ConceptKey("th240", "123456")))
                .thenThrow(new IllegalStateException("Concept introuvable"));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> seeder.seed(toInsert)
        );

        assertThat(ex.getMessage()).contains("Concept introuvable");

    }

    @Test
    void seed_InstitutionDoesNotExist() {

        final String VOCABULARY_ID = "th240";

        List<RecordingUnitSeeder.RecordingUnitSpecs> toInsert = List.of(
                new RecordingUnitSeeder.RecordingUnitSpecs(
                        "chartres-C309_01-1100",
                        1100,
                        new ConceptSeeder.ConceptKey(VOCABULARY_ID, "123456"),
                        new ConceptSeeder.ConceptKey(VOCABULARY_ID, "4287539"),
                        new ConceptSeeder.ConceptKey(VOCABULARY_ID, "4287541"),
                        "author@siamois.fr",
                        "chartres",
                        List.of("author@siamois.fr"),
                        List.of("author@siamois.fr", "author@siamois.fr"),
                        OffsetDateTime.of(2012, 6, 22, 0, 0, 0, 0, ZoneOffset.UTC),
                        null,
                        null,
                        new SpatialUnitSeeder.SpatialUnitKey("Spatial"),
                        new ActionUnitSeeder.ActionUnitKey("action-01")
                )
        );

        when(institutionSeeder.findInstitutionOrReturnNull("chartres"))
                .thenReturn(null);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> seeder.seed(toInsert)
        );

        assertThat(ex.getMessage()).contains("Institution introuvable");

    }

    @Test
    void seed_AuthorsDoesNotExist() {

        final String VOCABULARY_ID = "th240";

        List<RecordingUnitSeeder.RecordingUnitSpecs> toInsert = List.of(
                new RecordingUnitSeeder.RecordingUnitSpecs(
                        "chartres-C309_01-1100",
                        1100,
                        new ConceptSeeder.ConceptKey(VOCABULARY_ID, "123456"),
                        new ConceptSeeder.ConceptKey(VOCABULARY_ID, "4287539"),
                        new ConceptSeeder.ConceptKey(VOCABULARY_ID, "4287541"),
                        "author@siamois.fr",
                        "chartres",
                        List.of("author1@siamois.fr"),
                        List.of("author2@siamois.fr", "author3@siamois.fr"),
                        OffsetDateTime.of(2012, 6, 22, 0, 0, 0, 0, ZoneOffset.UTC),
                        null,
                        null,
                        new SpatialUnitSeeder.SpatialUnitKey("Spatial"),
                        new ActionUnitSeeder.ActionUnitKey("action-01")
                )
        );

        when(conceptSeeder.findConceptOrThrow(new ConceptSeeder.ConceptKey("th240", "123456")))
                .thenReturn(new Concept());
        when(institutionSeeder.findInstitutionOrReturnNull("chartres"))
                .thenReturn(new Institution());
        when(personSeeder.findPersonOrThrow("author@siamois.fr"))
                .thenReturn(new Person());
        when(personSeeder.findPersonOrThrow("author1@siamois.fr"))
                .thenThrow(new IllegalStateException("Person introuvable"));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> seeder.seed(toInsert)
        );

        assertThat(ex.getMessage()).contains("Person introuvable");

    }

    @Test
    void seed_SpatialDoesNotExist() {

        final String VOCABULARY_ID = "th240";

        List<RecordingUnitSeeder.RecordingUnitSpecs> toInsert = List.of(
                new RecordingUnitSeeder.RecordingUnitSpecs(
                        "chartres-C309_01-1100",
                        1100,
                        new ConceptSeeder.ConceptKey(VOCABULARY_ID, "123456"),
                        new ConceptSeeder.ConceptKey(VOCABULARY_ID, "4287539"),
                        new ConceptSeeder.ConceptKey(VOCABULARY_ID, "4287541"),
                        "author@siamois.fr",
                        "chartres",
                        List.of("author1@siamois.fr"),
                        List.of("author2@siamois.fr", "author3@siamois.fr"),
                        OffsetDateTime.of(2012, 6, 22, 0, 0, 0, 0, ZoneOffset.UTC),
                        null,
                        null,
                        new SpatialUnitSeeder.SpatialUnitKey("Spatial"),
                        new ActionUnitSeeder.ActionUnitKey("action-01")
                )
        );

        when(institutionSeeder.findInstitutionOrReturnNull("chartres"))
                .thenReturn(new Institution());

        when(spatialUnitRepository.findByNameAndInstitution("Spatial", null))
                .thenReturn(Optional.empty());

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> seeder.seed(toInsert)
        );

        assertThat(ex.getMessage()).contains("Spatial unit introuvable");

    }

    @Test
    void seed_ActionDoesNotExist() {

        final String VOCABULARY_ID = "th240";

        List<RecordingUnitSeeder.RecordingUnitSpecs> toInsert = List.of(
                new RecordingUnitSeeder.RecordingUnitSpecs(
                        "chartres-C309_01-1100",
                        1100,
                        new ConceptSeeder.ConceptKey(VOCABULARY_ID, "123456"),
                        new ConceptSeeder.ConceptKey(VOCABULARY_ID, "4287539"),
                        new ConceptSeeder.ConceptKey(VOCABULARY_ID, "4287541"),
                        "author@siamois.fr",
                        "chartres",
                        List.of("author1@siamois.fr"),
                        List.of("author2@siamois.fr", "author3@siamois.fr"),
                        OffsetDateTime.of(2012, 6, 22, 0, 0, 0, 0, ZoneOffset.UTC),
                        null,
                        null,
                        new SpatialUnitSeeder.SpatialUnitKey("Spatial"),
                        new ActionUnitSeeder.ActionUnitKey("action-01")
                )
        );

        when(institutionSeeder.findInstitutionOrReturnNull("chartres"))
                .thenReturn(new Institution());

        when(spatialUnitRepository.findByNameAndInstitution("Spatial", null))
                .thenReturn(Optional.of(new SpatialUnit()));

        when(actionUnitRepository.findByFullIdentifier("action-01"))
                .thenReturn(Optional.empty());

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> seeder.seed(toInsert)
        );

        assertThat(ex.getMessage()).contains("Action introuvable");

    }

    @Test
    void seed_AlreadyExists() {

        final String VOCABULARY_ID = "th240";

        List<RecordingUnitSeeder.RecordingUnitSpecs> toInsert = List.of(
                new RecordingUnitSeeder.RecordingUnitSpecs(
                        "chartres-C309_01-1100",
                        1100,
                        new ConceptSeeder.ConceptKey(VOCABULARY_ID, "123456"),
                        new ConceptSeeder.ConceptKey(VOCABULARY_ID, "4287539"),
                        new ConceptSeeder.ConceptKey(VOCABULARY_ID, "4287541"),
                        "author@siamois.fr",
                        "chartres",
                        List.of("author1@siamois.fr"),
                        List.of("author2@siamois.fr", "author3@siamois.fr"),
                        OffsetDateTime.of(2012, 6, 22, 0, 0, 0, 0, ZoneOffset.UTC),
                        null,
                        null,
                        new SpatialUnitSeeder.SpatialUnitKey("Spatial"),
                        new ActionUnitSeeder.ActionUnitKey("action-01")
                )
        );

        when(institutionSeeder.findInstitutionOrReturnNull("chartres"))
                .thenReturn(new Institution());

        when(spatialUnitRepository.findByNameAndInstitution("Spatial", null))
                .thenReturn(Optional.of(new SpatialUnit()));

        when(actionUnitRepository.findByFullIdentifier("action-01"))
                .thenReturn(Optional.of(new ActionUnit()));

        when(recordingUnitRepository.findByFullIdentifier("chartres-C309_01-1100"))
                .thenReturn(Optional.of(new RecordingUnit()));

        seeder.seed(toInsert);

        verify(recordingUnitRepository,never()).save(any(RecordingUnit.class));

    }

    @Test
    void seed_Created() {

        final String VOCABULARY_ID = "th240";

        List<RecordingUnitSeeder.RecordingUnitSpecs> toInsert = List.of(
                new RecordingUnitSeeder.RecordingUnitSpecs(
                        "chartres-C309_01-1100",
                        1100,
                        new ConceptSeeder.ConceptKey(VOCABULARY_ID, "123456"),
                        new ConceptSeeder.ConceptKey(VOCABULARY_ID, "4287539"),
                        new ConceptSeeder.ConceptKey(VOCABULARY_ID, "4287541"),
                        "author@siamois.fr",
                        "chartres",
                        List.of("author1@siamois.fr"),
                        List.of("author2@siamois.fr", "author3@siamois.fr"),
                        OffsetDateTime.of(2012, 6, 22, 0, 0, 0, 0, ZoneOffset.UTC),
                        null,
                        null,
                        new SpatialUnitSeeder.SpatialUnitKey("Spatial"),
                        new ActionUnitSeeder.ActionUnitKey("action-01")
                )
        );

        when(institutionSeeder.findInstitutionOrReturnNull("chartres"))
                .thenReturn(new Institution());

        when(spatialUnitRepository.findByNameAndInstitution("Spatial", null))
                .thenReturn(Optional.of(new SpatialUnit()));

        when(actionUnitRepository.findByFullIdentifier("action-01"))
                .thenReturn(Optional.of(new ActionUnit()));

        when(recordingUnitRepository.findByFullIdentifier("chartres-C309_01-1100"))
                .thenReturn(Optional.empty());

        seeder.seed(toInsert);

        verify(recordingUnitRepository,times(1)).save(any(RecordingUnit.class));

    }


}