package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.SpatialUnitRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpatialUnitSeederTest {

    @Mock
    SpatialUnitRepository spatialUnitRepository;
    @Mock
    ConceptSeeder conceptSeeder;
    @Mock
    PersonSeeder personSeeder;
    @Mock
    InstitutionSeeder institutionSeeder;

    @InjectMocks
    SpatialUnitSeeder seeder;

    @Test
    void seed_ConceptDoesNotExist() {

        when(conceptSeeder.findConceptOrReturnNull("th240", "123456")).thenReturn(null);
        List<SpatialUnitSeeder.SpatialUnitSpecs> toInsert = List.of(
                new SpatialUnitSeeder.SpatialUnitSpecs("name",
                        "th240",
                        "123456",
                        "author@siamois.fr",
                        "test",
                        Set.of(
                                new SpatialUnitSeeder.SpatialUnitKey("Name")
                        )
                )
        );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> seeder.seed(toInsert)
        );

        assertThat(ex.getMessage()).contains("Concept introuvable");
    }

    @Test
    void seed_AuthorDoesNotExist() {

        Concept c = new Concept();
        when(conceptSeeder.findConceptOrReturnNull("th240", "123456")).thenReturn(c);
        when(personSeeder.findPersonOrReturnNull("author@siamois.fr")).thenReturn(null);
        List<SpatialUnitSeeder.SpatialUnitSpecs> toInsert = List.of(
                new SpatialUnitSeeder.SpatialUnitSpecs("name",
                        "th240",
                        "123456",
                        "author@siamois.fr",
                        "test",
                        Set.of(
                                new SpatialUnitSeeder.SpatialUnitKey("Name")
                        )
                )
        );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> seeder.seed(toInsert)
        );

        assertThat(ex.getMessage()).contains("Auteur introuvable");
    }

    @Test
    void seed_InstitutionDoesNotExist() {

        Concept c = new Concept();
        Person p = new Person();
        when(conceptSeeder.findConceptOrReturnNull("th240", "123456")).thenReturn(c);
        when(personSeeder.findPersonOrReturnNull("author@siamois.fr")).thenReturn(p);
        when(institutionSeeder.findInstitutionOrReturnNull("test")).thenReturn(null);
        List<SpatialUnitSeeder.SpatialUnitSpecs> toInsert = List.of(
                new SpatialUnitSeeder.SpatialUnitSpecs("name",
                        "th240",
                        "123456",
                        "author@siamois.fr",
                        "test",
                        Set.of(
                                new SpatialUnitSeeder.SpatialUnitKey("Name")
                        )
                )
        );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> seeder.seed(toInsert)
        );

        assertThat(ex.getMessage()).contains("Institution introuvable");
    }

    @Test
    void seed_ChildDoesNotExist() {

        Concept c = new Concept();
        Person p = new Person();
        Institution i = new Institution();
        i.setId(1L);
        when(conceptSeeder.findConceptOrReturnNull("th240", "123456")).thenReturn(c);
        when(personSeeder.findPersonOrReturnNull("author@siamois.fr")).thenReturn(p);
        when(institutionSeeder.findInstitutionOrReturnNull("test")).thenReturn(i);
        when(spatialUnitRepository.findByNameAndInstitution("Name", 1L)).thenReturn(Optional.empty());
        List<SpatialUnitSeeder.SpatialUnitSpecs> toInsert = List.of(
                new SpatialUnitSeeder.SpatialUnitSpecs("name",
                        "th240",
                        "123456",
                        "author@siamois.fr",
                        "test",
                        Set.of(
                                new SpatialUnitSeeder.SpatialUnitKey("Name")
                        )
                )
        );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> seeder.seed(toInsert)
        );

        assertThat(ex.getMessage()).contains("Enfant introuvable");

    }

    @Test
    void seed_AlreadyExists() {

        Concept c = new Concept();
        Person p = new Person();
        Institution i = new Institution();
        SpatialUnit child = new SpatialUnit();
        child.setName("Name");
        i.setId(1L);

        when(conceptSeeder.findConceptOrReturnNull("th240", "123456")).thenReturn(c);
        when(personSeeder.findPersonOrReturnNull("author@siamois.fr")).thenReturn(p);
        when(institutionSeeder.findInstitutionOrReturnNull("test")).thenReturn(i);
        when(spatialUnitRepository.findByNameAndInstitution("Name", 1L)).thenReturn(Optional.of(child));
        when(spatialUnitRepository.findByNameAndInstitution("name", 1L)).thenReturn(Optional.of(new SpatialUnit()));
        List<SpatialUnitSeeder.SpatialUnitSpecs> toInsert = List.of(
                new SpatialUnitSeeder.SpatialUnitSpecs("name",
                        "th240",
                        "123456",
                        "author@siamois.fr",
                        "test",
                        Set.of(
                                new SpatialUnitSeeder.SpatialUnitKey("Name")
                        )
                )
        );

        Map<String, SpatialUnit> res = seeder.seed(toInsert);

        verify(spatialUnitRepository,never()).save(any(SpatialUnit.class));
        assertNotNull(res.get("name"));

    }

    @Test
    void seed_created() {

        Concept c = new Concept();
        Person p = new Person();
        Institution i = new Institution();
        SpatialUnit child = new SpatialUnit();
        child.setName("Name");
        i.setId(1L);

        SpatialUnit created = new SpatialUnit();
        created.setName("created");

        when(conceptSeeder.findConceptOrReturnNull("th240", "123456")).thenReturn(c);
        when(personSeeder.findPersonOrReturnNull("author@siamois.fr")).thenReturn(p);
        when(institutionSeeder.findInstitutionOrReturnNull("test")).thenReturn(i);
        when(spatialUnitRepository.findByNameAndInstitution("Name", 1L)).thenReturn(Optional.of(child));
        when(spatialUnitRepository.findByNameAndInstitution("created", 1L)).thenReturn(Optional.empty());
        when(spatialUnitRepository.save(any(SpatialUnit.class))).thenReturn(created);
        List<SpatialUnitSeeder.SpatialUnitSpecs> toInsert = List.of(
                new SpatialUnitSeeder.SpatialUnitSpecs("created",
                        "th240",
                        "123456",
                        "author@siamois.fr",
                        "test",
                        Set.of(
                                new SpatialUnitSeeder.SpatialUnitKey("Name")
                        )
                )
        );

        Map<String, SpatialUnit> res = seeder.seed(toInsert);

        verify(spatialUnitRepository,times(1)).save(any(SpatialUnit.class));
        assertNotNull(res.get("created"));

    }

}