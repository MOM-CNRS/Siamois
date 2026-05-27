package fr.siamois.ui.bean.settings.project;


import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class ProjectUploadSettingsBean {

    ActionUnitDTO project;

    public void init(ActionUnitDTO project) {
        this.project = project;
    }

    @EventListener(LoginEvent.class)
    public void reset() {
        project = null;
    }

}
