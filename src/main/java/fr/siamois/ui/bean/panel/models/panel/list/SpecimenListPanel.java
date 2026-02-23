package fr.siamois.ui.bean.panel.models.panel.list;


import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.services.authorization.writeverifier.RecordingUnitWriteVerifier;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.spatialunit.SpatialUnitTreeService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.panel.FlowBean;
import fr.siamois.ui.bean.panel.models.PanelBreadcrumb;
import fr.siamois.ui.form.FormContextServices;
import fr.siamois.ui.lazydatamodel.BaseLazyDataModel;
import fr.siamois.ui.lazydatamodel.SpecimenLazyDataModel;
import fr.siamois.ui.table.SpecimenTableViewModel;
import fr.siamois.ui.table.definitions.SpecimenTableDefinitionFactory;
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
    private final transient FormService formService;
    private final transient SpatialUnitTreeService spatialUnitTreeService;
    private final transient FlowBean flowBean;
    private final transient GenericNewUnitDialogBean<Specimen> genericNewUnitDialogBean;
    private final transient RecordingUnitWriteVerifier recordingUnitWriteVerifier;
    private final transient NavBean navBean;
    private final transient FormContextServices formContextServices;

    // locals
    private String actionUnitListErrorMessage;

    public String getPanelIndex() {
        return "specimen-list";
    }

    @Override
    protected long countUnitsByInstitution() {
        return specimenService.countByInstitution(sessionSettingsBean.getSelectedInstitution());
    }

    @Override
    protected BaseLazyDataModel<Specimen> createLazyDataModel() {
        SpecimenLazyDataModel lazyModel = new SpecimenLazyDataModel(specimenService, sessionSettingsBean, langBean);

        // instanciate table model
        tableModel = new SpecimenTableViewModel(
                lazyModel,
                formService,
                sessionSettingsBean,
                spatialUnitTreeService,
                spatialUnitService,
                navBean,
                flowBean,
                genericNewUnitDialogBean,
                formContextServices
        );

        return lazyModel;
    }

    @Override
    protected void setErrorMessage(String msg) {
        this.errorMessage = msg;
    }




    public SpecimenListPanel(ApplicationContext context) {
        super("panel.title.allspecimenunit",
                "bi bi-bucket",
                "siamois-panel specimen-panel list-panel",
                context);
        specimenService = context.getBean(SpecimenService.class);
        this.formService = context.getBean(FormService.class);
        this.spatialUnitTreeService = context.getBean(SpatialUnitTreeService.class);
        this.flowBean = context.getBean(FlowBean.class);
        this.genericNewUnitDialogBean = context.getBean(GenericNewUnitDialogBean.class);
        this.recordingUnitWriteVerifier = context.getBean(RecordingUnitWriteVerifier.class);
        this.navBean = context.getBean(NavBean.class);
        this.formContextServices = context.getBean(FormContextServices.class);
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
        return "bi bi-bucket";
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

    @Override
    void configureTableColumns() {
        SpecimenTableDefinitionFactory.applyTo(tableModel);
        // no toolbar button in institution context
    }

    @Override
    public String getPanelTypeClass() {
        return "specimen";
    }





}
