package fr.siamois.infrastructure.database.initializer;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.permissions.Permission;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.permissions.PersonProfileAssignment;
import fr.siamois.domain.models.permissions.Profile;
import fr.siamois.domain.services.permissions.ProfileService;
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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
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
        for (Field field : PermissionConstants.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers) && field.getType().equals(String.class)) {
                try {
                    field.setAccessible(true);
                    String code = (String) field.get(null);
                    createPermissionIfNotExists(code);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
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

    @Override
    public void initialize() throws DatabaseDataInitException {
        initializePermissions();
        Profile superAdmin = profileService.createOrGetSuperadminProfile();
        Person admin = personRepository
                .findByUsernameIgnoreCase(adminUsername)
                .orElseThrow(() -> new DatabaseDataInitException("Super administrator profile not found"));

        assignProfilIfMissing(superAdmin, admin);

        Institution defaultInstitution = institutionRepository.findInstitutionByIdentifier("siamois")
                .orElseThrow(() -> new DatabaseDataInitException("Default Institution not found"));
        Profile organizationManager = profileService
                .createOrGetOrganizationManagerProfile(institutionMapper.convert(defaultInstitution));

        assignProfilIfMissing(organizationManager, admin);
    }
}
