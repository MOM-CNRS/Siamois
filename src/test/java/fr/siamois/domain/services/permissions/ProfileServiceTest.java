package fr.siamois.domain.services.permissions;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.permissions.Permission;
import fr.siamois.domain.models.permissions.Profile;
import fr.siamois.domain.models.permissions.ProfileConstants;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.ProfileDTO;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.infrastructure.database.repositories.permissions.PermissionRepository;
import fr.siamois.infrastructure.database.repositories.permissions.ProfileRepository;
import fr.siamois.mapper.ProfileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private PermissionRepository permissionRepository;
    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private InstitutionRepository institutionRepository;
    @Mock
    private ActionUnitRepository actionUnitRepository;
    @Mock
    private ProfileMapper profileMapper;

    @InjectMocks
    private ProfileService profileService;

    private Institution institution;
    private InstitutionDTO institutionDTO;
    private ActionUnit actionUnit;
    private ActionUnitDTO actionUnitDTO;
    private Permission mockPermission;
    private Profile existingProfile;

    @BeforeEach
    void setUp() {
        institution = new Institution();
        institution.setId(1L);

        institutionDTO = new InstitutionDTO();
        institutionDTO.setId(1L);

        actionUnit = new ActionUnit();
        actionUnit.setId(10L);

        actionUnitDTO = new ActionUnitDTO();
        actionUnitDTO.setId(10L);
        actionUnitDTO.setCreatedByInstitution(institutionDTO);

        mockPermission = new Permission();
        mockPermission.setId(100L);
        mockPermission.setCode("MOCK_PERMISSION");

        existingProfile = new Profile();
        existingProfile.setId(999L);
        existingProfile.setCode("EXISTING_CODE");
    }

    @Test
    void createOrGetSuperadminProfile_WhenAlreadyExists_ReturnsExisting() {
        when(profileRepository.findByCode(ProfileConstants.SUPERADMIN)).thenReturn(Optional.of(existingProfile));

        Profile result = profileService.createOrGetSuperadminProfile();

        assertEquals(existingProfile, result);
        verify(permissionRepository, never()).findByCode(anyString());
        verify(profileRepository, never()).save(any());
    }

    @Test
    void createOrGetSuperadminProfile_WhenDoesNotExist_CreatesAndReturnsNew() {
        when(profileRepository.findByCode(ProfileConstants.SUPERADMIN)).thenReturn(Optional.empty());
        when(permissionRepository.findByCode(anyString())).thenReturn(Optional.of(mockPermission));
        when(profileRepository.save(any(Profile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Profile result = profileService.createOrGetSuperadminProfile();

        assertNotNull(result);
        assertEquals(ProfileConstants.SUPERADMIN, result.getCode());
        assertEquals("Super administrateur", result.getName());

        verify(profileRepository, times(1)).save(any(Profile.class));
    }

    @Test
    void createOrGetOrganizationManagerProfile_CreatesCorrectly() {
        when(profileRepository.findByCodeAndInstitutionId(ProfileConstants.ORGANIZATION_MANAGER, institutionDTO.getId()))
                .thenReturn(Optional.empty());
        when(permissionRepository.findByCode(anyString())).thenReturn(Optional.of(mockPermission));
        when(institutionRepository.findById(institutionDTO.getId())).thenReturn(Optional.of(institution));
        when(profileRepository.save(any(Profile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Profile result = profileService.createOrGetOrganizationManagerProfile(institutionDTO);

        assertNotNull(result);
        assertEquals(ProfileConstants.ORGANIZATION_MANAGER, result.getCode());
        assertEquals(institution, result.getInstitution());
    }

    @Test
    void createOrGetOrganizationProjectManagerProfile_ThrowsExceptionIfInstitutionNotFound() {
        when(profileRepository.findByCodeAndInstitutionId(ProfileConstants.ORGANIZATION_PROJECT_MANAGER, institutionDTO.getId()))
                .thenReturn(Optional.empty());
        when(permissionRepository.findByCode(anyString())).thenReturn(Optional.of(mockPermission));
        when(institutionRepository.findById(institutionDTO.getId())).thenReturn(Optional.empty());

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                profileService.createOrGetOrganizationProjectManagerProfile(institutionDTO)
        );

        assertTrue(exception.getMessage().contains("Institution with code"));
        verify(profileRepository, never()).save(any());
    }

    @Test
    void createOrGetOrganizationMemberProfile_WhenAlreadyExists_ReturnsExisting() {
        when(profileRepository.findByCodeAndInstitutionId(ProfileConstants.ORGANIZATION_MEMBER, institutionDTO.getId()))
                .thenReturn(Optional.of(existingProfile));

        Profile result = profileService.createOrGetOrganizationMemberProfile(institutionDTO);

        assertEquals(existingProfile, result);
        verify(institutionRepository, never()).findById(anyLong());
    }

    @Test
    void createOrGetProjectManagerProfile_CreatesCorrectly() {
        when(profileRepository.findByCodeAndInstitutionIdAndActionUnitId(
                ProfileConstants.PROJECT_MANAGER, institutionDTO.getId(), actionUnitDTO.getId()))
                .thenReturn(Optional.empty());
        when(permissionRepository.findByCode(anyString())).thenReturn(Optional.of(mockPermission));
        when(institutionRepository.findById(institutionDTO.getId())).thenReturn(Optional.of(institution));
        when(actionUnitRepository.findById(actionUnitDTO.getId())).thenReturn(Optional.of(actionUnit));

        ArgumentCaptor<Profile> profileCaptor = ArgumentCaptor.forClass(Profile.class);
        when(profileRepository.save(profileCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        Profile result = profileService.createOrGetProjectManagerProfile(actionUnitDTO);

        assertNotNull(result);
        Profile savedProfile = profileCaptor.getValue();
        assertEquals(ProfileConstants.PROJECT_MANAGER, savedProfile.getCode());
        assertEquals(institution, savedProfile.getInstitution());
        assertEquals(actionUnit, savedProfile.getActionUnit());
    }

    @Test
    void createOrGetProjectMemberProfile_WhenAlreadyExists_ReturnsExisting() {
        when(profileRepository.findByCodeAndInstitutionIdAndActionUnitId(
                ProfileConstants.PROJECT_MEMBER, institutionDTO.getId(), actionUnitDTO.getId()))
                .thenReturn(Optional.of(existingProfile));

        Profile result = profileService.createOrGetProjectMemberProfile(actionUnitDTO);

        assertEquals(existingProfile, result);
        verify(actionUnitRepository, never()).findById(anyLong());
        verify(profileRepository, never()).save(any());
    }

    @Test
    void findAllProfilesByActionUnit() {
        Profile profile1 = new Profile();
        Profile profile2 = new Profile();
        ProfileDTO dto1 = new ProfileDTO();
        ProfileDTO dto2 = new ProfileDTO();

        when(profileRepository.findAllOfActionUnitScope(actionUnitDTO.getId())).thenReturn(List.of(profile1, profile2));
        when(profileMapper.convert(profile1)).thenReturn(dto1);
        when(profileMapper.convert(profile2)).thenReturn(dto2);

        List<ProfileDTO> result = profileService.findAllProfilesByActionUnit(actionUnitDTO);

        assertEquals(2, result.size());
        assertTrue(result.contains(dto1));
        assertTrue(result.contains(dto2));
    }

    @Test
    void findAllProfilesOfInstance() {
        Profile profile = new Profile();
        ProfileDTO dto = new ProfileDTO();

        when(profileRepository.findAllOfInstanceScope()).thenReturn(List.of(profile));
        when(profileMapper.convert(profile)).thenReturn(dto);

        List<ProfileDTO> result = profileService.findAllProfilesOfInstance();

        assertEquals(1, result.size());
        assertEquals(dto, result.get(0));
    }
}