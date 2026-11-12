package fr.siamois.ui.bean.panel.models.panel;

import fr.siamois.domain.models.events.LangageChangeEvent;
import fr.siamois.ui.bean.LangBean;
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

    /** The resource URI of the home page, as recorded in the history and the bookmarks. */
    public static final String RESOURCE_URI = "/welcome";

    private static final String HOME_LABEL_CODE = "common.location.home";

    public WelcomePanel(ApplicationContext context) {
        super(HOME_LABEL_CODE, "bi bi-house", "siamois-panel", context);
        refreshName();
    }

    /**
     * Builds the title of the home page of an organisation, in the user's language.
     *
     * @param langBean        the bean resolving the user's messages
     * @param institutionName the name of the organisation
     * @return the title, e.g. « Accueil - Mon organisation »
     */
    public static String titleOf(LangBean langBean, String institutionName) {
        return langBean.msg(HOME_LABEL_CODE) + " - " + institutionName;
    }

    /**
     * Recomputes the title from the current language and organisation name.
     */
    @EventListener(LangageChangeEvent.class)
    public void refreshName() {
        this.titleCodeOrTitle = titleOf(langBean, sessionSettingsBean.getSelectedInstitution().getName());
    }

    @Override
    public String ressourceUri() {
        return RESOURCE_URI;
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
        return sessionSettingsBean.getSelectedInstitutionId();
    }
}
