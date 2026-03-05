package fr.siamois.utils.context;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.mapper.InstitutionMapper;
import fr.siamois.mapper.PersonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemUserLoaderTest {

    @Mock
    private PersonRepository personRepository;

    @Mock
    private InstitutionRepository institutionRepository;

    @Mock
    private PersonMapper personMapper;

    @Mock
    private InstitutionMapper institutionMapper;

    @InjectMocks
    private SystemUserLoader systemUserLoader;

    private Person mockSystemPerson;
    private PersonDTO mockSystemPersonDTO;
    private Institution mockDefaultInstitution;
    private InstitutionDTO mockDefaultInstitutionDTO;

    @BeforeEach
    void setUp() {
        mockSystemPerson = new Person();
        mockSystemPerson.setUsername("system");
        mockSystemPerson.setEnabled(true);
        mockSystemPerson.setName("SIAMOIS");
        mockSystemPerson.setLastname("SYSTEM");
        mockSystemPerson.setEmail("system@siamois.fr");
        mockSystemPerson.setPassword("SIAMOIS_UNHASHED");
        mockSystemPerson.setSuperAdmin(false);

        mockSystemPersonDTO = new PersonDTO();
        mockSystemPersonDTO.setUsername("system");

        mockDefaultInstitution = new Institution();
        mockDefaultInstitution.setName("Organisation par défaut");
        mockDefaultInstitution.setDescription("DEFAULT");
        mockDefaultInstitution.setIdentifier("siamois");

        mockDefaultInstitutionDTO = new InstitutionDTO();
        mockDefaultInstitutionDTO.setIdentifier("siamois");

        when(personMapper.convert(any(Person.class))).thenReturn(mockSystemPersonDTO);
        when(institutionMapper.convert(any(Institution.class))).thenReturn(mockDefaultInstitutionDTO);
    }

    @Test
    void loadSystemUser_WhenPersonAndInstitutionExist_ReturnsUserInfo() {
        // Arrange
        when(personRepository.findByUsernameIgnoreCase("system"))
                .thenReturn(Optional.of(mockSystemPerson));
        when(institutionRepository.findInstitutionByIdentifier("siamois"))
                .thenReturn(Optional.of(mockDefaultInstitution));

        // Act
        UserInfo result = systemUserLoader.loadSystemUser();

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getUser().getUsername()).isEqualTo("system");
        assertThat(result.getInstitution().getIdentifier()).isEqualTo("siamois");
        assertThat(result.getLang()).isEqualTo("en");

        verify(personRepository, never()).save(any(Person.class));
        verify(institutionRepository, never()).save(any(Institution.class));
    }

    @Test
    void loadSystemUser_WhenPersonDoesNotExist_CreatesAndReturnsUserInfo() {
        // Arrange
        when(personRepository.findByUsernameIgnoreCase("system"))
                .thenReturn(Optional.empty());
        when(personRepository.save(any(Person.class)))
                .thenReturn(mockSystemPerson);
        when(institutionRepository.findInstitutionByIdentifier("siamois"))
                .thenReturn(Optional.of(mockDefaultInstitution));

        // Act
        UserInfo result = systemUserLoader.loadSystemUser();

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getUser().getUsername()).isEqualTo("system");
        assertThat(result.getInstitution().getIdentifier()).isEqualTo("siamois");
        assertThat(result.getLang()).isEqualTo("en");

        verify(personRepository, times(1)).save(any(Person.class));
        verify(institutionRepository, never()).save(any(Institution.class));
    }

    @Test
    void loadSystemUser_WhenInstitutionDoesNotExist_CreatesAndReturnsUserInfo() {
        // Arrange
        when(personRepository.findByUsernameIgnoreCase("system"))
                .thenReturn(Optional.of(mockSystemPerson));
        when(institutionRepository.findInstitutionByIdentifier("siamois"))
                .thenReturn(Optional.empty());
        when(institutionRepository.save(any(Institution.class)))
                .thenReturn(mockDefaultInstitution);

        // Act
        UserInfo result = systemUserLoader.loadSystemUser();

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getUser().getUsername()).isEqualTo("system");
        assertThat(result.getInstitution().getIdentifier()).isEqualTo("siamois");
        assertThat(result.getLang()).isEqualTo("en");

        verify(institutionRepository, times(1)).save(any(Institution.class));
        verify(personRepository, never()).save(any(Person.class));
    }

    @Test
    void loadSystemUser_WhenNeitherPersonNorInstitutionExist_CreatesAndReturnsUserInfo() {
        // Arrange
        when(personRepository.findByUsernameIgnoreCase("system"))
                .thenReturn(Optional.empty());
        when(personRepository.save(any(Person.class)))
                .thenReturn(mockSystemPerson);
        when(institutionRepository.findInstitutionByIdentifier("siamois"))
                .thenReturn(Optional.empty());
        when(institutionRepository.save(any(Institution.class)))
                .thenReturn(mockDefaultInstitution);

        // Act
        UserInfo result = systemUserLoader.loadSystemUser();

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getUser().getUsername()).isEqualTo("system");
        assertThat(result.getInstitution().getIdentifier()).isEqualTo("siamois");
        assertThat(result.getLang()).isEqualTo("en");

        verify(personRepository, times(1)).save(any(Person.class));
        verify(institutionRepository, times(1)).save(any(Institution.class));
    }
}
