package fr.siamois.domain.services.authorization;

import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.authorization.writeverifier.WritePermissionVerifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class PermissionServiceImpl implements PermissionService {

    private final InstitutionService institutionService;

    private final Map<Class<? extends TraceableEntity>, WritePermissionVerifier> verifiers;

    public PermissionServiceImpl(InstitutionService institutionService,
                                 List<WritePermissionVerifier> verifiers) {
        this.institutionService = institutionService;

        this.verifiers = new HashMap<>();

        for (WritePermissionVerifier verifier : verifiers) {
            this.verifiers.put(verifier.getEntityClass(), verifier);
        }
    }

    @Override
    public boolean hasReadPermission(UserInfo user, TraceableEntity resource) {
        if (!resource.getCreatedByInstitution().equals(user.getInstitution()))
            return false;

        if (isActionManager(user) || isInstitutionManager(user)) {
            return true;
        }

        return institutionService.personIsInInstitution(user.getUser(), resource.getCreatedByInstitution());
    }

    @Override
    public boolean hasWritePermission(UserInfo user, TraceableEntity resource) {
        if (!resource.getCreatedByInstitution().equals(user.getInstitution()))
            return false;

        if (isActionManager(user) || isInstitutionManager(user)) {
            return true;
        }

        if (verifiers.containsKey(resource.getClass())) {
            return verifiers.get(resource.getClass()).hasSpecificWritePermission(user, resource);
        }

        log.error("No write permission verifier found for resource class: {}", resource.getClass().getName());
        return false;
    }

    @Override
    public boolean isInstitutionManager(UserInfo user) {
        return institutionService.personIsInstitutionManager(user.getUser(), user.getInstitution());
    }

    @Override
    public boolean isActionManager(UserInfo user) {
        return institutionService.personIsActionManager(user.getUser(), user.getInstitution());
    }
}
