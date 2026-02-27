package fr.siamois.ui.bean.panel.models.panel.single;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.exceptions.actionunit.ActionUnitNotFoundException;
import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.history.RevisionWithInfo;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.RedirectBean;
import fr.siamois.ui.bean.panel.models.PanelBreadcrumb;
import fr.siamois.ui.form.FormUiDto;
import fr.siamois.ui.mapper.FormMapper;
import fr.siamois.utils.MessageUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.primefaces.model.menu.DefaultMenuItem;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.time.OffsetDateTime;
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
        return "/specimen/" + idunit;
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

            unit = specimenService.findById(idunit);
            backupClone = new SpecimenDTO(unit);
            this.titleCodeOrTitle = unit.getFullIdentifier();

            initForms(true);



        } catch (RuntimeException e) {
            this.errorMessage = "Failed to load specimen: " + e.getMessage();
        }


        history = historyAuditService.findAllRevisionForEntity(SpecimenDTO.class, idunit);
        documents = documentService.findForSpecimen(unit);
    }


    @Override
    public void init() {
        try {

            // Details form
            detailsForm = formContextServices.getConversionService().convert(Specimen.DETAILS_FORM, FormUiDto.class);

            activeTabIndex = 0;

            if (idunit == null) {
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
            log.error("Recording unit with id {} not found", idunit);
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
    SpecimenDTO findUnitById(Long id) {
        return specimenService.findById(id);
    }

    @Override
    String findLabel(SpecimenDTO unit) {
        return unit.getFullIdentifier();
    }

    @Override
    protected DefaultMenuItem createRootTypeItem() {
        return DefaultMenuItem.builder()
                .value(langBean.msg("panel.title.allspecimenunit"))
                .id("allSpecimen")
                .command("#{flowBean.addSpecimenListPanel()}")
                .update("flow")
                .onstart(PF_BUI_CONTENT_SHOW)
                .oncomplete(PF_BUI_CONTENT_HIDE)
                .process(THIS)
                .build();
    }

    @Override
    String getOpenPanelCommand(SpecimenDTO unit) {
        return "#{flowBean.addSpecimenPanel(".concat(unit.getId().toString()).concat(")}");
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
    public void cancelChanges() {

        unit.setType(backupClone.getType());
        unit.setCreatedByInstitution(backupClone.getCreatedByInstitution());
        unit.setCreatedBy(backupClone.getCreatedBy());
        formContext.setHasUnsavedModifications(false);
        initForms(true);
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
            specimenPanel.setIdunit(id);
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
    public String getPanelIndex() {
        return "specimen-"+idunit;
    }

    @Override
    public String getPanelTypeClass() {
        return "specimen";
    }


}
