package fr.siamois.domain.events.listener;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.history.InfoRevisionEntity;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.utils.context.ExecutionContextHolder;
import fr.siamois.utils.context.SystemUserLoader;
import jakarta.persistence.PrePersist;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Scope(value = "session")
@Component
public class InfoRevisionListener {

    private SystemUserLoader systemUserLoader;
    private final SessionSettingsBean sessionSettingsBean;
    private final ApplicationContext applicationContext;

    public InfoRevisionListener(SessionSettingsBean sessionSettingsBean, ApplicationContext applicationContext) {
        this.sessionSettingsBean = sessionSettingsBean;
        this.applicationContext = applicationContext;
    }

    /**
     * Method called before persisting an InfoRevisionEntity.
     * Is it used to set the updatedBy and updatedFrom fields in the revision information.
     *
     * @param entity the InfoRevisionEntity being persisted
     */
    @PrePersist
    private void onPersist(InfoRevisionEntity entity) {

        UserInfo info = resolveCurrentUser();

        entity.setUpdatedBy(info.getUser());
        entity.setUpdatedFrom(info.getInstitution());
    }

    private UserInfo resolveCurrentUser() {
        // --- 1. Try web session ---
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes && sessionSettingsBean != null) {
                UserInfo user = sessionSettingsBean.getUserInfo();
                if (user != null) return user;

            }
        } catch (Exception ignored) {

            // no op, goes to next block

        }

        // --- 2. Try ThreadLocal (ExecutionContextHolder) ---
        UserInfo user = ExecutionContextHolder.get();
        if (user != null) return user;

        // --- 3. Lazy load SystemUserLoader from context ---
        if (systemUserLoader == null) {
            systemUserLoader = applicationContext.getBean(SystemUserLoader.class);
        }
        return systemUserLoader.loadSystemUser();
    }


}
