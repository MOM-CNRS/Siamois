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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProfileService {

    /**
     * The genuinely INSTANCE-scope permission codes — every one of them is the instance-wide counterpart
     * of an organisation- or project-scope permission (see {@link PermissionConstants}'s "Instance-wide
     * counterparts" section), or an instance-only action like {@link PermissionConstants#ORGANIZATION_CREATE}.
     * The superadmin profile holds exactly these — never an organisation- or project-scope code directly —
     * and {@link ProfilePermissionService} explicitly checks each capability's own-scope code at every
     * level, so this alone is enough for the superadmin to act everywhere.
     */
    private static final List<String> SUPERADMIN_PERMISSIONS = List.of(
            PermissionConstants.INSTANCE_MANAGE_SETTINGS,
            PermissionConstants.ORGANIZATION_CREATE,
            PermissionConstants.INSTANCE_MANAGE_ORGANIZATIONS_SETTINGS,
            PermissionConstants.INSTANCE_MANAGE_ORGANIZATIONS_ACTIONS,
            PermissionConstants.INSTANCE_MANAGE_ORGANIZATIONS_PLACES,
            PermissionConstants.INSTANCE_ACCESS_ORGANIZATIONS,
            PermissionConstants.INSTANCE_EDIT_RECORDING_UNITS,
            PermissionConstants.INSTANCE_EDIT_PHASES,
            PermissionConstants.INSTANCE_EDIT_FINDS,
            PermissionConstants.INSTANCE_EDIT_CONTAINERS
    );

    private final PermissionRepository permissionRepository;
    private final ProfileRepository profileRepository;
    private final InstitutionRepository institutionRepository;
    private final ActionUnitRepository actionUnitRepository;
    private final ProfileMapper profileMapper;



    private Permission findOrThrowPermission(String permissionCode) {
        return permissionRepository.findByCode(permissionCode).orElseThrow(() -> new IllegalStateException(String.format("Permission with code %s not found", permissionCode)));
    }

    /**
     * Makes {@code profile}'s permission set exactly match {@code canonical} — adding anything missing
     * and, importantly, removing anything that no longer belongs (e.g. a code granted by an earlier
     * version of a system profile's definition). Without the removal half, a system profile only ever
     * grows over successive app versions, so its held permissions silently drift out of sync with its
     * scope's own catalog (see {@code AbstractMembersListBean#permissionCatalogOf}, which only displays a
     * profile's own-scope codes) — the permission count keeps counting stale codes the UI can no longer show.
     *
     * @return {@code true} if the set actually changed (caller should persist)
     */
    private boolean reconcilePermissions(Profile profile, Set<Permission> canonical) {
        Set<Permission> current = profile.getPermissions();
        boolean removed = current.retainAll(canonical);
        boolean added = current.addAll(canonical);
        return removed || added;
    }

    /**
     * @return the superadmin profile, creating it if missing and reconciling its permissions to exactly
     *         {@link #SUPERADMIN_PERMISSIONS} if it already exists (adding anything missing, removing
     *         anything stale from an earlier version of this list)
     */
    @NonNull
    public Profile createOrGetSuperadminProfile() {
        Set<Permission> allPermissions = SUPERADMIN_PERMISSIONS.stream()
                .map(this::findOrThrowPermission)
                .collect(Collectors.toSet());

        Optional<Profile> existing = profileRepository.findByCode(ProfileConstants.SUPERADMIN);
        if (existing.isPresent()) {
            Profile profile = existing.get();
            if (reconcilePermissions(profile, allPermissions)) {
                return profileRepository.save(profile);
            }
            return profile;
        }

        Profile superAdmin = Profile.builder()
                .code(ProfileConstants.SUPERADMIN)
                .name("Super administrateur")
                .permissions(allPermissions)
                .scope(PermissionScopeType.INSTANCE)
                .build();

        return profileRepository.save(superAdmin);
    }

    @NonNull
    private Profile createOrGetOrganizationProfile(String name, String code, List<String> permissions, @NonNull InstitutionDTO institutionDTO) {
        Set<Permission> canonicalPermissions = permissions.stream()
                .map(this::findOrThrowPermission)
                .collect(Collectors.toSet());

        Optional<Profile> existing = profileRepository.findByCodeAndInstitutionId(code, institutionDTO.getId());
        if (existing.isPresent()) {
            Profile profile = existing.get();
            if (reconcilePermissions(profile, canonicalPermissions)) {
                return profileRepository.save(profile);
            }
            return profile;
        }

        Institution institution = institutionRepository.findById(institutionDTO.getId()).orElseThrow(() -> new IllegalStateException(String.format("Institution with code %s not found", institutionDTO.getId())));

        Profile organizationProfile = Profile.builder()
                .code(code)
                .name(name)
                .institution(institution)
                .permissions(canonicalPermissions)
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
                PermissionConstants.ORGANIZATION_ACCESS,
                PermissionConstants.ORGANIZATION_EDIT_RECORDING_UNITS,
                PermissionConstants.ORGANIZATION_EDIT_PHASES,
                PermissionConstants.ORGANIZATION_EDIT_FINDS,
                PermissionConstants.ORGANIZATION_EDIT_CONTAINERS
        ), institutionDTO);
    }

    @NonNull
    public Profile createOrGetOrganizationProjectManagerProfile(@NonNull InstitutionDTO institutionDTO) {
        return createOrGetOrganizationProfile("Gestionnaire des projets", ProfileConstants.ORGANIZATION_PROJECT_MANAGER, List.of(
                PermissionConstants.ORGANIZATION_CREATE_ACTIONS,
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
        Set<Permission> canonicalPermissions = permissions.stream()
                .map(this::findOrThrowPermission)
                .collect(Collectors.toSet());

        Optional<Profile> existing = profileRepository.findByCodeAndInstitutionIdAndActionUnitId(code, institutionDTO.getId(), actionUnitDTO.getId());
        if (existing.isPresent()) {
            Profile profile = existing.get();
            if (reconcilePermissions(profile, canonicalPermissions)) {
                return profileRepository.save(profile);
            }
            return profile;
        }

        Institution institution = institutionRepository.findById(institutionDTO.getId()).orElseThrow(() -> new IllegalStateException(String.format("Institution with code %s not found", institutionDTO.getId())));
        ActionUnit actionUnit = actionUnitRepository.findById(actionUnitDTO.getId()).orElseThrow(() -> new IllegalStateException(String.format("Action unit with code %s not found", actionUnitDTO.getId())));

        Profile projectProfile = Profile.builder()
                .name(name)
                .code(code)
                .institution(institution)
                .actionUnit(actionUnit)
                .permissions(canonicalPermissions)
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
