package fr.siamois.ui.bean.panel.models.panel.single;

import fr.siamois.dto.entity.AbstractEntityDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.bean.RedirectBean;
import fr.siamois.ui.bean.panel.models.panel.AbstractPanel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;

import java.io.Serializable;

/**
 * One entity, opened by its resource URI ({@code /action-unit/12}, {@code /recording-unit/4}, …).
 * {@link #init()} loads it once — for the page title and the organization React is mounted in — and
 * turns an unknown entity into a 404 page and one the user may not see into a 403 page.
 */
@Slf4j
@Getter
@Setter
public abstract class AbstractEntityPanel<T extends AbstractEntityDTO> extends AbstractPanel implements Serializable {

    protected final RedirectBean redirectBean;
    private final String uriSegment;
    private final String entityType;
    private final String svgIcon;

    protected Long unitId;
    protected transient T unit;

    /**
     * @param uriSegment the entity's resource URI segment, e.g. "action-unit" for {@code /action-unit/12}
     * @param entityType the React registry key of the entity, e.g. "project"
     */
    protected AbstractEntityPanel(String titleKey, String icon, String panelClass, String svgIcon,
                                  String uriSegment, String entityType, ApplicationContext context) {
        super(titleKey, icon, panelClass, context);
        this.redirectBean = context.getBean(RedirectBean.class);
        this.uriSegment = uriSegment;
        this.entityType = entityType;
        this.svgIcon = svgIcon;
    }

    /** The entity, or an exception when there is none with that id. */
    protected abstract T loadUnit(Long id);

    /** The panel's title: the entity's identifier or name. */
    protected abstract String titleOf(T unit);

    /** Whether the user may open this entity. Its organization's access is already checked by FocusViewBean. */
    protected boolean canView(PersonDTO user, T unit) {
        return true;
    }

    public void init() {
        if (unitId == null) {
            redirectBean.redirectTo(HttpStatus.NOT_FOUND);
            return;
        }
        try {
            unit = loadUnit(unitId);
        } catch (RuntimeException e) {
            log.warn("{} {} could not be loaded: {}", entityType, unitId, e.getMessage());
            unit = null;
        }
        if (unit == null) {
            redirectBean.redirectTo(HttpStatus.NOT_FOUND);
            return;
        }
        titleCodeOrTitle = titleOf(unit);
        PersonDTO user = sessionSettingsBean.getUserInfo().getUser();
        if (!canView(user, unit)) {
            log.warn("Person {} tried to access {} {} without permission", user, entityType, unitId);
            redirectBean.redirectTo(HttpStatus.FORBIDDEN);
        }
    }

    @Override
    public String ressourceUri() {
        return "/" + uriSegment + "/" + unitId;
    }

    @Override
    public String getPrefixPanelIndex() {
        return uriSegment + "-" + unitId;
    }

    @Override
    public String svgIcon() {
        return svgIcon;
    }

    @Override
    public String reactPanelKind() {
        return "detail";
    }

    @Override
    public String reactEntityType() {
        return entityType;
    }

    @Override
    public Long reactEntityId() {
        return unitId;
    }

    @Override
    public Long reactOrganizationId() {
        return unit != null && unit.getCreatedByInstitution() != null ? unit.getCreatedByInstitution().getId() : null;
    }
}
