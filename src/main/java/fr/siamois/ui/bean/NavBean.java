package fr.siamois.ui.bean;

import fr.siamois.domain.events.publisher.InstitutionChangeEventPublisher;
import fr.siamois.domain.models.Bookmark;
import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.services.BookmarkService;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.dto.entity.*;
import fr.siamois.ui.bean.converter.InstitutionConverter;
import fr.siamois.ui.bean.panel.FlowBean;
import fr.siamois.ui.bean.panel.models.panel.AbstractPanel;
import fr.siamois.ui.bean.settings.InstitutionListSettingsBean;
import fr.siamois.utils.MessageUtils;
import io.micrometer.common.lang.Nullable;
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

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;


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


    private final SessionSettingsBean sessionSettingsBean;
    private final transient InstitutionChangeEventPublisher institutionChangeEventPublisher;
    private final transient InstitutionConverter converter;
    private final transient InstitutionService institutionService;
    private final RedirectBean redirectBean;
    private final InstitutionListSettingsBean institutionListSettingsBean;
    private final transient BookmarkService bookmarkService;
    private final FlowBean flowBean;
    private final LangBean langBean;

    private String urlToGoBack; // URL to go back from settings

    public static final String COMMON_BOOKMARK_SAVED = "common.bookmark.saved";
    public static final String FLOW = "FLOW";
    public static final String FOCUS = "FOCUS";

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

    public InstitutionDTO getSelectedInstitution() {
        return sessionSettingsBean.getSelectedInstitution();
    }

    public PersonDTO currentUser() {
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


    public void bookmarkRecordingUnit(RecordingUnitDTO recordingUnit) {

        bookmarkUnit(recordingUnit.getId(),recordingUnit.getFullIdentifier(),RECORDING_UNIT_BASE_URI);

    }

    public void unBookmarkRecordingUnit(RecordingUnitDTO recordingUnit) {

        unBookmark(RECORDING_UNIT_BASE_URI+recordingUnit.getId());

    }

    public void bookmark(SpecimenDTO specimen) {

        // Maybe check that ressource exists and user has access to it?
        bookmarkService.save(
                sessionSettingsBean.getUserInfo(),
                SPECIMEN_BASE_URI + specimen.getId(),
                specimen.getFullIdentifier()
        );
        MessageUtils.displayInfoMessage(langBean, COMMON_BOOKMARK_SAVED);
    }

    public void bookmark(SpatialUnitDTO su) {

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

    public void toggleSpecimenBookmark(SpecimenDTO specimen) {
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

    public void backFromSettings() throws IOException {
        setApplicationMode(NavBean.ApplicationMode.SIAMOIS);
        if (urlToGoBack != null && !urlToGoBack.isEmpty()) {
            FacesContext.getCurrentInstance()
                    .getExternalContext()
                    .redirect(urlToGoBack);
        } else {
            redirectBean.redirectTo("/focus/L3dlbGNvbWU=");
        }
    }

    public enum ApplicationMode {
        SIAMOIS,
        SETTINGS
    }

    public void goToActionUnitList(String mode) throws IOException {
        if(Objects.equals(mode, FLOW)) {
            flowBean.addActionUnitListPanel();
            flowBean.redirectToDashboard();
        }
        if(Objects.equals(mode, FOCUS)) {
            flowBean.redirectToFocus("/action-unit");
        }
    }

    public void goToRecordingUnitList(String mode) throws IOException {
        if(Objects.equals(mode, FLOW)) {
            flowBean.addRecordingUnitListPanel();
            flowBean.redirectToDashboard();
        }
        if(Objects.equals(mode, FOCUS)) {
            flowBean.redirectToFocus("/recording-unit");
        }
    }

    public void goToSpatialUnitList(String mode) throws IOException {
        if(Objects.equals(mode, FLOW)) {
            flowBean.addSpatialUnitListPanel();
            flowBean.redirectToDashboard();
        }
        if(Objects.equals(mode, FOCUS)) {
            flowBean.redirectToFocus("/spatial-unit");
        }
    }

    public void goToSpecimenList(String mode) throws IOException {
        if(Objects.equals(mode, FLOW)) {
            flowBean.addSpecimenListPanel();
            flowBean.redirectToDashboard();
        }
        if(Objects.equals(mode, FOCUS)) {
            flowBean.redirectToFocus("/specimen");
        }
    }

    public void redirectToBookmarked(String resource) {
        try {
            flowBean.redirectToFocus(resource);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    public void redirectToActionUnit() throws IOException {
        String id = FacesContext.getCurrentInstance()
                .getExternalContext()
                .getRequestParameterMap()
                .get("id");
        flowBean.redirectToFocus("/action-unit/" + id);
    }

    public void redirectToActionUnit(Long actionUnitId, @Nullable Integer tabIndex) throws IOException {

        // Construction de l'URL de base
        StringBuilder url = new StringBuilder("/action-unit/");
        url.append(actionUnitId);

        // Ajout du paramètre d'onglet seulement s'il est présent
        if (tabIndex != null) {
            url.append("?tab=").append(tabIndex);
        }

        // Appel au flowBean pour la redirection finale
        flowBean.redirectToFocus(url.toString());
    }

    public void redirectToSpatialUnit() throws IOException {
        String id = FacesContext.getCurrentInstance()
                .getExternalContext()
                .getRequestParameterMap()
                .get("id");
        flowBean.redirectToFocus(SPATIAL_UNIT_BASE_URI + id);
    }

}
