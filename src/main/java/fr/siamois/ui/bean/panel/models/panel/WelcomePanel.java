package fr.siamois.ui.bean.panel.models.panel;

import fr.siamois.domain.models.events.LangageChangeEvent;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/** The organization's home page. */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class WelcomePanel extends AbstractPanel implements Serializable {

    public WelcomePanel(ApplicationContext context) {
        super("common.location.home", "bi bi-house", "siamois-panel", context);
        refreshName();
    }

    @EventListener(LangageChangeEvent.class)
    public void refreshName() {
        this.titleCodeOrTitle = String.format("%s - %s",
                langBean.msg("common.location.home"),
                sessionSettingsBean.getSelectedInstitution().getName());
    }

    @Override
    public String ressourceUri() {
        return "/welcome";
    }

    @Override
    public String getPrefixPanelIndex() {
        return "welcome-panel";
    }

    @Override
    public String svgIcon() {
        return "/resources/img/svg/house.svg";
    }

    @Override
    public String reactPanelKind() {
        return "home";
    }

    @Override
    public Long reactOrganizationId() {
        return sessionSettingsBean.getSelectedInstitution().getId();
    }
}
