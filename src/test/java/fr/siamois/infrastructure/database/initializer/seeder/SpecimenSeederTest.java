package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.infrastructure.database.repositories.specimen.SpecimenRepository;
import org.junit.jupiter.api.BeforeEach;
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
class SpecimenSeederTest {

    @Mock
    InstitutionSeeder institutionSeeder;
    @Mock
    ConceptSeeder conceptSeeder;
    @Mock
    SpecimenRepository specimenRepository;
    @Mock
    PersonSeeder personSeeder;
    @Mock
    RecordingUnitSeeder recordingUnitSeeder;

    @InjectMocks
    SpecimenSeeder seeder;

    List<SpecimenSeeder.SpecimenSpecs> toInsert;

    @BeforeEach
    void setUp() {
        toInsert = List.of(
                new SpecimenSeeder.SpecimenSpecs(
                        "chartres-C309_01-1100-1",
                        1,
                        new ConceptSeeder.ConceptKey("th240", "123456"),
                        new ConceptSeeder.ConceptKey("th240", "4286252"),
                        new ConceptSeeder.ConceptKey("th240", "4286252"),
                        "author@siamois.fr",
                        "chartres",
                        List.of("author@siamois.fr"),
                        List.of("author@siamois.fr"),
                        OffsetDateTime.of(2012, 6, 22, 0, 0, 0, 0, ZoneOffset.UTC),
                        new RecordingUnitSeeder.RecordingUnitKey("chartres-C309_01-1100", "")
                )
        );
    }


    @Test
    void seed_AuthorDoesNotExist() {


        when(personSeeder.findOrCreatePerson("author@siamois.fr"))
                .thenThrow(new IllegalStateException("Person introuvable"));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> seeder.seed(toInsert,1L)
        );

        assertThat(ex.getMessage()).contains("Person introuvable");

    }

    @Test
    void seed_InstitutionDoesNotExist() {

        when(institutionSeeder.findInstitutionOrReturnNull("chartres"))
                .thenReturn(null);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> seeder.seed(toInsert,1L)
        );

        assertThat(ex.getMessage()).contains("Institution introuvable");


    }

    @Test
    void seed_AlreadyExists() {

        ActionUnit au = new ActionUnit();
        au.setFullIdentifier("action-full-id");
        RecordingUnit ru = new RecordingUnit();
        ru.setFullIdentifier("chartres-C309_01-1100");
        ru.setActionUnit(au);

        when(institutionSeeder.findInstitutionOrReturnNull("chartres"))
                .thenReturn(new Institution());
        when(recordingUnitSeeder.getRecordingUnitFromKey(new RecordingUnitSeeder.RecordingUnitKey("chartres-C309_01-1100",""),1L))
                .thenReturn(ru);
        when(specimenRepository.findByFullIdentifierAndInstitutionIdAndRecordingUnitFullIdentifierAndActionUnitFullIdentifier(
                "chartres-C309_01-1100-1", null, "chartres-C309_01-1100", "action-full-id"))
                .thenReturn(Optional.of(new Specimen()));

        seeder.seed(toInsert,1L);

        verify(specimenRepository,never()).save(any(Specimen.class));

    }

    @Test
    void seed_Created() {

        ActionUnit au = new ActionUnit();
        au.setFullIdentifier("action-full-id");
        RecordingUnit returned = new RecordingUnit();
        returned.setFullIdentifier("chartres-C309_01-1100");
        returned.setActionUnit(au);

        when(institutionSeeder.findInstitutionOrReturnNull("chartres"))
                .thenReturn(new Institution());
        when(recordingUnitSeeder.getRecordingUnitFromKey(new RecordingUnitSeeder.RecordingUnitKey("chartres-C309_01-1100",""),1L))
                .thenReturn(returned);

        seeder.seed(toInsert,1L);

        verify(specimenRepository,times(1)).save(any(Specimen.class));

    }

}