package fr.siamois.domain.services.authorization.writeverifier;

import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.dto.entity.AbstractEntityDTO;

/**
 * Interface for verifying write permissions on resources.
 * Implementations of this interface should provide logic to check if a user has the necessary permissions
 * to write to a specific resource of type TraceableEntity.
 */
public interface WritePermissionVerifier {

    /**
     * Used to determine if this verifier can handle the given resource.
     *
     * @return the class of the entity this verifier is responsible for
     */
    Class<? extends TraceableEntity> getEntityClass();

    /**
     * Checks if the user has write permission for the specified resource.
     *
     * @param userInfo the user information containing the user's permissions
     * @param resource the resource to check permissions against
     * @return true if the user has write permission, false otherwise
     */
    boolean hasSpecificWritePermission(UserInfo userInfo, AbstractEntityDTO resource);
}
