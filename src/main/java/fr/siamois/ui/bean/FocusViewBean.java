package fr.siamois.ui.bean;

import fr.siamois.domain.events.publisher.InstitutionChangeEventPublisher;
import fr.siamois.domain.events.publisher.LoginEventPublisher;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.services.ResourceInstitutionService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.bean.panel.PanelFactory;
import fr.siamois.ui.bean.panel.models.panel.AbstractPanel;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.io.Serializable;
import java.util.Base64;
import java.util.Objects;

@Slf4j
@Named
@ViewScoped
@Data
@RequiredArgsConstructor
public class FocusViewBean implements Serializable {

    private final transient PanelFactory panelFactory;
    private final transient HistoryBean historyBean;
    private final LangBean langBean;
    private final SessionSettingsBean sessionSettingsBean;
    private final transient ResourceInstitutionService resourceInstitutionService;
    private final transient ProfilePermissionService profilePermissionService;
    private final transient InstitutionChangeEventPublisher institutionChangeEventPublisher;
    private final transient LoginEventPublisher loginEventPublisher;
    private final transient RedirectBean redirectBean;


    private AbstractPanel mainPanel;

    private String mainPanelId;
    private String secondaryPanelId; // optionnel

    private String decodedMain;
    private String decodedSide;

    // tokens reçus depuis l'URL
    private String mainToken;
    private String secondaryToken;
    private String backToken;

    /** A resource URI: its type, and its entity id when it names one entity rather than a list. */
    private record ParsedPath(String type, Long id) {
    }

    // The query string (the former ?tab= and ?viewId=, still in old bookmarks) is ignored: React
    // keeps its own tab and list state.
    private ParsedPath parsePath(String path) {
        if (path.startsWith("/")) path = path.substring(1);
        String[] parts = path.split("\\?", 2)[0].split("/");
        return new ParsedPath(parts[0], parts.length > 1 ? Long.parseLong(parts[1]) : null);
    }

    public void beforeInit() {
        HistoryBean.HistoryItem newEntry = new HistoryBean.HistoryItem();

        if (mainToken != null) {
            ParsedPath parsedMain = parsePath(decodeToken(mainToken));

            if (!activateInstitutionOf(parsedMain)) {
                return;
            }

            HistoryBean.HistoryItemComponent main = new HistoryBean.HistoryItemComponent();
            mainPanel = panelFactory.create(parsedMain.type(), parsedMain.id());
            mainPanel.setRoot(true);
            if (backToken != null) {
                mainPanel.setGoBackUrl(decodeToken(backToken));
            }
            main.setIcon(mainPanel.getIcon());
            main.setTitle(mainPanel.resolveTitleOrTitleCode());
            main.setUri(mainPanel.ressourceUri());
            main.setStyleClass(mainPanel.getPanelClass());
            newEntry.setMain(main);
        }

        if (secondaryToken != null && !secondaryToken.isEmpty()) {
            HistoryBean.HistoryItemComponent side = new HistoryBean.HistoryItemComponent();

            ParsedPath parsedSide = parsePath(decodeToken(secondaryToken));
            AbstractPanel overviewPanel = panelFactory.create(parsedSide.type(), parsedSide.id());
            overviewPanel.setRoot(false);
            mainPanel.setParentOrOverview(overviewPanel);
            overviewPanel.setParentOrOverview(mainPanel);
            side.setIcon(overviewPanel.getIcon());
            side.setTitle(overviewPanel.resolveTitleOrTitleCode());

            side.setUri(overviewPanel.ressourceUri());
            side.setStyleClass(overviewPanel.getPanelClass());
            newEntry.setSecondary(side);
        }

        historyBean.addItem(newEntry);

    }

    private boolean activateInstitutionOf(ParsedPath parsed) {
        InstitutionDTO target = resourceInstitutionService
                .findInstitutionOf(parsed.type(), parsed.id())
                .orElse(null);

        if (target == null) {
            // List and welcome panels belong to no entity: they are displayed in the active organisation.
            return true;
        }

        UserInfo userInfo = sessionSettingsBean.getUserInfo();
        PersonDTO user = userInfo == null ? null : userInfo.getUser();
        if (!profilePermissionService.canAccessInstitution(user, target)) {
            log.warn("Person {} tried to focus on {}/{} without access to organisation {}",
                    user, parsed.type(), parsed.id(), target.getId());
            redirectBean.redirectTo(HttpStatus.FORBIDDEN);
            return false;
        }

        InstitutionDTO active = sessionSettingsBean.getSelectedInstitution();
        if (active != null && Objects.equals(active.getId(), target.getId())) {
            return true;
        }

        sessionSettingsBean.setSelectedInstitution(target);
        historyBean.getItems().clear(); // entries of the previous organisation are no longer reachable
        institutionChangeEventPublisher.publishInstitutionChangeEvent();
        loginEventPublisher.publishLoginEvent();
        return true;
    }


    private String decodeToken(String token) {
        return new String(Base64.getUrlDecoder().decode(token));
    }

}
