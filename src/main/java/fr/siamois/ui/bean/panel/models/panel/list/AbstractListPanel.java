package fr.siamois.ui.bean.panel.models.panel.list;

import fr.siamois.ui.bean.panel.models.panel.AbstractPanel;
import org.springframework.context.ApplicationContext;

import java.io.Serializable;

/**
 * An organization-wide list of one entity type ({@code /action-unit}, {@code /recording-unit}, …), in
 * the organization currently selected in the session.
 */
public abstract class AbstractListPanel extends AbstractPanel implements Serializable {

    private final String uriSegment;
    private final String entityType;
    private final String svgIcon;

    /**
     * @param uriSegment the list's resource URI segment, e.g. "action-unit" for {@code /action-unit}
     * @param entityType the React registry key of the listed entities, e.g. "project"
     */
    protected AbstractListPanel(String titleKey, String icon, String panelClass, String svgIcon,
                                String uriSegment, String entityType, ApplicationContext context) {
        super(titleKey, icon, panelClass, context);
        this.uriSegment = uriSegment;
        this.entityType = entityType;
        this.svgIcon = svgIcon;
    }

    @Override
    public String ressourceUri() {
        return "/" + uriSegment;
    }

    @Override
    public String getPrefixPanelIndex() {
        return uriSegment + "-list";
    }

    @Override
    public String svgIcon() {
        return svgIcon;
    }

    @Override
    public String reactPanelKind() {
        return "list";
    }

    @Override
    public String reactEntityType() {
        return entityType;
    }

    @Override
    public Long reactOrganizationId() {
        return sessionSettingsBean.getSelectedInstitution().getId();
    }
}
