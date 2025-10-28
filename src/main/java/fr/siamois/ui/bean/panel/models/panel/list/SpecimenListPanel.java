package fr.siamois.ui.bean.panel.models.panel.list;


import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.services.BookmarkService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.FieldService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.panel.models.PanelBreadcrumb;
import fr.siamois.ui.lazydatamodel.BaseLazyDataModel;
import fr.siamois.ui.lazydatamodel.SpecimenLazyDataModel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;


@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Getter
@Setter
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class SpecimenListPanel extends AbstractListPanel<Specimen>  implements Serializable {

    private final transient SpecimenService specimenService;

    // locals
    private String actionUnitListErrorMessage;



    @Override
    protected long countUnitsByInstitution() {
        return specimenService.countByInstitution(sessionSettingsBean.getSelectedInstitution());
    }

    @Override
    protected BaseLazyDataModel<Specimen> createLazyDataModel() {
        return new SpecimenLazyDataModel(specimenService, sessionSettingsBean, langBean);
    }

    @Override
    protected void setErrorMessage(String msg) {
        this.errorMessage = msg;
    }




    public SpecimenListPanel(ApplicationContext context) {
        super("panel.title.allspecimenunit",
                "bi bi-box2",
                "siamois-panel specimen-panel list-panel",
                context);
        specimenService = context.getBean(SpecimenService.class);
    }

    @Override
    public String displayHeader() {
        return "/panel/header/specimenListPanelHeader.xhtml";
    }


    @Override
    protected String getBreadcrumbKey() {
        return "common.entity.specimen";
    }

    @Override
    protected String getBreadcrumbIcon() {
        return "bi bi-box2";
    }



    public List<Person> authorsAvailable() {

        return personService.findAllAuthorsOfActionUnitByInstitution(sessionSettingsBean.getSelectedInstitution());

    }

    @Override
    public void init() {
        super.init();
        lazyDataModel.setSelectedUnits(new ArrayList<>());
    }

    @Override
    public String display() {
        return "/panel/specimenListPanel.xhtml";
    }

    @Override
    public String ressourceUri() {
        return "/specimen";
    }


    public static class Builder {

        private final SpecimenListPanel specimenListPanel;

        public Builder(ObjectProvider<SpecimenListPanel> specimenListPanelProvider) {
            this.specimenListPanel = specimenListPanelProvider.getObject();
        }

        public SpecimenListPanel.Builder breadcrumb(PanelBreadcrumb breadcrumb) {
            specimenListPanel.setBreadcrumb(breadcrumb);

            return this;
        }

        public SpecimenListPanel build() {
            specimenListPanel.init();
            return specimenListPanel;
        }
    }





}
