package fr.siamois.domain.services.authorization;

import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.dto.entity.AbstractEntityDTO;
import org.springframework.stereotype.Service;


/**
 * Service interface for managing permissions related to resources.
 */
@Service
public interface PermissionService {

    /**
     * Checks if the user has read permission for the specified resource.
     *
     * @param user     the user information
     * @param resource the resource to check permissions against
     * @return true if the user has read permission, false otherwise
     */
    boolean hasReadPermission(UserInfo user, AbstractEntityDTO resource);

    /**
     * Checks if the user has write permission for the specified resource.
     *
     * @param user     the user information
     * @param resource the resource to check permissions against
     * @return true if the user has write permission, false otherwise
     */
    boolean hasWritePermission(UserInfo user, AbstractEntityDTO resource);

    /**
     * Checks if the user is an institution manager.
     *
     * @param user the user information
     * @return true if the user is an institution manager, false otherwise
     */
    boolean isInstitutionManager(UserInfo user);

    /**
     * Checks if the user is an action manager.
     *
     * @param user the user information
     * @return true if the user is an action manager, false otherwise
     */
    boolean isActionManager(UserInfo user);
}
