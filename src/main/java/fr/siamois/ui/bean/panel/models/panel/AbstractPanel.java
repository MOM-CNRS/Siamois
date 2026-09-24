package fr.siamois.ui.bean.panel.models.panel;

import fr.siamois.dto.view.TableViewState;
import fr.siamois.ui.bean.dialog.newunit.NewUnitContext;
import fr.siamois.ui.bean.dialog.newunit.UnitKind;
import fr.siamois.ui.bean.panel.models.PanelBreadcrumb;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.primefaces.PrimeFaces;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Abstract class representing a panel in the UI.
 * Provides common properties and methods for all panels.
 */
@Getter
@Setter
public abstract class AbstractPanel implements Serializable {

    protected String titleCodeOrTitle;
    protected String panelClass;
    protected String icon;
    private String goBackUrl;
    @Getter(AccessLevel.NONE)
    protected PanelBreadcrumb breadcrumb;
    @Getter(AccessLevel.NONE)
    protected Boolean isBreadcrumbVisible = true;
    protected Boolean collapsed = false;
    protected Boolean loaded = false;
    protected boolean isRoot = true; // Is the panel a root panel or an overview
    protected AbstractPanel parentOrOverview; // if root, the panel can display have an overview
    protected String errorMessage;


    public abstract String buildBookmarkUrl();

    public abstract void applyViewState(TableViewState state);

    public abstract boolean isDirty();

    public abstract boolean isBookmarked();

    public abstract void togglePanelBookmark() ;

    public String getActionToolbarId() {
        if (isRoot) {
            return "actionForm-"+getPanelIndex();
        }
        return "actionForm-"+parentOrOverview.getPanelIndex();
    }

    public abstract boolean canUserUpdateView();

    public void loadData() {
        // deffer loading here ?
        loaded = true;
    }

    public abstract void refresh();

    protected AbstractPanel() {
    }

    /**
     * Formats the given OffsetDateTime to a string in UTC timezone.
     *
     * @param dateTime the OffsetDateTime to format
     * @param showTime do we show the time?
     * @return the formatted date string, or an empty string if dateTime is null
     */
    public String formatUtcDateTime(OffsetDateTime dateTime, boolean showTime) {
        if (dateTime == null) return "";
        String pattern = "dd/MM/yyyy HH:mm";
        if (!showTime) {
            pattern = "dd/MM/yyyy";
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern).withZone(ZoneOffset.UTC);
        return formatter.format(dateTime);
    }

    /**
     * Formats the given OffsetDateTime to a string in UTC timezone.
     *
     * @param dateTime the OffsetDateTime to format
     * @return the formatted date string, or an empty string if dateTime is null
     */
    public String formatUtcDateTime(OffsetDateTime dateTime) {
        return formatUtcDateTime(dateTime, false);
    }

    protected AbstractPanel(String titleCodeOrTitle, String icon, String panelClass) {
        this.titleCodeOrTitle = titleCodeOrTitle;
        this.icon = icon;
        this.panelClass = panelClass;
    }

    /**
     * Abstract method to return the path to the panel's template.
     * Must be implemented by subclasses to provide specific display logic.
     *
     * @return a string representation of the panel content
     */
    public abstract String display();

    /**
     * <p>Abstract method to return the resource URI associated with the panel.</p>
     * <p>Must be implemented by subclasses to provide specific resource identification.</p>
     * <p>When the user uses this URI, a new panel should appear at the top of the flow.</p>
     * <p>A Redirection Controller should define the given URI process.</p>
     *
     * @return a string representing the resource URI
     */
    public abstract String ressourceUri();

    /**
     * Returns the path to the header template for the panel.
     * This method can be overridden by subclasses to provide a specific header.
     *
     * @return a string representing the path to the header template, or null if not applicable
     */
    public String displayHeader() {
        return null;
    }

    public UnitKind getCreationUnitKind() {
        return null;
    }

    public NewUnitContext buildCreationContext(UnitKind kind) {
        return NewUnitContext.builder()
                .kindToCreate(kind)
                .trigger(NewUnitContext.Trigger.homePanel())
                .build();
    }

    public boolean canDuplicate() {
        return false;
    }

    /** Only an Action Unit panel has project-level settings to jump to from its header toolbar. */
    public boolean canOpenInProjectSettings() {
        return false;
    }

    public void duplicate() {
        // no-op by default
    }

    public boolean isBreadcrumbVisible() {
        if (breadcrumb == null) return false;
        return isBreadcrumbVisible;
    }

    public PanelBreadcrumb getBreadcrumb() {
        if (breadcrumb == null) return new PanelBreadcrumb();
        return breadcrumb;
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

    public abstract String getPrefixPanelIndex();
    public abstract String svgIcon();

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

    public String getPanelTypeClass() {
        return "";
    }

    public abstract String resolveTitleOrTitleCode();

    public String getLeftSpltterSize() {
        if (parentOrOverview == null) {
            return "none";
        } else {
            return "block";
        }
    }

    public void closeOverview() {

        parentOrOverview = null;
        PrimeFaces.current().ajax().update("sideview");
        PrimeFaces.current().executeScript("hideSideview('" + getPanelIndex() + "');");


    }



    public abstract boolean hasPreviousNext() ;

    /**
     * Whether this panel's whole body (both the main pane and, when present, the overview pane —
     * panel/panelContent.xhtml) should be rendered by the React main-panel bundle instead of the
     * legacy JSF templates. Default false: every unmigrated entity type keeps its exact current
     * behavior, deferred loading included. See the migration plan §7/§8 phase 8.
     */
    public boolean isReactPanelEnabled() {
        return false;
    }

    /** "home" | "list" | "detail" — matches window.SiamoisMainPanel.mount's panelKind, or null when {@link #isReactPanelEnabled()} is false. */
    public String reactPanelKind() {
        return null;
    }

    /**
     * Whether the React block is the one focus.xhtml actually renders for this panel right now —
     * a JSF overview next to a React main panel (or vice versa) isn't supported, so a React main
     * panel with a still-JSF overview falls back to the JSF block too, on both sides together.
     * focus.xhtml gates its two mutually-exclusive blocks (id="react-panel-..." vs id="panel-...")
     * on exactly this condition; kept here once instead of repeated inline in the template so it
     * can't drift between the two `rendered` attributes and {@link #getPanelContainerId()}.
     */
    public boolean isReactPanelActive() {
        return isReactPanelEnabled() && (parentOrOverview == null || parentOrOverview.isReactPanelEnabled());
    }

    /**
     * The id of whichever container focus.xhtml actually rendered for this panel's content —
     * {@code p:blockUI}'s own {@code block=} attribute must resolve to a real element: pointing it
     * at a hardcoded "panel-" prefix broke the moment the React block (a different id) took over,
     * leaving blockUI's target permanently unresolved — every DOM mutation on the page then
     * re-threw in PrimeFaces' own MutationObserver (core.js#registerMutationObserver: target.get(0)
     * undefined, `.id` read off it), which is the "tas d'erreurs en redimensionnant" reported once
     * the React panel shipped.
     */
    public String getPanelContainerId() {
        return (isReactPanelActive() ? "react-panel-" : "panel-") + getPrefixPanelIndex();
    }

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
}