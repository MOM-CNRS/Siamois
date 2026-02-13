package fr.siamois.ui.bean.panel.models.panel.single;

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
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.RedirectBean;
import fr.siamois.ui.bean.panel.models.PanelBreadcrumb;
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
public class SpecimenPanel extends AbstractSingleEntityPanel<Specimen>  implements Serializable {

    public static final String BI_BI_BUCKET = "bi bi-bucket";
    // Deps
    protected final transient LangBean langBean;


    protected final transient RecordingUnitService recordingUnitService;
    protected final transient PersonService personService;
    private final transient RedirectBean redirectBean;
    private final transient SpecimenService specimenService;

    @Override
    protected boolean documentExistsInUnitByHash(Specimen unit, String hash) {
        return documentService.existInSpecimenByHash(unit, hash);
    }

    @Override
    protected void addDocumentToUnit(Document doc, Specimen unit) {
        documentService.addToSpecimen(doc, unit);
    }

    // ---------- Locals

    protected SpecimenPanel(ApplicationContext context) {

        super("common.entity.specimen",
                BI_BI_BUCKET,
                "siamois-panel specimen-panel single-panel",
                context);
        this.langBean = context.getBean(LangBean.class);
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
            Hibernate.initialize(unit.getDocuments());
            Hibernate.initialize(unit.getAuthors());
            Hibernate.initialize(unit.getCollectors());
            backupClone = new Specimen(unit);
            this.titleCodeOrTitle = unit.getFullIdentifier();

            initForms(true);



        } catch (RuntimeException e) {
            this.errorMessage = "Failed to load specimen: " + e.getMessage();
        }


        history = historyAuditService.findAllRevisionForEntity(Specimen.class, idunit);
        documents = documentService.findForSpecimen(unit);
    }


    @Override
    public void init() {
        try {

            // Details form
            detailsForm = Specimen.DETAILS_FORM;


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
    public List<Person> authorsAvailable() {
        return List.of();
    }

    @Override
    Specimen findUnitById(Long id) {
        return specimenService.findById(id);
    }

    @Override
    String findLabel(Specimen unit) {
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
    String getOpenPanelCommand(Specimen unit) {
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
    protected void setFormScopePropertyValue(Concept concept) {
        unit.setType(concept);
    }

    @Override
    public void cancelChanges() {

        unit.setType(backupClone.getType());
        unit.setRecordingUnit(backupClone.getRecordingUnit());
        unit.setCategory(backupClone.getCategory());
        unit.setCreatedByInstitution(backupClone.getCreatedByInstitution());
        unit.setCreatedBy(backupClone.getCreatedBy());
        unit.setAuthors(backupClone.getAuthors());
        unit.setCollectors(backupClone.getCollectors());
        unit.setCollectionDate(backupClone.getCollectionDate());
        formContext.setHasUnsavedModifications(false);
        initForms(true);
    }

    @Override
    public void visualise(RevisionWithInfo<Specimen> history) {
        // button is deactivated
    }

    @Override
    public String getAutocompleteClass() {
        return "recording-unit-autocomplete";
    }

    @Override
    public boolean save(Boolean validated) {

        formContext.flushBackToEntity();
        unit.setValidated(validated);
        if (Boolean.TRUE.equals(validated)) {
            unit.setValidatedBy(sessionSettingsBean.getAuthenticatedUser());
            unit.setValidatedAt(OffsetDateTime.now());
        } else {
            unit.setValidatedBy(null);
            unit.setValidatedAt(null);
        }

        try {
            specimenService.save(unit);
        } catch (FailedRecordingUnitSaveException e) {
            MessageUtils.displayErrorMessage(langBean, "common.entity.specimen.updateFailed", unit.getFullIdentifier());
            return false;
        }

        refreshUnit();
        MessageUtils.displayInfoMessage(langBean, "common.entity.specimen.updated", unit.getFullIdentifier());
        return true;
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


}
