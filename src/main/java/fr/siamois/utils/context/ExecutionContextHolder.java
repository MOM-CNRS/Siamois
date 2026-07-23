package fr.siamois.utils.context;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.utils.AuthenticatedUserUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for holding the execution context in a ThreadLocal.
 * This class is not intended to be instantiated.
 * */
@Slf4j
public final class ExecutionContextHolder {

    // Empêche l'instanciation de la classe
    private ExecutionContextHolder() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated.");
    }

    private static final ThreadLocal<UserInfo> CONTEXT = new ThreadLocal<>();

    public static void set(UserInfo info) {
        if (info == null) {
            CONTEXT.remove();
            return;
        }
        UserInfo previous = CONTEXT.get();
        if (previous != null && !sameUser(previous, info)) {
            log.warn("Execution context of user id={} was still bound to thread '{}' when binding user id={}. " +
                            "A previous task did not clear it.",
                    idOf(previous.getUser()), Thread.currentThread().getName(), idOf(info.getUser()));
        }
        CONTEXT.set(info);
    }

    public static UserInfo get() {
        UserInfo current = CONTEXT.get();
        if (current == null) {
            return null;
        }
        if (isStale(current)) {
            log.warn("Discarding execution context of user id={} on thread '{}' : it does not match the " +
                            "authenticated user of the current request. A previous task failed to clear it.",
                    idOf(current.getUser()), Thread.currentThread().getName());
            CONTEXT.remove();
            return null;
        }
        return current;
    }

    /**
     * Clears the execution context for the current thread.
     */
    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * A context is stale when the request is authenticated as somebody other than the person it
     * describes. Both identifiers must be known before a mismatch is called, so that an unsaved or
     * partially built principal never invalidates a legitimate context.
     */
    private static boolean isStale(UserInfo current) {
        Long authenticatedId = authenticatedPersonId();
        if (authenticatedId == null) {
            return false;
        }
        Long contextId = idOf(current.getUser());
        return contextId != null && !authenticatedId.equals(contextId);
    }

    private static Long authenticatedPersonId() {
        try {
            return AuthenticatedUserUtils.getAuthenticatedUser().map(Person::getId).orElse(null);
        } catch (RuntimeException e) {
            // A principal that is not a Person leaves nothing to compare against; reading the context
            // must degrade to "cannot tell", never blow up.
            log.debug("Could not read the authenticated user to validate the execution context", e);
            return null;
        }
    }

    private static boolean sameUser(UserInfo left, UserInfo right) {
        Long leftId = idOf(left.getUser());
        return leftId != null && leftId.equals(idOf(right.getUser()));
    }

    private static Long idOf(PersonDTO person) {
        return person == null ? null : person.getId();
    }
}
