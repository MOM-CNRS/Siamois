package fr.siamois.ui.bean;

import fr.siamois.domain.events.publisher.InstitutionChangeEventPublisher;
import fr.siamois.domain.models.Bookmark;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.services.BookmarkService;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
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
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

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
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class NavBean implements Serializable {

    public static final String COMMON_BOOKMARK_SAVED = "common.bookmark.saved";
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

    private static final String RECORDING_UNIT_BASE_URI = "/recording-unit/";
    private static final String SPECIMEN_BASE_URI = "/specimen/";
    private static final String SPATIAL_UNIT_BASE_URI = "/spatial-unit/";

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
            reloadBookmarkedPanels();
        }
        return bookmarkedPanels;
    }

    public void reloadBookmarkedPanels() {
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

    public void bookmarkUnit(Long id, String titleCodeOrTitle, String ressourceBaseUri) {

        bookmarkService.save(
                sessionSettingsBean.getUserInfo(),
                ressourceBaseUri+ id,
                titleCodeOrTitle
        );
        MessageUtils.displayInfoMessage(langBean, COMMON_BOOKMARK_SAVED);
    }


    public void bookmarkRecordingUnit(RecordingUnit recordingUnit) {

        bookmarkUnit(recordingUnit.getId(),recordingUnit.getFullIdentifier(),RECORDING_UNIT_BASE_URI);

    }

    public void unBookmarkRecordingUnit(RecordingUnit recordingUnit) {

        unBookmark(RECORDING_UNIT_BASE_URI+recordingUnit.getId());

    }

    public void bookmark(Specimen specimen) {

        // Maybe check that ressource exists and user has access to it?
        bookmarkService.save(
                sessionSettingsBean.getUserInfo(),
                SPECIMEN_BASE_URI + specimen.getId(),
                specimen.getFullIdentifier()
        );
        MessageUtils.displayInfoMessage(langBean, COMMON_BOOKMARK_SAVED);
    }

    public void bookmark(SpatialUnit su) {

        bookmarkService.save(
                sessionSettingsBean.getUserInfo(),
                SPATIAL_UNIT_BASE_URI+ su.getId(),
                su.getName()
        );
        MessageUtils.displayInfoMessage(langBean, COMMON_BOOKMARK_SAVED);
    }

    public void unBookmark(String uri) {
        bookmarkService.deleteBookmark(
                sessionSettingsBean.getUserInfo(),
                uri
        );
        MessageUtils.displayInfoMessage(langBean, "common.bookmark.unsaved");
    }

    public Boolean isRessourceBookmarkedByUser(String ressourceUri) {
        return bookmarkService.isRessourceBookmarkedByUser(sessionSettingsBean.getUserInfo(), ressourceUri);
    }

    public void toggleRecordingUnitBookmark(RecordingUnitDTO recordingUnit) {
        if(Boolean.TRUE.equals(isRessourceBookmarkedByUser(RECORDING_UNIT_BASE_URI + recordingUnit.getId()))) {
            unBookmarkRecordingUnit(recordingUnit);
        }
        else {
            bookmarkRecordingUnit(recordingUnit);
        }
        reloadBookmarkedPanels();
    }

    public void toggleSpatialUnitBookmark(SpatialUnitDTO su) {
        final String uri = SPATIAL_UNIT_BASE_URI + su.getId();
        if(Boolean.TRUE.equals(isRessourceBookmarkedByUser(uri))) {
            unBookmark(uri);
        }
        else {
            bookmark(su);
        }
        reloadBookmarkedPanels();
    }

    public void toggleSpecimenBookmark(Specimen specimen) {
        if(Boolean.TRUE.equals(isRessourceBookmarkedByUser(SPECIMEN_BASE_URI + specimen.getId()))) {
            unBookmark(SPECIMEN_BASE_URI + specimen.getId());
        }
        else {
            bookmark(specimen);
        }
        reloadBookmarkedPanels();
    }

    public Boolean isRecordingUnitBookmarkedByUser(String fullIdentifier) {
        return isRessourceBookmarkedByUser(RECORDING_UNIT_BASE_URI+fullIdentifier);
    }

    public Boolean isSpatialUnitBookmarkedByUser(Long id) {
        return isRessourceBookmarkedByUser(SPATIAL_UNIT_BASE_URI+id);
    }

    public Boolean isSpecimenBookmarkedByUser(String fullIdentifier) {
        return isRessourceBookmarkedByUser(SPECIMEN_BASE_URI+fullIdentifier);
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
        reloadBookmarkedPanels();
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
