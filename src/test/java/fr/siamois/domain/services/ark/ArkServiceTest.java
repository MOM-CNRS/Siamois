package fr.siamois.domain.services.ark;

import fr.siamois.domain.models.ark.Ark;
import fr.siamois.domain.models.exceptions.ark.NoArkConfigException;
import fr.siamois.domain.models.exceptions.ark.TooManyGenerationsException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.settings.InstitutionSettings;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.infrastructure.database.repositories.ArkRepository;
import fr.siamois.mapper.InstitutionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArkServiceTest {

    @Mock private ArkRepository arkRepository;
    @Mock private NoidCheckService noidCheckService;
    @Mock private InstitutionService institutionService;
    @Mock private RecordingUnitService recordingUnitService;
    @Mock private ServletUriComponentsBuilder builder;
    @Mock private InstitutionMapper mapper;

    private ArkService arkService;

    @BeforeEach
    void beforeEach() {
        arkService = new ArkService(noidCheckService, arkRepository, institutionService, mapper ,builder);
    }

    @Test
    void arkNotExistInInstitution_shouldReturnFalse_whenQualifierExist() {
        when(arkRepository.findByInstitutionAndQualifier(anyLong(), anyString())).thenReturn(Optional.of(new Ark()));

        Institution institution = new Institution();
        institution.setId(1L);

        boolean result = arkService.qualifierNotExistInInstitution(institution, "KHKFJNS-B");

        assertFalse(result);
    }

    @Test
    void generateAndSave_shouldGenerateAndSaveArk() throws NoArkConfigException, TooManyGenerationsException {
        Institution institution = new Institution();
        institution.setId(1L);

        InstitutionSettings settings = new InstitutionSettings();
        settings.setInstitution(institution);
        settings.setArkNaan("12345");
        settings.setArkSize(5);

        when(noidCheckService.calculateCheckDigit(anyString())).thenReturn("X");
        when(arkRepository.findByInstitutionAndQualifier(anyLong(), anyString())).thenReturn(Optional.empty());
        when(arkRepository.save(any(Ark.class))).thenAnswer(invocation -> {
            Ark ark = invocation.getArgument(0, Ark.class);
            ark.setInternalId(1L);
            return ark;
        });

        Ark ark = arkService.generateAndSave(settings);

        assertNotNull(ark);
        assertEquals(institution, ark.getCreatingInstitution());
        assertTrue(ark.getQualifier().matches("[a-z0-9]{5}-X"));
    }

    @Test
    void generateAndSave_shouldThrowNoArkConfigException_whenArkNaanIsNull() {
        Institution institution = new Institution();
        institution.setId(1L);

        InstitutionSettings empty = new InstitutionSettings();
        empty.setInstitution(institution);

        assertThrows(NoArkConfigException.class, () -> arkService.generateAndSave(empty));
    }

    @Test
    void generateAndSave_shouldThrowTooManyGenerationsException_whenMaxGenerationsReached() {
        Institution institution = new Institution();
        institution.setId(1L);

        InstitutionSettings settings = new InstitutionSettings();
        settings.setInstitution(institution);
        settings.setArkNaan("12345");
        settings.setArkSize(3);



        when(noidCheckService.calculateCheckDigit(anyString())).thenReturn("X");
        when(arkRepository.findByInstitutionAndQualifier(anyLong(), anyString())).thenReturn(Optional.of(new Ark()));

        assertThrows(TooManyGenerationsException.class, () -> arkService.generateAndSave(settings));
    }

    @Test
    void getUriOf() {
        // Arrange
        InstitutionDTO institutionDTO = new InstitutionDTO();
        institutionDTO.setId(1L);

        Institution institution = new Institution();
        institution.setId(1L);

        InstitutionSettings settings = new InstitutionSettings();
        settings.setInstitution(institution);
        settings.setArkNaan("12345");
        settings.setArkPrefix("ark:/");
        settings.setArkIsUppercase(false);
        settings.setArkIsEnabled(true);

        Ark ark = new Ark();
        ark.setCreatingInstitution(institution);
        ark.setQualifier("abcde-x");

        when(mapper.convert(any(Institution.class))).thenReturn(institutionDTO);
        when(institutionService.createOrGetSettingsOf(institutionDTO)).thenReturn(settings);
        when(builder.cloneBuilder()).thenReturn(builder);
        when(builder.path(anyString())).thenReturn(builder);
        when(builder.toUriString()).thenReturn("http://localhost/api/ark:/12345/abcde-x");

        // Act
        String uri = arkService.getUriOf(ark);

        // Assert
        assertNotNull(uri);
        assertEquals("http://localhost/api/ark:/12345/abcde-x", uri);
        verify(builder, times(4)).path(anyString());
        verify(builder, times(1)).toUriString();
    }
}