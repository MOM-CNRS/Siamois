package fr.siamois.ui.bean.panel.models.panel.single;

import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.exceptions.actionunit.ActionUnitNotFoundException;
import fr.siamois.domain.models.history.RevisionWithInfo;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.ui.bean.RedirectBean;
import fr.siamois.ui.bean.panel.models.PanelBreadcrumb;
import fr.siamois.ui.bean.panel.models.panel.AbstractPanel;
import fr.siamois.ui.form.FormUiDto;
import fr.siamois.ui.mapper.FormMapper;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.model.menu.DefaultMenuItem;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.List;

@Slf4j
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Getter
@Setter
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class SpecimenPanel extends AbstractSingleEntityPanel<SpecimenDTO>  implements Serializable {

    public static final String BI_BI_BUCKET = "bi bi-bucket";
    private final transient FormMapper formMapper;

    protected final transient RecordingUnitService recordingUnitService;
    protected final transient PersonService personService;
    private final transient RedirectBean redirectBean;
    private final transient SpecimenService specimenService;

    @Override
    protected boolean documentExistsInUnitByHash(SpecimenDTO unit, String hash) {
        return documentService.existInSpecimenByHash(unit, hash);
    }

    @Override
    protected void addDocumentToUnit(Document doc, SpecimenDTO unit) {
        documentService.addToSpecimen(doc, unit);
    }

    // ---------- Locals

    protected SpecimenPanel(FormMapper formMapper, ApplicationContext context) {

        super("common.entity.specimen",
                BI_BI_BUCKET,
                "siamois-panel specimen-panel single-panel",
                context);
        this.formMapper = formMapper;
        this.recordingUnitService = context.getBean(RecordingUnitService.class);
        this.personService = context.getBean(PersonService.class);
        this.specimenService = context.getBean(SpecimenService.class);
        this.redirectBean = context.getBean(RedirectBean.class);
    }

    @Override
    public String ressourceUri() {
        return "/specimen/" + unitId;
    }

    @Override
    public String displayHeader() {
        return "/panel/header/specimenPanelHeader.xhtml";
    }


    public void refreshUnit() {

        // reinit

        errorMessage = null;
        unit = null;

        try {

            unit = specimenService.findById(unitId);

            this.titleCodeOrTitle = unit.getFullIdentifier();

            initForms(true);



        } catch (RuntimeException e) {
            this.errorMessage = "Failed to load specimen: " + e.getMessage();
        }


        //history = historyAuditService.findAllRevisionForEntity(SpecimenDTO.class, unitId);
        documents = documentService.findForSpecimen(unit);
    }


    @Override
    public void init() {
        try {

            // Details form
            detailsForm = formContextServices.getConversionService().convert(Specimen.DETAILS_FORM, FormUiDto.class);

            activeTabIndex = 0;

            if (unitId == null) {
                this.errorMessage = "The ID of the specimen unit must be defined";
                return;
            }

            refreshUnit();

            if (this.unit == null) {
                log.error("The Specimen page should not be accessed without ID or by direct page path");
                errorMessage = "The Specimen page should not be accessed without ID or by direct page path";
            }

        } catch (
                ActionUnitNotFoundException e) {
            log.error("Recording unit with id {} not found", unitId);
            redirectBean.redirectTo(HttpStatus.NOT_FOUND);
        } catch (
                RuntimeException e) {
            this.errorMessage = "Failed to load recording unit: " + e.getMessage();
            redirectBean.redirectTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }

    }

    @Override
    public List<PersonDTO> authorsAvailable() {
        return List.of();
    }

    @Override
    protected String getFocusPath(Long id) {
        return "/specimen/"+id;
    }

    @Override
    protected void addToOverview(Long id, AbstractPanel parentOrOverview, Integer activeTabIndex) {
        flowBean.addSpecimenToOverview(id,parentOrOverview, activeTabIndex);
    }

    @Override
    protected SpecimenDTO findNext() {
        return specimenService.findNextByActionUnit(unit.getRecordingUnit(), unit);
    }

    @Override
    protected SpecimenDTO findPrevious() {
        return specimenService.findPreviousByActionUnit(unit.getRecordingUnit(), unit);
    }

    @Override
    public void toggleValidate() {
        unit = specimenService.toggleValidated(unit.getId());
    }

    @Override
    SpecimenDTO findUnitById(Long id) {
        return specimenService.findById(id);
    }

    @Override
    String findLabel(SpecimenDTO unit) {
        return unit.getFullIdentifier();
    }

    @Override
    protected DefaultMenuItem createRootTypeItem() {

        String command ;
        Long id = unit.getRecordingUnit().getId();
        if(isRoot) {
            command = "#{navBean.redirectToBookmarked('/recording-unit/'"+id+")}";
        }
        else {

            command = "#{flowBean.addRecordingUnitToOverview(" + id + ", focusViewBean.mainPanel, 2)}";
        }

        return DefaultMenuItem.builder()
                .value(unit.getRecordingUnit().getFullIdentifier())
                .command(command)
                .update("@this")
                .id("allSpecimen")
                .onstart(PF_BUI_CONTENT_SHOW)
                .oncomplete(PF_BUI_CONTENT_HIDE)
                .process(THIS)
                .build();
    }

    @Override
    String getOpenPanelCommand(SpecimenDTO unit) {

        if(isRoot) {
            return "#{navBean.redirectToBookmarked('/specimen/".concat(unit.getId().toString()).concat("')}");
        }
        else {

            return "#{flowBean.addSpecimenToOverview(" + unit.getId() + ", focusViewBean.mainPanel, null)}";
        }
    }


    @Override
    public void initForms(boolean forceInit) {

        // Init system form answers
        initFormContext(forceInit);

    }

    @Override
    protected String getFormScopePropertyName() {
        return "";
    }

    @Override
    protected void setFormScopePropertyValue(ConceptDTO concept) {
        unit.setType(concept);
    }


    @Override
    public void visualise(RevisionWithInfo<SpecimenDTO> history) {
        // button is deactivated
    }

    @Override
    public String getAutocompleteClass() {
        return "recording-unit-autocomplete";
    }

    @Override
    public boolean save(Boolean validated) {
        return formContext.save();
    }

    public static class Builder {

        private final SpecimenPanel specimenPanel;

        public Builder(ObjectProvider<SpecimenPanel> specimenPanelProvider) {
            this.specimenPanel = specimenPanelProvider.getObject();
        }

        public SpecimenPanel.Builder id(Long id) {
            specimenPanel.setUnitId(id);
            return this;
        }

        public SpecimenPanel.Builder breadcrumb(PanelBreadcrumb breadcrumb) {
            specimenPanel.setBreadcrumb(breadcrumb);

            return this;
        }


        public SpecimenPanel build() {
            specimenPanel.init();
            return specimenPanel;
        }
    }

    @Override
    public String getTabView() {
        return "/panel/tabview/specimenTabView.xhtml";
    }

    @Override
    public String getPrefixPanelIndex() {
        return "specimen-"+ unitId;
    }

    @Override
    public String getPanelTypeClass() {
        return "specimen";
    }


}
