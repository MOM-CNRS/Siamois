package fr.siamois.ui.bean.panel.models.panel.list;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.ui.bean.panel.models.PanelBreadcrumb;
import fr.siamois.ui.lazydatamodel.ActionUnitLazyDataModel;
import fr.siamois.ui.lazydatamodel.BaseLazyDataModel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.List;


@Slf4j
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Getter
@Setter
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ActionUnitListPanel extends AbstractListPanel<ActionUnit> implements Serializable {

    // locals
    private String actionUnitListErrorMessage;


    @Override
    protected long countUnitsByInstitution() {
        return actionUnitService.countByInstitution(sessionSettingsBean.getSelectedInstitution());
    }

    @Override
    protected BaseLazyDataModel<ActionUnit> createLazyDataModel() {
        return new ActionUnitLazyDataModel(actionUnitService, sessionSettingsBean, langBean);
    }

    @Override
    protected void setErrorMessage(String msg) {
        this.actionUnitListErrorMessage = msg;
    }


    public ActionUnitListPanel(ApplicationContext context) {
        super("panel.title.allactionunit",
                "bi bi-arrow-down-square",
                "siamois-panel action-unit-panel list-panel",
                context);
    }

    @Override
    public String displayHeader() {
        return "/panel/header/actionUnitListPanelHeader.xhtml";
    }


    @Override
    protected String getBreadcrumbKey() {
        return "common.entity.actionUnits";
    }

    @Override
    protected String getBreadcrumbIcon() {
        return "bi bi-arrow-down-square";
    }



    public List<Person> authorsAvailable() {
        return personService.findAllAuthorsOfActionUnitByInstitution(sessionSettingsBean.getSelectedInstitution());
    }



    @Override
    public String display() {
        return "/panel/actionUnitListPanel.xhtml";
    }

    @Override
    public String ressourceUri() {
        return "/action-unit";
    }

    public static class ActionUnitListPanelBuilder {

        private final ActionUnitListPanel actionUnitListPanel;

        public ActionUnitListPanelBuilder(ObjectProvider<ActionUnitListPanel> actionUnitListPanelProvider) {
            this.actionUnitListPanel = actionUnitListPanelProvider.getObject();
        }

        public ActionUnitListPanel.ActionUnitListPanelBuilder breadcrumb(PanelBreadcrumb breadcrumb) {
            actionUnitListPanel.setBreadcrumb(breadcrumb);

            return this;
        }

        public ActionUnitListPanel build() {
            actionUnitListPanel.init();
            return actionUnitListPanel;
        }
    }





}
