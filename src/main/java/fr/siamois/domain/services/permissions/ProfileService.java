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

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final PermissionRepository permissionRepository;
    private final ProfileRepository profileRepository;
    private final InstitutionRepository institutionRepository;
    private final ActionUnitRepository actionUnitRepository;
    private final ProfileMapper profileMapper;



    private Permission findOrThrowPermission(String permissionCode) {
        return permissionRepository.findByCode(permissionCode).orElseThrow(() -> new IllegalStateException(String.format("Permission with code %s not found", permissionCode)));
    }


    @NonNull
    public Profile createOrGetSuperadminProfile() {
        Optional<Profile> profile = profileRepository.findByCode(ProfileConstants.SUPERADMIN);
        if (profile.isPresent()) return profile.get();

        Set<Permission> profilePermission = new HashSet<>();
        profilePermission.add(findOrThrowPermission(PermissionConstants.INSTANCE_MANAGE_MEMBERS));
        profilePermission.add(findOrThrowPermission(PermissionConstants.ORGANIZATION_CREATE));
        profilePermission.add(findOrThrowPermission(PermissionConstants.ORGANIZATION_ACCESS));

        Profile superAdmin = Profile.builder()
                .code(ProfileConstants.SUPERADMIN)
                .name("Super administrateur")
                .permissions(profilePermission)
                .scope(PermissionScopeType.INSTANCE)
                .build();

        return profileRepository.save(superAdmin);
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
                PermissionConstants.ORGANIZATION_MANAGE_MEMBERS,
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
                PermissionConstants.PROJECT_MANAGE_MEMBERS,
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
