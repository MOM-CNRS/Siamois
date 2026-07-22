package fr.siamois.utils.context;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.utils.AuthenticatedUserUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for holding the execution context in a ThreadLocal.
 * This class is not intended to be instantiated.
 * <p>
 * Servlet containers and {@code @Async} executors hand threads back to a pool once a task ends, so a
 * value left behind by one request stays visible to the <em>next</em> request served by that same
 * thread. Every writer must therefore pair its {@link #set(UserInfo)} with a {@link #clear()} in a
 * {@code finally} block; for web requests this is the job of
 * {@code fr.siamois.ui.config.security.UserInfoContextFilter}.
 * <p>
 * {@link #get()} additionally cross-checks the stored context against the authenticated user of the
 * current request, so that a context which somehow outlived its request is dropped rather than served
 * to somebody else. That is a safety net, not a substitute for clearing.
 * <p>
 * The backing {@link ThreadLocal} is deliberately <strong>not</strong> an {@link InheritableThreadLocal}:
 * a pooled worker would inherit the value present when the thread was <em>created</em> and then keep
 * serving it forever. Threads must be given the context explicitly.
 */
@Slf4j
public final class ExecutionContextHolder {

    // Empêche l'instanciation de la classe
    private ExecutionContextHolder() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated.");
    }

    private static final ThreadLocal<UserInfo> CONTEXT = new ThreadLocal<>();

    /**
     * Sets the execution context for the current thread.
     * <p>
     * The caller owns the lifetime of what it binds and must clear it before the thread is released.
     *
     * @param info The user information to store in the context. A null value unbinds the context
     *             rather than storing an empty entry.
     */
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

    /**
     * Gets the execution context for the current thread.
     * <p>
     * When the current request is authenticated, the stored context must describe that same person. A
     * mismatch means the value was left behind by an earlier request on this pooled thread, so it is
     * discarded and reported instead of being handed to the wrong user. When there is no authentication
     * at all (startup initializers, background imports) the context is taken at face value, since there
     * is nothing to cross-check it against.
     *
     * @return The user information stored in the context, or null if not set or not trustworthy.
     */
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
