package fr.siamois.ui.bean.panel.models.panel.list;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.LocalizedConceptData;
import fr.siamois.domain.services.BookmarkService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.FieldService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.panel.models.PanelBreadcrumb;
import fr.siamois.ui.lazydatamodel.BaseLazyDataModel;
import fr.siamois.ui.lazydatamodel.SpatialUnitLazyDataModel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.List;

@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Data
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class SpatialUnitListPanel extends AbstractListPanel<SpatialUnit>  implements Serializable {

    // locals
    private String spatialUnitListErrorMessage;

    public SpatialUnitListPanel(SpatialUnitService spatialUnitService,
                                PersonService personService,
                                ConceptService conceptService,
                                SessionSettingsBean sessionSettingsBean,
                                LangBean langBean,
                                LabelService labelService,
                                ActionUnitService actionUnitService, BookmarkService bookmarkService, FieldService fieldService, FieldConfigurationService fieldConfigurationService) {
        super("panel.title.allspatialunit",
                "bi bi-geo-alt",
                "siamois-panel spatial-unit-panel list-panel",
                spatialUnitService,
                personService,
                conceptService,
                sessionSettingsBean,
                langBean,
                labelService,
                actionUnitService,
                bookmarkService,
                fieldService,
                fieldConfigurationService,
                SpatialUnit.class);
    }

    @Override
    protected String getBreadcrumbKey() {
        return "common.entity.spatialUnits";
    }

    @Override
    protected String getBreadcrumbIcon() {
        return "bi bi-geo-alt";
    }


    @Override
    public String displayHeader() {
        return "/panel/header/spatialUnitListPanelHeader.xhtml";
    }


    @Override
    protected long countUnitsByInstitution() {
        return spatialUnitService.countByInstitution(sessionSettingsBean.getSelectedInstitution());
    }

    @Override
    protected BaseLazyDataModel<SpatialUnit> createLazyDataModel() {
        return new SpatialUnitLazyDataModel(spatialUnitService, sessionSettingsBean, langBean);
    }

    @Override
    protected void setErrorMessage(String msg) {
        this.spatialUnitListErrorMessage = msg;
    }

    public List<LocalizedConceptData> categoriesAvailable() {
        List<Concept> cList = conceptService.findAllBySpatialUnitOfInstitution(sessionSettingsBean.getSelectedInstitution());

        return cList.stream()
                .map(concept -> labelService.findLabelOf(
                        concept, langBean.getLanguageCode()
                ))
                .toList();

    }

    public List<Person> authorsAvailable() {
        return personService.findAllAuthorsOfSpatialUnitByInstitution(sessionSettingsBean.getSelectedInstitution());
    }



    @Override
    public String display() {
        return "/panel/spatialUnitListPanel.xhtml";
    }

    @Override
    public String ressourceUri() {
        return "/spatialunit";
    }

    public static class SpatialUnitListPanelBuilder {

        private final SpatialUnitListPanel spatialUnitListPanel;

        public SpatialUnitListPanelBuilder(ObjectProvider<SpatialUnitListPanel> spatialUnitListPanelProvider) {
            this.spatialUnitListPanel = spatialUnitListPanelProvider.getObject();
        }

        public SpatialUnitListPanel.SpatialUnitListPanelBuilder breadcrumb(PanelBreadcrumb breadcrumb) {
            spatialUnitListPanel.setBreadcrumb(breadcrumb);

            return this;
        }

        public SpatialUnitListPanel build() {
            spatialUnitListPanel.init();
            return spatialUnitListPanel;
        }
    }





}
