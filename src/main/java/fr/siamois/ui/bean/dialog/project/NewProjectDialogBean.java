package fr.siamois.ui.bean.dialog.project;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.actionunit.template.ProjectTemplate;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.api.openapi.v1.request.project.ProjectCreateRequest;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.settings.project.ProjectListBean;
import fr.siamois.utils.MessageUtils;
import jakarta.faces.application.FacesMessage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.PrimeFaces;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.Serializable;
import java.util.*;

/**
 * The "new project" dialog of the project settings list, aligned on the React create form: the organisation
 * (among those the user may create projects in), a name, an identifier (proposed automatically, editable),
 * the starting point, the project type, the main location and the spatial context. Creation goes through
 * {@link ProjectApiService#createProject}, the same use case the REST API and the React app use, so the
 * rights check, the uniqueness check and the default form answers are the same everywhere.
 */
@Slf4j
@Getter
@Setter
@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class NewProjectDialogBean implements Serializable {

    /** A starting point to pick (see {@link ProjectTemplate}): its id and its label in the user's language. */
    @lombok.Value
    public static class TemplateChoice implements Serializable {
        String id;
        String label;
    }

    private final transient InstitutionService institutionService;
    private final transient ProfilePermissionService profilePermissionService;
    private final transient FieldConfigurationService fieldConfigurationService;
    private final transient ProjectApiService projectApiService;
    private final transient ProjectCreationPlaces projectCreationPlaces;
    private final transient ProjectListBean projectListBean;
    private final SessionSettingsBean sessionSettingsBean;
    private final LangBean langBean;

    /** The organisations the user may create projects in. */
    private List<InstitutionDTO> creatableInstitutions = List.of();
    private Long institutionId;
    private String name;
    private String identifier;
    /** The identifier proposed for the organisation: sent only when the user typed another one. */
    private String proposedIdentifier;
    private ConceptAutocompleteDTO typeConcept;
    /** The thesaurus page of the project type field, linked from its picker. */
    private String typeConceptUrl;
    private String templateId = ProjectTemplate.DEFAULT_ID;
    private List<TemplateChoice> templates = List.of();
    private ProjectPlaceFields placeFields = ProjectPlaceFields.NONE;
    private PlaceChoice mainLocation;
    private List<PlaceChoice> spatialContext = new ArrayList<>();
    /** The places the pickers offered: the converter of the place pickers. */
    private final OfferedPlaces offeredPlaces = new OfferedPlaces();

    /** Refreshes what the user may create — run when the project list page is displayed. */
    public void init() {
        PersonDTO person = sessionSettingsBean.getAuthenticatedUser();
        if (person == null) {
            creatableInstitutions = List.of();
            return;
        }
        List<InstitutionDTO> mine = List.copyOf(institutionService.findInstitutionsOfPerson(person));
        Set<Long> allowed = profilePermissionService.institutionIdsWithActionUnitCreatePermission(person, mine);
        creatableInstitutions = mine.stream()
                .filter(institution -> allowed.contains(institution.getId()))
                .sorted(Comparator.comparing(InstitutionDTO::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Whether the "new project" button is offered: the user may create a project somewhere. */
    public boolean isCanCreate() {
        return !creatableInstitutions.isEmpty();
    }

    /** Opens the dialog on a blank form, in the active organisation when the user may create projects there. */
    public void open() {
        init();
        if (creatableInstitutions.isEmpty()) {
            MessageUtils.displayWarnMessage(langBean, "common.error.forbidden");
            return;
        }
        name = null;
        InstitutionDTO active = sessionSettingsBean.getSelectedInstitution();
        institutionId = creatableInstitutions.stream()
                .map(InstitutionDTO::getId)
                .filter(id -> active != null && id.equals(active.getId()))
                .findFirst()
                .orElse(creatableInstitutions.getFirst().getId());
        onOrganisationChange();
        PrimeFaces.current().ajax().update("newProjectDialog");
        PrimeFaces.current().executeScript("PF('newProjectDialog').show();");
    }

    /** The organisation changed: its types, its templates, its places and its next identifier differ. */
    public void onOrganisationChange() {
        loadTypes();
        loadTemplates();
        loadPlaceFields();
        proposeIdentifier();
    }

    /** Proposes the identifier the next project of the chosen organisation would get; the user may change it. */
    public void proposeIdentifier() {
        identifier = null;
        proposedIdentifier = null;
        if (institutionId == null) {
            return;
        }
        try {
            identifier = projectApiService.previewNextProjectIdentifier(
                    projectApiService.requireCaller(), String.valueOf(institutionId), langBean.getLanguageCode()).orElse(null);
            proposedIdentifier = identifier;
        } catch (RuntimeException e) {
            log.warn("Could not preview the next project identifier of institution {}", institutionId, e);
        }
    }

    /** The starting points of the chosen organisation: the default project, an empty one, or a copy of one of its projects. */
    public void loadTemplates() {
        templateId = ProjectTemplate.DEFAULT_ID;
        templates = List.of();
        if (institutionId == null) {
            return;
        }
        try {
            templates = projectApiService.listProjectTemplates(projectApiService.requireCaller(), String.valueOf(institutionId)).stream()
                    .map(t -> new TemplateChoice(t.id(), switch (t.kind()) {
                        case DEFAULT -> langBean.msg("projectList.create.template.default");
                        case EMPTY -> langBean.msg("projectList.create.template.empty");
                        case PROJECT, KITION -> t.label();
                    }))
                    .toList();
        } catch (RuntimeException e) {
            log.warn("Could not list the project templates of institution {}", institutionId, e);
        }
    }

    /** Resets the project type and finds the thesaurus page of the chosen organisation's type field. */
    public void loadTypes() {
        typeConcept = null;
        typeConceptUrl = null;
        Optional<UserInfo> info = chosenInstitutionUserInfo();
        if (info.isEmpty()) {
            return;
        }
        try {
            fieldConfigurationService.findConfigurationForFieldCode(info.get(), ActionUnit.TYPE_FIELD_CODE);
            typeConceptUrl = fieldConfigurationService.getUrlForFieldCode(info.get(), ActionUnit.TYPE_FIELD_CODE);
        } catch (NoConfigForFieldException e) {
            log.warn("No vocabulary configured for the project type field in institution {}", institutionId);
            MessageUtils.displayNoThesaurusConfiguredMessage(langBean);
        }
    }

    /**
     * The project types of the chosen organisation's vocabulary matching what the user typed, with their
     * alternative labels, definition and parents, like the concept pickers of the React create forms.
     *
     * @param query the text typed in the type picker
     * @return the matching types, or none when the organisation has no vocabulary for the field
     */
    public List<ConceptAutocompleteDTO> completeType(String query) {
        Optional<UserInfo> info = chosenInstitutionUserInfo();
        if (info.isEmpty()) {
            return List.of();
        }
        try {
            return fieldConfigurationService.fetchAutocomplete(info.get(), ActionUnit.TYPE_FIELD_CODE, query);
        } catch (NoConfigForFieldException e) {
            log.warn("No vocabulary configured for the project type field in institution {}", institutionId);
            return List.of();
        }
    }

    /** Finds the place fields of the chosen organisation's project form, and clears the places picked. */
    public void loadPlaceFields() {
        placeFields = ProjectPlaceFields.NONE;
        mainLocation = null;
        spatialContext = new ArrayList<>();
        offeredPlaces.forget();
        if (institutionId == null) {
            return;
        }
        try {
            placeFields = projectCreationPlaces.fieldsOf(institutionId, sessionSettingsBean.getAuthenticatedUser(),
                    langBean.getLanguageCode());
        } catch (RuntimeException e) {
            log.warn("Could not find the place fields of the project form of institution {}", institutionId, e);
        }
    }

    /**
     * What the main location picker suggests.
     *
     * @param query the text typed
     * @return the places to offer
     */
    public List<PlaceChoice> completeMainLocation(String query) {
        return suggestPlaces(placeFields.getMainLocation(), query);
    }

    /**
     * What the spatial context picker suggests, narrowed by the main location when its sources depend on it.
     *
     * @param query the text typed
     * @return the places to offer
     */
    public List<PlaceChoice> completeSpatialContext(String query) {
        return suggestPlaces(placeFields.getSpatialContext(), query);
    }

    /**
     * A place was picked: a picked suggestion becomes a place of the organisation, so that it can narrow
     * the other picker's suggestions and be attached to the project.
     */
    public void onPlacePicked() {
        try {
            mainLocation = mainLocation == null ? null : placeOf(mainLocation);
            spatialContext = new ArrayList<>(spatialContext.stream().map(this::placeOf).toList());
        } catch (ResponseStatusException e) {
            MessageUtils.displayMessage(FacesMessage.SEVERITY_ERROR,
                    langBean.msg("projectList.create"), e.getReason() != null ? e.getReason() : e.getMessage());
        } catch (RuntimeException e) {
            log.error("Could not create the place of a suggestion", e);
            MessageUtils.displayInternalError(langBean);
        }
    }

    /** Creates the project; the dialog closes only when it worked, so a refusal leaves the form as it was. */
    public void create() {
        boolean created = false;
        try {
            projectApiService.createProject(projectApiService.requireCaller(), createRequest(), langBean.getLanguageCode());
            created = true;
            MessageUtils.displayInfoMessage(langBean, "projectList.create.success", name);
            projectListBean.init();
        } catch (ResponseStatusException e) {
            MessageUtils.displayMessage(FacesMessage.SEVERITY_ERROR,
                    langBean.msg("projectList.create"), e.getReason() != null ? e.getReason() : e.getMessage());
        } catch (RuntimeException e) {
            log.error("Project creation failed", e);
            MessageUtils.displayInternalError(langBean);
        }
        PrimeFaces.current().ajax().addCallbackParam("created", created);
    }

    private ProjectCreateRequest createRequest() {
        ProjectCreateRequest request = new ProjectCreateRequest();
        request.setOrganizationId(String.valueOf(institutionId));
        request.setName(name);
        request.setIdentifier(typedIdentifier());
        request.setTypeId(typeConcept == null ? null : String.valueOf(typeConcept.getConceptLabelToDisplay().getConcept().getId()));
        request.setTemplateId(templateId);
        request.setMainLocationId(placeIdOf(mainLocation));
        request.setSpatialContextSpatialUnitIds(spatialContextIds());
        return request;
    }

    /** The identifier typed by the user; null when it is the proposed one, so the server allocates it. */
    private String typedIdentifier() {
        if (identifier == null || identifier.isBlank() || identifier.trim().equals(proposedIdentifier)) {
            return null;
        }
        return identifier.trim();
    }

    private List<String> spatialContextIds() {
        List<String> ids = spatialContext.stream().map(this::placeIdOf).filter(Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? null : ids;
    }

    private String placeIdOf(PlaceChoice choice) {
        return choice == null || choice.isExternal() ? null : String.valueOf(choice.getId());
    }

    private PlaceChoice placeOf(PlaceChoice choice) {
        return offeredPlaces.remember(projectCreationPlaces.placeOf(institutionId, choice, langBean.getLanguageCode()));
    }

    private List<PlaceChoice> suggestPlaces(PlaceField field, String query) {
        if (field == null || institutionId == null) {
            return List.of();
        }
        try {
            return offeredPlaces.remember(projectCreationPlaces.suggest(institutionId, field, query,
                    placeFields.dependenciesOn(mainLocation), langBean.getLanguageCode()));
        } catch (RuntimeException e) {
            log.warn("Could not suggest places for field {} of institution {}", field.getId(), institutionId, e);
            return List.of();
        }
    }

    private Optional<UserInfo> chosenInstitutionUserInfo() {
        return creatableInstitutions.stream()
                .filter(institution -> institution.getId().equals(institutionId))
                .findFirst()
                .map(institution -> new UserInfo(institution, sessionSettingsBean.getAuthenticatedUser(), langBean.getLanguageCode()));
    }
}
