package fr.siamois.ui.bean.panel.models.panel;

import fr.siamois.domain.services.BookmarkService;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationContext;

import java.io.Serializable;
import java.util.Objects;

/**
 * What {@code focus.xhtml} mounts the React main panel from: a descriptor of the page's main entity
 * or list — its title, icon, resource URI and bookmark state for the page chrome and the history, and
 * the {@code react*} values handed to {@code window.SiamoisMainPanel.mount} (panel/reactPanelMount.xhtml).
 * The panel's content itself is entirely React's.
 * <p>
 * A panel may carry an overview ({@link #parentOrOverview}): the entity React shows in its side pane,
 * kept here so a reload (F5) and the navigation history reopen it.
 */
@Getter
@Setter
public abstract class AbstractPanel implements Serializable {

    protected final SessionSettingsBean sessionSettingsBean;
    protected final LangBean langBean;
    protected final transient BookmarkService bookmarkService;

    protected String titleCodeOrTitle;
    protected String panelClass;
    protected String icon;
    private String goBackUrl;
    protected boolean isRoot = true; // Is the panel a root panel or an overview
    protected AbstractPanel parentOrOverview; // if root, the panel can have an overview

    protected AbstractPanel(String titleCodeOrTitle, String icon, String panelClass, ApplicationContext context) {
        this.titleCodeOrTitle = titleCodeOrTitle;
        this.icon = icon;
        this.panelClass = panelClass;
        this.sessionSettingsBean = context.getBean(SessionSettingsBean.class);
        this.langBean = context.getBean(LangBean.class);
        this.bookmarkService = context.getBean(BookmarkService.class);
    }

    /**
     * <p>The resource URI of the panel. When the user uses this URI, the panel is opened in focus.</p>
     * <p>A Redirection Controller should define the given URI process.</p>
     *
     * @return a string representing the resource URI
     */
    public abstract String ressourceUri();

    public abstract String getPrefixPanelIndex();

    public abstract String svgIcon();

    /** "home" | "list" | "detail" — matches window.SiamoisMainPanel.mount's panelKind. */
    public abstract String reactPanelKind();

    /** Registry key matching a frontend entities/&lt;type&gt;/config.tsx (e.g. "project"), or null for a panel with no single entity (Home). */
    public String reactEntityType() {
        return null;
    }

    /** The panel's own entity id for a "detail" reactPanelKind, or null otherwise. */
    public Long reactEntityId() {
        return null;
    }

    /** Institution/organization id scoping this panel's data, or null when not applicable. */
    public Long reactOrganizationId() {
        return null;
    }

    public boolean isBookmarked() {
        return bookmarkService.isRessourceBookmarkedByUser(sessionSettingsBean.getUserInfo(), ressourceUri());
    }

    public String resolveTitleOrTitleCode() {
        try {
            return langBean.msg(titleCodeOrTitle);
        } catch (Exception e) {
            return titleCodeOrTitle;
        }
    }

    public String getPanelIndex() {
        if (isRoot) {
            return getPrefixPanelIndex();
        }
        return getPrefixPanelIndex() + "-overview";
    }

    /**
     * {@link #getPanelIndex()} made safe for a JS identifier: p:remoteCommand turns its name into a
     * global function assignment, and a hyphenated index ("action-unit-list") made that a syntax
     * error, so none of the React panel's bridged actions (reactPanelActions.xhtml) ever existed.
     */
    public String getJsPanelIndex() {
        return getPanelIndex().replaceAll("[^A-Za-z0-9_]", "_");
    }

    /** React closed the overview: forget it, so a reload doesn't reopen it. */
    public void closeOverview() {
        parentOrOverview = null;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        AbstractPanel other = (AbstractPanel) obj;
        return Objects.equals(this.ressourceUri(), other.ressourceUri());
    }

    @Override
    public int hashCode() {
        return Objects.hash(ressourceUri());
    }
}
