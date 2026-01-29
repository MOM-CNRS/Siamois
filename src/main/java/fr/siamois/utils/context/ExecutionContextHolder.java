package fr.siamois.utils.context;

import fr.siamois.domain.models.UserInfo;

/**
 * Utility class for holding the execution context in a ThreadLocal.
 * This class is not intended to be instantiated.
 */
public final class ExecutionContextHolder {

    // Empêche l'instanciation de la classe
    private ExecutionContextHolder() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated.");
    }

    private static final ThreadLocal<UserInfo> CONTEXT = new ThreadLocal<>();

    /**
     * Sets the execution context for the current thread.
     * @param info The user information to store in the context.
     */
    public static void set(UserInfo info) {
        CONTEXT.set(info);
    }

    /**
     * Gets the execution context for the current thread.
     * @return The user information stored in the context, or null if not set.
     */
    public static UserInfo get() {
        return CONTEXT.get();
    }

    /**
     * Clears the execution context for the current thread.
     */
    public static void clear() {
        CONTEXT.remove();
    }
}
