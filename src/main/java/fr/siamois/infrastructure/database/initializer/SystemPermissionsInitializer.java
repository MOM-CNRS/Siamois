package fr.siamois.infrastructure.database.initializer;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.domain.models.permissions.Permission;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.permissions.PersonProfileAssignment;
import fr.siamois.domain.models.permissions.Profile;
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

    private final ProfileRepository profileRepository;
    private final PermissionRepository permissionRepository;
    private final Map<String, Permission> permissions = new HashMap<>();
    private final PersonRepository personRepository;
    private final PersonProfileAssignmentRepository personProfileAssignmentRepository;

    @Value("${siamois.admin.username}")
    private String adminUsername;

    private void createPermissionIfNotExists(String code) {
        Optional<Permission> opt = permissionRepository.findByCode(code);
        Permission permission;
        if (opt.isEmpty()) {
            permission = new Permission();
            permission.setCode(code);
            permissionRepository.save(permission);
        } else {
            permission = opt.get();
        }
        permissions.put(code, permission);
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
        Optional<Profile> profile = profileRepository.findByCode("SUPERADMIN");
        Profile superAdmin;
        if (profile.isEmpty()) {
            Set<Permission> profilePermission = new HashSet<>();

            profilePermission.add(permissions.get(PermissionConstants.INSTANCE_MANAGE_MEMBERS));
            profilePermission.add(permissions.get(PermissionConstants.ORGANIZATION_CREATE));
            profilePermission.add(permissions.get(PermissionConstants.ORGANIZATION_ACCESS));

            superAdmin = Profile.builder()
                    .code("SUPERADMIN")
                    .name("Super administrateur")
                    .permissions(profilePermission)
                    .build();

            superAdmin = profileRepository.save(superAdmin);
            log.info("Super administrator profile created");
            Person admin = personRepository
                    .findByUsernameIgnoreCase(adminUsername)
                    .orElseThrow(() -> new DatabaseDataInitException("Super administrator profile not found"));

            PersonProfileAssignment personProfileAssignment = new PersonProfileAssignment();
            personProfileAssignment.setPerson(admin);
            personProfileAssignment.setProfile(superAdmin);
            personProfileAssignmentRepository.save(personProfileAssignment);
            log.info("Super administrator profile assignment created");
        }
    }
}
