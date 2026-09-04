package fr.siamois.ui.bean.panel.models.panel.single;

import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.form.customform.CustomFormComposer;
import fr.siamois.domain.models.history.RevisionWithInfo;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.settings.tableconfig.TypeFieldFormConfig;
import fr.siamois.domain.models.settings.tableconfig.TypeFieldsConfig;
import fr.siamois.domain.services.PhaseService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.ui.bean.RedirectBean;
import fr.siamois.ui.bean.dialog.newunit.UnitKind;
import fr.siamois.ui.bean.panel.models.PanelBreadcrumb;
import fr.siamois.ui.bean.panel.models.panel.AbstractPanel;
import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.utils.MessageUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.model.menu.DefaultMenuItem;
import org.primefaces.model.menu.DefaultMenuModel;
import org.primefaces.model.menu.MenuModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Getter
@Setter
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class PhasePanel extends AbstractSingleEntityPanel<PhaseDTO> implements Serializable {

    private final transient PhaseService phaseService;
    private final transient RedirectBean redirectBean;
    private final transient TableFieldConfigService tableFieldConfigService;
    private final transient LabelService labelService;
    private final transient ProfilePermissionService profilePermissionService;

    @Override
    protected boolean documentExistsInUnitByHash(PhaseDTO unit, String hash) {
        return false;
    }

    @Override
    protected void addDocumentToUnit(Document doc, PhaseDTO unit) {
        // not yet supported
    }

    protected PhasePanel(ApplicationContext context) {
        super("common.entity.phase",
                "bi bi-layers",
                "siamois-panel phase-panel single-panel",
                context);
        this.phaseService = context.getBean(PhaseService.class);
        this.redirectBean = context.getBean(RedirectBean.class);
        this.tableFieldConfigService = context.getBean(TableFieldConfigService.class);
        this.labelService = context.getBean(LabelService.class);
        this.profilePermissionService = context.getBean(ProfilePermissionService.class);
    }

    public String entityRessourceUri() {
        return "/phase/" + unitId;
    }

    @Override
    public boolean canUserEditUnit() {
        return unit != null && profilePermissionService.hasPhaseWritePermission(sessionSettingsBean.getUserInfo(), unit);
    }

    @Override
    public String displayHeader() {
        return "/panel/header/phasePanelHeader.xhtml";
    }

    @Override
    public UnitKind getCreationUnitKind() {
        return UnitKind.PHASE;
    }

    @Override
    public void refreshUnit() {
        errorMessage = null;
        unit = null;

        try {
            unit = phaseService.findById(unitId);
            this.titleCodeOrTitle = unit.getIdentifier();
            initForms(true);
        } catch (RuntimeException e) {
            this.errorMessage = "Failed to load phase: " + e.getMessage();
        }

        documents = List.of();
    }

    @Override
    protected String currentIdentifierValue() {
        return unit.getIdentifier();
    }

    @Override
    protected boolean persistIdentifierEdit(String trimmed) {
        if (trimmed.isEmpty()) {
            MessageUtils.displayWarnMessage(langBean, "phase.error.identifier.blank");
            return false;
        }

        String previous = unit.getIdentifier();
        unit.setIdentifier(trimmed);

        if (phaseService.identifierAlreadyExistInAction(unit)) {
            unit.setIdentifier(previous);
            MessageUtils.displayWarnMessage(langBean, "phase.error.identifier.alreadyExists");
            return false;
        }

        try {
            phaseService.save(unit);
            this.titleCodeOrTitle = unit.getIdentifier();
            return true;
        } catch (RuntimeException e) {
            unit.setIdentifier(previous);
            MessageUtils.displayErrorMessage(langBean, "common.entity.phase.updateFailed", unit.getIdentifier());
            return false;
        }
    }

    @Override
    public void init() {
        try {
            activeTabIndex = 0;

            if (unitId == null) {
                this.errorMessage = "The ID of the phase must be defined";
                return;
            }

            refreshUnit();

            if (this.unit == null) {
                log.error("Phase page accessed without valid ID");
                errorMessage = "Phase page accessed without valid ID";
            }
        } catch (RuntimeException e) {
            this.errorMessage = "Failed to load phase: " + e.getMessage();
            redirectBean.redirectTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public List<PersonDTO> authorsAvailable() {
        return List.of();
    }

    @Override
    protected String getFocusPath(Long id) {
        return "/phase/" + id;
    }

    @Override
    protected void addToOverview(Long id, AbstractPanel parentOrOverview, Integer activeTabIndex) {
        flowBean.addPhaseToOverview(id, parentOrOverview, activeTabIndex, false);
    }

    @Override
    protected PhaseDTO findNext() {
        return phaseService.findNextByActionUnit(unit.getActionUnit(), unit);
    }

    @Override
    protected PhaseDTO findPrevious() {
        return phaseService.findPreviousByActionUnit(unit.getActionUnit(), unit);
    }

    @Override
    protected void doToggleValidate() {
        // not yet supported
    }

    @Override
    PhaseDTO findUnitById(Long id) {
        return phaseService.findById(id);
    }

    @Override
    public List<MenuModel> getAllParentBreadcrumbModels() {
        MenuModel breadcrumbModel = new DefaultMenuModel();
        breadcrumbModel.getElements().add(createHomeItem());
        if (unit != null && unit.getActionUnit() != null) {
            ActionUnitDTO actionUnit = actionUnitService.findById(unit.getActionUnit().getId());
            breadcrumbModel.getElements().add(createUnitItem(actionUnit));
        }
        breadcrumbModel.getElements().add(createRootTypeItem());
        return List.of(breadcrumbModel);
    }

    @Override
    protected DefaultMenuItem createRootTypeItem() {
        String command = isRoot
                ? "#{navBean.redirectToBookmarked('/phase')}"
                : "#{flowBean.addPhaseListPanel()}";

        return DefaultMenuItem.builder()
                .value("Phases")
                .command(command)
                .update("@this")
                .id("rootPhases")
                .icon("bi bi-layers")
                .onstart(PF_BUI_CONTENT_SHOW)
                .oncomplete(PF_BUI_CONTENT_HIDE)
                .process(THIS)
                .build();
    }

    @Override
    public void initForms(boolean forceInit) {
        String typeName = resolveTypeName();
        Long projectId = unit.getActionUnit() != null ? unit.getActionUnit().getId() : null;

        FormUiDto form = Phase.DETAILS_FORM;
        if (projectId != null) {
            FormUiDto base = CustomFormComposer.withoutFields(form, inactiveSystemFieldBindings(projectId, typeName));
            form = CustomFormComposer.withAdditionalFields(base, "Champs additionnels", additionalFields(projectId, typeName));
        }
        detailsForm = CustomFormComposer.deepCopy(form);

        initFormContext(forceInit);
    }

    /**
     * The label of the Phase's own type concept, i.e. the type name field configurations
     * are keyed on ({@link TableFieldConfigService#DEFAULT_TYPE} when the phase has none).
     */
    private String resolveTypeName() {
        return unit.getType() != null
                ? labelService.findLabelOf(unit.getType(), langBean.getLanguageCode()).getLabel()
                : TableFieldConfigService.DEFAULT_TYPE;
    }

    private Set<String> inactiveSystemFieldBindings(Long projectId, String typeName) {
        TypeFieldsConfig fieldsConfig = tableFieldConfigService.getFieldsConfig(projectId, ConfigurableTable.PHASE, typeName);
        return fieldsConfig.getFields().stream()
                .filter(f -> !f.isActive())
                .map(TypeFieldFormConfig::getValueBinding)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private List<CustomColUiDto> additionalFields(Long projectId, String typeName) {
        return tableFieldConfigService.getActiveAdditionalFields(projectId, ConfigurableTable.PHASE, typeName).stream()
                .map(field -> new CustomColUiDto.Builder().field(field).build())
                .toList();
    }

    @Override
    protected String getFormScopePropertyName() {
        return "type";
    }

    @Override
    protected void setFormScopePropertyValue(ConceptDTO concept) {
        unit.setType(concept);
    }

    @Override
    public void visualise(RevisionWithInfo<PhaseDTO> history) {
        // deactivated
    }

    @Override
    public String getAutocompleteClass() {
        return "phase-autocomplete";
    }

    @Override
    public boolean save(Boolean validated) {
        return formContext.save();
    }

    public static class Builder {

        private final PhasePanel phasePanel;

        public Builder(ObjectProvider<PhasePanel> provider) {
            this.phasePanel = provider.getObject();
        }

        public Builder id(Long id) {
            phasePanel.setUnitId(id);
            return this;
        }

        public Builder breadcrumb(PanelBreadcrumb breadcrumb) {
            phasePanel.setBreadcrumb(breadcrumb);
            return this;
        }

        public PhasePanel build() {
            phasePanel.init();
            return phasePanel;
        }
    }

    @Override
    public String getTabView() {
        return "/panel/tabview/phaseTabView.xhtml";
    }

    @Override
    public String getPrefixPanelIndex() {
        return "phase-" + unitId;
    }

    @Override
    public String svgIcon() {
        return "/resources/img/svg/layers.svg";
    }

    @Override
    public String getPanelTypeClass() {
        return "phase";
    }
}
