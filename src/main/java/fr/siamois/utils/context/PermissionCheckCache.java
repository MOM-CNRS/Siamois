package fr.siamois.utils.context;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Per-request cache for {@code ProfilePermissionService}'s permission checks, bound to the current
 * thread exactly like {@link ExecutionContextHolder} and cleared at the same point (see
 * {@code UserInfoContextFilter}). Without it, a single view render that asks "can I edit this field?"
 * once per rendered field/button re-issues the exact same permission query dozens of times per request
 * (same person, same code) — see the perf investigation that added this class.
 */
public final class PermissionCheckCache {

    private PermissionCheckCache() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated.");
    }

    private static final ThreadLocal<Map<Key, Boolean>> CACHE = ThreadLocal.withInitial(HashMap::new);

    public static boolean computeIfAbsent(String scope, Long personId, Long secondaryId, String permissionCode,
                                           Supplier<Boolean> supplier) {
        Map<Key, Boolean> cache = CACHE.get();
        Key key = new Key(scope, personId, secondaryId, permissionCode);
        Boolean cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        boolean result = supplier.get();
        cache.put(key, result);
        return result;
    }

    /**
     * Clears the cache for the current thread. Must be called once per request (in a {@code finally}
     * block) so results never leak across requests handled by the same worker thread.
     */
    public static void clear() {
        CACHE.remove();
    }

    private record Key(String scope, Long personId, Long secondaryId, String permissionCode) {
    }
}
