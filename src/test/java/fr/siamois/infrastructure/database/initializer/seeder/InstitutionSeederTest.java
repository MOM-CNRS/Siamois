package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.exceptions.api.NotSiamoisThesaurusException;
import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InstitutionSeederTest {

    @Mock
    InstitutionRepository institutionRepository;

    @Mock
    PersonSeeder personSeeder;

    @Mock
    ThesaurusSeeder thesaurusSeeder;

    @Mock
    FieldConfigurationService fieldConfigurationService;

    @InjectMocks
    InstitutionSeeder institutionSeeder;

    @Test
    void seed_ThesaurusNotFound()  {

        Institution i = new Institution();
        i.setIdentifier("test");

        List<InstitutionSeeder.InstitutionSpec> toInsert = List.of(
                new InstitutionSeeder.InstitutionSpec("Mon institution", "Test", "test", null, "https://thesaurus.mom.fr", "th240")
        );


        when(thesaurusSeeder.findVocabularyOrReturnNull("https://thesaurus.mom.fr", "th240")).thenReturn(null);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> institutionSeeder.seed(toInsert)
        );

        assertThat(ex.getMessage()).contains("Invalid thesaurus: th240" );
    }

    @Test
    void seed_AlreadyExists() throws DatabaseDataInitException, NotSiamoisThesaurusException, ErrorProcessingExpansionException {

        Institution i = new Institution();
        i.setIdentifier("test");

        List<InstitutionSeeder.InstitutionSpec> toInsert = List.of(
                new InstitutionSeeder.InstitutionSpec("Mon institution", "Test", "test", null, "https://thesaurus.mom.fr", "th240")
        );

        doReturn(null).when(fieldConfigurationService).setupFieldConfigurationForInstitution(any(Institution.class), any(Vocabulary.class));
        when(thesaurusSeeder.findVocabularyOrReturnNull("https://thesaurus.mom.fr", "th240")).thenReturn(new Vocabulary());
        when(institutionRepository.findInstitutionByIdentifier(anyString())).thenReturn(Optional.of(i));

        institutionSeeder.seed(toInsert);

        verify(institutionRepository, never()).save(any(Institution.class));
        verify(fieldConfigurationService, times(1)).setupFieldConfigurationForInstitution(any(Institution.class), any(Vocabulary.class));
    }

    @Test
    void seed_PersonDoesNotExist() {

        Institution i = new Institution();
        i.setIdentifier("test");

        List<InstitutionSeeder.InstitutionSpec> toInsert = List.of(
                new InstitutionSeeder.InstitutionSpec("Mon institution", "Test", "test",
                        List.of("user@siamois.fr"),"https://thesaurus.mom.fr", "th240")
        );

        when(personSeeder.findPersonOrReturnNull(anyString())).thenReturn(null);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> institutionSeeder.seed(toInsert)
        );


        assertThat(ex.getMessage()).contains("Invalid email: user@siamois.fr");

    }

    @Test
    void seed_success() throws DatabaseDataInitException, NotSiamoisThesaurusException, ErrorProcessingExpansionException {

        Institution i = new Institution();
        Person p = new Person();
        i.setIdentifier("test");

        List<InstitutionSeeder.InstitutionSpec> toInsert = List.of(
                new InstitutionSeeder.InstitutionSpec("Mon institution", "Test", "test",
                        List.of("user@siamois.fr"),"https://thesaurus.mom.fr", "th240")
        );

        doReturn(null).when(fieldConfigurationService).setupFieldConfigurationForInstitution(any(Institution.class), any(Vocabulary.class));
        when(thesaurusSeeder.findVocabularyOrReturnNull("https://thesaurus.mom.fr", "th240")).thenReturn(new Vocabulary());
        when(personSeeder.findPersonOrReturnNull(anyString())).thenReturn(p);
        when(institutionRepository.findInstitutionByIdentifier(anyString())).thenReturn(Optional.empty());
        when(institutionRepository.save(any(Institution.class))).thenReturn(new Institution());

        institutionSeeder.seed(toInsert);

        verify(institutionRepository, times(1)).save(any(Institution.class));
        verify(fieldConfigurationService, times(1)).setupFieldConfigurationForInstitution(any(Institution.class), any(Vocabulary.class));

    }
}