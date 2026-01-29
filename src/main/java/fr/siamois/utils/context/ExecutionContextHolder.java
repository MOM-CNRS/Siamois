package fr.siamois.utils.context;

import fr.siamois.domain.models.UserInfo;

public final class ExecutionContextHolder {

    private static final ThreadLocal<UserInfo> CONTEXT = new ThreadLocal<>();

    public static void set(UserInfo info) {
        CONTEXT.set(info);
    }

    public static UserInfo get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}

