package fr.siamois.infrastructure.database.initializer;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.permissions.Permission;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.permissions.PersonProfileAssignment;
import fr.siamois.domain.models.permissions.Profile;
import fr.siamois.domain.services.permissions.PersonProfileAssignmentService;
import fr.siamois.domain.services.permissions.ProfileService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.infrastructure.database.repositories.permissions.PermissionRepository;
import fr.siamois.infrastructure.database.repositories.permissions.PersonProfileAssignmentRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.mapper.InstitutionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@Order(-9)
@RequiredArgsConstructor
public class SystemPermissionsInitializer implements DatabaseInitializer{

    private final PermissionRepository permissionRepository;
    private final PersonRepository personRepository;
    private final PersonProfileAssignmentRepository personProfileAssignmentRepository;
    private final ProfileService profileService;
    private final InstitutionRepository institutionRepository;
    private final InstitutionMapper institutionMapper;
    private final PersonProfileAssignmentService personProfileAssignmentService;

    @Value("${siamois.admin.username}")
    private String adminUsername;

    private void createPermissionIfNotExists(String code) {
        Optional<Permission> opt = permissionRepository.findByCode(code);
        Permission permission;
        if (opt.isEmpty()) {
            permission = new Permission();
            permission.setCode(code);
            permissionRepository.save(permission);
        }
    }

    private void initializePermissions() {
        for (String code : PermissionConstants.allCodes()) {
            createPermissionIfNotExists(code);
        }
    }

    private void assignProfilIfMissing(Profile profile, Person person) {
        if (personProfileAssignmentRepository.findByProfileIdAndPersonId(profile.getId(), person.getId()).isEmpty()) {
            PersonProfileAssignment personProfileAssignment = new PersonProfileAssignment();
            personProfileAssignment.setPerson(person);
            personProfileAssignment.setProfile(profile);
            personProfileAssignmentRepository.save(personProfileAssignment);
            log.info("Profile {} assigned to {}", profile.getCode(), person.getUsername());
        }
    }

    private void initializeInstitutionPermissions(InstitutionDTO institution) {
        profileService.createOrGetOrganizationManagerProfile(institution);
        profileService.createOrGetOrganizationProjectManagerProfile(institution);
        profileService.createOrGetOrganizationMemberProfile(institution);
    }

    /**
     * Creates the organization profiles of every existing institution and puts every superadmin among
     * its managers. A superadmin holds no instance-wide grant over organization data: they reach an
     * organization only through its
     * {@link fr.siamois.domain.models.permissions.ProfileConstants#ORGANIZATION_MANAGER} profile, which
     * this catches up here for the organizations that existed before the superadmin did.
     */
    private void assignSuperAdminsToExistingInstitutions() {
        for (Institution institution : institutionRepository.findAll()) {
            InstitutionDTO institutionDTO = institutionMapper.convert(institution);
            if (institutionDTO == null) {
                continue;
            }
            initializeInstitutionPermissions(institutionDTO);
            personProfileAssignmentService.assignSuperAdminsAsOrganizationManagers(institutionDTO);
        }
    }

    @Override
    public void initialize() throws DatabaseDataInitException {
        initializePermissions();
        Profile superAdmin = profileService.createOrGetSuperadminProfile();
        Person admin = personRepository
                .findByUsernameIgnoreCase(adminUsername)
                .orElseThrow(() -> new DatabaseDataInitException("Super administrator profile not found"));

        assignProfilIfMissing(superAdmin, admin);

        institutionRepository.findInstitutionByIdentifier("siamois")
                .orElseThrow(() -> new DatabaseDataInitException("Default Institution not found"));

        assignSuperAdminsToExistingInstitutions();
    }
}
