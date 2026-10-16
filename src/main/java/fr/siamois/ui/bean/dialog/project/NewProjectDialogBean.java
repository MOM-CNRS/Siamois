package fr.siamois.ui.bean.dialog.project;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import fr.siamois.domain.services.InstitutionService;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The "new project" dialog of the project settings list: the organisation (among those the user may
 * create projects in), a name, an identifier and the project type. Creation goes through
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

    /**
     * A project type to pick: the concept id and its label in the user's language. A class with getters
     * rather than a record: the EL of the JSF page reads bean properties, not record accessors.
     */
    @lombok.Value
    public static class TypeChoice implements Serializable {
        String id;
        String label;
    }

    private final transient InstitutionService institutionService;
    private final transient ProfilePermissionService profilePermissionService;
    private final transient FieldConfigurationService fieldConfigurationService;
    private final transient ProjectApiService projectApiService;
    private final transient ProjectListBean projectListBean;
    private final SessionSettingsBean sessionSettingsBean;
    private final LangBean langBean;

    /** The organisations the user may create projects in. */
    private List<InstitutionDTO> creatableInstitutions = List.of();
    private Long institutionId;
    private String name;
    private String identifier;
    private String typeId;
    private List<TypeChoice> types = List.of();

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
        identifier = null;
        typeId = null;
        InstitutionDTO active = sessionSettingsBean.getSelectedInstitution();
        institutionId = creatableInstitutions.stream()
                .map(InstitutionDTO::getId)
                .filter(id -> active != null && id.equals(active.getId()))
                .findFirst()
                .orElse(creatableInstitutions.get(0).getId());
        loadTypes();
        PrimeFaces.current().ajax().update("newProjectDialog");
        PrimeFaces.current().executeScript("PF('newProjectDialog').show();");
    }

    /** The project types of the chosen organisation's vocabulary. */
    public void loadTypes() {
        typeId = null;
        types = List.of();
        InstitutionDTO institution = creatableInstitutions.stream()
                .filter(i -> i.getId().equals(institutionId))
                .findFirst()
                .orElse(null);
        if (institution == null) {
            return;
        }
        try {
            UserInfo info = new UserInfo(institution, sessionSettingsBean.getAuthenticatedUser(), langBean.getLanguageCode());
            Map<String, String> byId = new LinkedHashMap<>();
            for (ConceptAutocompleteDTO dto : fieldConfigurationService.fetchAutocomplete(info, ActionUnit.TYPE_FIELD_CODE, "")) {
                byId.putIfAbsent(String.valueOf(dto.getConceptLabelToDisplay().getConcept().getId()),
                        dto.getConceptLabelToDisplay().getLabel());
            }
            types = byId.entrySet().stream().map(e -> new TypeChoice(e.getKey(), e.getValue())).toList();
        } catch (NoConfigForFieldException e) {
            log.warn("No vocabulary configured for the project type field in institution {}", institutionId);
            MessageUtils.displayNoThesaurusConfiguredMessage(langBean);
        }
    }

    /** Creates the project; the dialog closes only when it worked, so a refusal leaves the form as it was. */
    public void create() {
        boolean created = false;
        try {
            ProjectCreateRequest request = new ProjectCreateRequest();
            request.setOrganizationId(String.valueOf(institutionId));
            request.setName(name);
            request.setIdentifier(identifier);
            request.setTypeId(typeId);
            projectApiService.createProject(projectApiService.requireCaller(), request, langBean.getLanguageCode());
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
}
