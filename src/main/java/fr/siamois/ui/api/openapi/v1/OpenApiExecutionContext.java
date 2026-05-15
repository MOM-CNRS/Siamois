package fr.siamois.ui.api.openapi.v1;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.utils.context.ExecutionContextHolder;

import java.util.function.Supplier;

/**
 * Contexte utilisateur (langue, institution) pour les appels formulaire hors JSF (API REST).
 */
public final class OpenApiExecutionContext {

    private OpenApiExecutionContext() {
    }

    public static void runWithUserInfo(UserInfo userInfo, Runnable action) {
        if (userInfo == null) {
            action.run();
            return;
        }
        ExecutionContextHolder.set(userInfo);
        try {
            action.run();
        } finally {
            ExecutionContextHolder.clear();
        }
    }

    public static <T> T callWithUserInfo(UserInfo userInfo, Supplier<T> action) {
        if (userInfo == null) {
            return action.get();
        }
        ExecutionContextHolder.set(userInfo);
        try {
            return action.get();
        } finally {
            ExecutionContextHolder.clear();
        }
    }
}
