package fr.siamois.domain.services.permissions;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.permissions.*;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.ProfileDTO;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.infrastructure.database.repositories.permissions.PermissionRepository;
import fr.siamois.infrastructure.database.repositories.permissions.ProfileRepository;
import fr.siamois.mapper.ProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private static final List<String> SUPERADMIN_PERMISSIONS = List.of(
            PermissionConstants.INSTANCE_MANAGE_SETTINGS,
            PermissionConstants.ORGANIZATION_CREATE,
            PermissionConstants.ORGANIZATION_ACCESS,
            PermissionConstants.ORGANIZATION_MANAGE_ACTIONS,
            PermissionConstants.PROJECT_MANAGE_SETTINGS
    );

    private final PermissionRepository permissionRepository;
    private final ProfileRepository profileRepository;
    private final InstitutionRepository institutionRepository;
    private final ActionUnitRepository actionUnitRepository;
    private final ProfileMapper profileMapper;



    private Permission findOrThrowPermission(String permissionCode) {
        return permissionRepository.findByCode(permissionCode).orElseThrow(() -> new IllegalStateException(String.format("Permission with code %s not found", permissionCode)));
    }


    @NonNull
    @Transactional
    public Profile createOrGetSuperadminProfile() {
        Optional<Profile> profile = profileRepository.findByCode(ProfileConstants.SUPERADMIN);
        if (profile.isPresent()) return grantMissingPermissions(profile.get(), SUPERADMIN_PERMISSIONS);

        Set<Permission> profilePermission = new HashSet<>();
        for (String permissionCode : SUPERADMIN_PERMISSIONS) {
            profilePermission.add(findOrThrowPermission(permissionCode));
        }

        Profile superAdmin = Profile.builder()
                .code(ProfileConstants.SUPERADMIN)
                .name("Super administrateur")
                .permissions(profilePermission)
                .scope(PermissionScopeType.INSTANCE)
                .build();

        return profileRepository.save(superAdmin);
    }

    /**
     * Grants a persisted system profile the permissions it does not hold yet, so that a profile created
     * by an earlier version of the application picks up the permissions this version grants it. Existing
     * permissions are never removed.
     *
     * @param profile         the persisted profile to reconcile
     * @param permissionCodes the {@link PermissionConstants} codes the profile must hold
     * @return the profile, saved again if it was missing permissions
     */
    @NonNull
    private Profile grantMissingPermissions(@NonNull Profile profile, List<String> permissionCodes) {
        Set<String> heldCodes = profile.getPermissions().stream()
                .map(Permission::getCode)
                .collect(Collectors.toSet());
        List<Permission> missing = permissionCodes.stream()
                .filter(code -> !heldCodes.contains(code))
                .map(this::findOrThrowPermission)
                .toList();
        if (missing.isEmpty()) {
            return profile;
        }
        profile.getPermissions().addAll(missing);
        return profileRepository.save(profile);
    }

    @NonNull
    private Profile createOrGetOrganizationProfile(String name, String code, List<String> permissions, @NonNull InstitutionDTO institutionDTO) {
        Optional<Profile> profile = profileRepository.findByCodeAndInstitutionId(code, institutionDTO.getId());
        if (profile.isPresent()) return profile.get();
        Set<Permission> profilePermission = new HashSet<>();
        for (String permissionCode : permissions) {
            profilePermission.add(findOrThrowPermission(permissionCode));
        }

        Institution institution = institutionRepository.findById(institutionDTO.getId()).orElseThrow(() -> new IllegalStateException(String.format("Institution with code %s not found", institutionDTO.getId())));

        Profile organizationProfile = Profile.builder()
                .code(code)
                .name(name)
                .institution(institution)
                .permissions(profilePermission)
                .scope(PermissionScopeType.ORGANISATION)
                .build();

        return profileRepository.save(organizationProfile);
    }

    @NonNull
    public Profile createOrGetOrganizationManagerProfile(@NonNull InstitutionDTO institutionDTO) {
        return createOrGetOrganizationProfile("Gestionnaire de l'organisation", ProfileConstants.ORGANIZATION_MANAGER, List.of(
                PermissionConstants.ORGANIZATION_MANAGE_SETTINGS,
                PermissionConstants.ORGANIZATION_MANAGE_ACTIONS,
                PermissionConstants.ORGANIZATION_MANAGE_PLACES,
                PermissionConstants.ORGANIZATION_ACCESS
        ), institutionDTO);
    }

    @NonNull
    public Profile createOrGetOrganizationProjectManagerProfile(@NonNull InstitutionDTO institutionDTO) {
        return createOrGetOrganizationProfile("Gestionnaire des projets", ProfileConstants.ORGANIZATION_PROJECT_MANAGER, List.of(
                PermissionConstants.ORGANIZATION_MANAGE_ACTIONS,
                PermissionConstants.ORGANIZATION_MANAGE_PLACES,
                PermissionConstants.ORGANIZATION_ACCESS
        ), institutionDTO);
    }

    @NonNull
    public Profile createOrGetOrganizationMemberProfile(@NonNull InstitutionDTO institutionDTO) {
        return createOrGetOrganizationProfile("Membre d'organisation", ProfileConstants.ORGANIZATION_MEMBER, List.of(
                PermissionConstants.ORGANIZATION_ACCESS
        ), institutionDTO);
    }

    private Profile createOrGetProjectProfile(String name, String code, List<String> permissions, @NonNull InstitutionDTO institutionDTO, @NonNull ActionUnitDTO actionUnitDTO) {
        Optional<Profile> profile = profileRepository.findByCodeAndInstitutionIdAndActionUnitId(code, institutionDTO.getId(), actionUnitDTO.getId());
        if (profile.isPresent()) return profile.get();
        Set<Permission> profilePermission = new HashSet<>();
        for (String permissionCode : permissions) {
            profilePermission.add(findOrThrowPermission(permissionCode));
        }

        Institution institution = institutionRepository.findById(institutionDTO.getId()).orElseThrow(() -> new IllegalStateException(String.format("Institution with code %s not found", institutionDTO.getId())));
        ActionUnit actionUnit = actionUnitRepository.findById(actionUnitDTO.getId()).orElseThrow(() -> new IllegalStateException(String.format("Action unit with code %s not found", actionUnitDTO.getId())));

        Profile projectProfile = Profile.builder()
                .name(name)
                .code(code)
                .institution(institution)
                .actionUnit(actionUnit)
                .permissions(profilePermission)
                .scope(PermissionScopeType.PROJECT)
                .build();

        return profileRepository.save(projectProfile);
    }

    @NonNull
    public Profile createOrGetProjectManagerProfile(@NonNull ActionUnitDTO actionUnitDTO) {
        return createOrGetProjectProfile("Gestionnaire du projet", ProfileConstants.PROJECT_MANAGER, List.of(
                PermissionConstants.PROJECT_MANAGE_SETTINGS,
                PermissionConstants.PROJECT_EDIT_RECORDING_UNITS,
                PermissionConstants.PROJECT_EDIT_FINDS,
                PermissionConstants.PROJECT_EDIT_PHASES,
                PermissionConstants.PROJECT_EDIT_CONTAINERS
        ), actionUnitDTO.getCreatedByInstitution(), actionUnitDTO);
    }

    @NonNull
    public Profile createOrGetProjectMemberProfile(@NonNull ActionUnitDTO actionUnitDTO) {
        return createOrGetProjectProfile("Membre du projet", ProfileConstants.PROJECT_MEMBER, List.of(
                PermissionConstants.PROJECT_EDIT_RECORDING_UNITS,
                PermissionConstants.PROJECT_EDIT_FINDS,
                PermissionConstants.PROJECT_EDIT_PHASES,
                PermissionConstants.PROJECT_EDIT_CONTAINERS
        ), actionUnitDTO.getCreatedByInstitution(), actionUnitDTO);
    }

    public List<ProfileDTO> findAllProfilesByActionUnit(ActionUnitDTO project) {
        return profileRepository.findAllOfActionUnitScope(project.getId())
                .stream()
                .map(profileMapper::convert)
                .toList();
    }

    public List<ProfileDTO> findAllProfilesOfInstance() {
        return profileRepository.findAllOfInstanceScope()
                .stream()
                .map(profileMapper::convert)
                .toList();
    }
}
