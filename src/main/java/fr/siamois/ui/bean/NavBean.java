package fr.siamois.ui.bean;

import fr.siamois.domain.models.Bookmark;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.services.BookmarkService;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.publisher.InstitutionChangeEventPublisher;
import fr.siamois.ui.bean.converter.InstitutionConverter;
import fr.siamois.ui.bean.panel.FlowBean;
import fr.siamois.ui.bean.panel.models.panel.AbstractPanel;
import fr.siamois.ui.bean.settings.InstitutionListSettingsBean;
import fr.siamois.utils.MessageUtils;
import jakarta.faces.context.FacesContext;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import javax.faces.bean.SessionScoped;
import java.io.Serializable;
import java.util.List;


/**
 * Bean to manage the navigation bar of the application. Allows the user to select a team.
 *
 * @author Julien Linget
 */
@Slf4j
@Component
@Getter
@Setter
@SessionScoped
public class NavBean implements Serializable {

    private final SessionSettingsBean sessionSettingsBean;
    private final transient InstitutionChangeEventPublisher institutionChangeEventPublisher;
    private final transient InstitutionConverter converter;
    private final transient InstitutionService institutionService;
    private final RedirectBean redirectBean;
    private final InstitutionListSettingsBean institutionListSettingsBean;
    private final transient BookmarkService bookmarkService;
    private final FlowBean flowBean;
    private final LangBean langBean;

    private ApplicationMode applicationMode = ApplicationMode.SIAMOIS;

    @Getter(AccessLevel.NONE)
    private transient List<Bookmark> bookmarkedPanels = null;

    private static final String RECORDING_UNIT_BASE_URI = "/recording-unit";

    public NavBean(SessionSettingsBean sessionSettingsBean,
                   InstitutionChangeEventPublisher institutionChangeEventPublisher,
                   InstitutionConverter converter,
                   InstitutionService institutionService,
                   RedirectBean redirectBean,
                   InstitutionListSettingsBean institutionListSettingsBean, BookmarkService bookmarkService, FlowBean flowBean, LangBean langBean) {
        this.sessionSettingsBean = sessionSettingsBean;
        this.institutionChangeEventPublisher = institutionChangeEventPublisher;
        this.converter = converter;
        this.institutionService = institutionService;
        this.redirectBean = redirectBean;
        this.institutionListSettingsBean = institutionListSettingsBean;
        this.bookmarkService = bookmarkService;
        this.flowBean = flowBean;
        this.langBean = langBean;
    }

    public boolean userIsSuperAdmin() {
        return sessionSettingsBean.getUserInfo().getUser().isSuperAdmin();
    }

    public Institution getSelectedInstitution() {
        return sessionSettingsBean.getSelectedInstitution();
    }

    public Person currentUser() {
        return sessionSettingsBean.getAuthenticatedUser();
    }

    public boolean isSiamoisMode() {
        return applicationMode == ApplicationMode.SIAMOIS;
    }

    public boolean isSettingsMode() {
        return applicationMode == ApplicationMode.SETTINGS;
    }

    public void goToOrganisationSettings() {
        institutionListSettingsBean.init();
        redirectBean.redirectTo("/settings/organisation");
    }

    public void addToBookmarkedPanels(AbstractPanel panel) {
        bookmarkedPanels.add(bookmarkService.save(sessionSettingsBean.getUserInfo(), panel));
    }

    public void removeFromBookmarkedPanels(AbstractPanel panel) {
        bookmarkService.delete(sessionSettingsBean.getUserInfo(), panel);
    }

    public List<Bookmark> getBookmarkedPanels() {
        if (bookmarkedPanels == null) {
            bookmarkedPanels = bookmarkService.findAll(sessionSettingsBean.getUserInfo());
        }
        return bookmarkedPanels;
    }

    public void reloadBookarkedPanels() {
        bookmarkedPanels = bookmarkService.findAll(sessionSettingsBean.getUserInfo());
    }

    public String bookmarkTitle(Bookmark bookmark) {
        try {
            return langBean.msg(bookmark.getTitleCode());
        } catch (NoSuchMessageException e) {
            return bookmark.getTitleCode();
        }
    }

    public void logout() {
        SecurityContextHolder.getContext().setAuthentication(null);
        FacesContext.getCurrentInstance().getExternalContext().invalidateSession();
        redirectBean.redirectTo("/");
    }

    public void bookmarkRecordingUnit(String fullIdentifier) {

        // Maybe check that ressource exists and user has access to it?
        bookmarkService.save(
                sessionSettingsBean.getUserInfo(),
                RECORDING_UNIT_BASE_URI+fullIdentifier,
                fullIdentifier
        );
        MessageUtils.displayInfoMessage(langBean, "common.bookmark.saved");
    }

    public void unBookmarkRecordingUnit(String fullIdentifier) {
        bookmarkService.deleteBookmark(
                sessionSettingsBean.getUserInfo(),
                RECORDING_UNIT_BASE_URI+fullIdentifier
        );
        MessageUtils.displayInfoMessage(langBean, "common.bookmark.unsaved");
    }

    public Boolean isRessourceBookmarkedByUser(String ressourceUri) {
        return bookmarkService.isRessourceBookmarkedByUser(sessionSettingsBean.getUserInfo(), ressourceUri);
    }

    public void toggleRecordingUnitBookmark(String fullIdentifier) {
        if(Boolean.TRUE.equals(isRessourceBookmarkedByUser(RECORDING_UNIT_BASE_URI+fullIdentifier))) {
            unBookmarkRecordingUnit(fullIdentifier);
        }
        else {
            bookmarkRecordingUnit(fullIdentifier);
        }
        reloadBookarkedPanels();
    }

    public Boolean isRecordingUnitBookmarkedByUser(String fullIdentifier) {
        return isRessourceBookmarkedByUser(RECORDING_UNIT_BASE_URI+fullIdentifier);
    }

    public Boolean isPanelBookmarkedByUser(AbstractPanel panel) {
        return isRessourceBookmarkedByUser(panel.ressourceUri());
    }

    public void togglePanelBookmark(AbstractPanel panel) {
        if(Boolean.TRUE.equals(isPanelBookmarkedByUser(panel))) {
            removeFromBookmarkedPanels(panel);
        }
        else {
            addToBookmarkedPanels(panel);
        }
        reloadBookarkedPanels();
    }

    @EventListener(LoginEvent.class)
    public void reset() {
        bookmarkedPanels = null;
    }

    public enum ApplicationMode {
        SIAMOIS,
        SETTINGS
    }

}
