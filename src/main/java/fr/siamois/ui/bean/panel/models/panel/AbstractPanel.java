package fr.siamois.ui.bean.panel.models.panel;

import fr.siamois.ui.bean.panel.models.PanelBreadcrumb;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

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
    @Getter(AccessLevel.NONE)
    protected PanelBreadcrumb breadcrumb;
    @Getter(AccessLevel.NONE)
    protected Boolean isBreadcrumbVisible = true;
    protected Boolean collapsed = false;

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
        if(!showTime) {
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

}