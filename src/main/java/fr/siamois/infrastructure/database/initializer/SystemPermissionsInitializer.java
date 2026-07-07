package fr.siamois.infrastructure.database.initializer;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.domain.models.permissions.*;
import fr.siamois.domain.services.permissions.ProfileService;
import fr.siamois.infrastructure.database.repositories.permissions.PermissionRepository;
import fr.siamois.infrastructure.database.repositories.permissions.PersonProfileAssignmentRepository;
import fr.siamois.infrastructure.database.repositories.permissions.ProfileRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemPermissionsInitializer implements DatabaseInitializer{

    private final PermissionRepository permissionRepository;
    private final PersonRepository personRepository;
    private final PersonProfileAssignmentRepository personProfileAssignmentRepository;
    private final ProfileService profileService;

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

    @Override
    public void initialize() throws DatabaseDataInitException {
        initializePermissions();
        Profile superAdmin = profileService.createOrGetSuperadminProfile();
        Person admin = personRepository
                .findByUsernameIgnoreCase(adminUsername)
                .orElseThrow(() -> new DatabaseDataInitException("Super administrator profile not found"));

        if (!personProfileAssignmentRepository.personIsSuperAdmin(admin)) {
            PersonProfileAssignment personProfileAssignment = new PersonProfileAssignment();
            personProfileAssignment.setPerson(admin);
            personProfileAssignment.setProfile(superAdmin);
            personProfileAssignmentRepository.save(personProfileAssignment);
            log.info("Super administrator profile assignment created");
        }
    }
}
