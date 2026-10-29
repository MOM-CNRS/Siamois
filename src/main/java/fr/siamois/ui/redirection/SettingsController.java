package fr.siamois.ui.redirection;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.settings.InstitutionListSettingsBean;
import fr.siamois.ui.bean.settings.administration.ApplicationMembersListBean;
import fr.siamois.ui.bean.settings.exporttemplate.ExportTemplatesSettingsBean;
import fr.siamois.ui.bean.settings.project.ProjectDetailsBean;
import fr.siamois.ui.bean.settings.project.ProjectListBean;
import fr.siamois.ui.bean.settings.project.ProjectTableFieldSettingsBean;
import jakarta.ws.rs.ForbiddenException;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@Scope(value = "session")
public class SettingsController {

    private final NavBean navBean;
    private final InstitutionListSettingsBean institutionListSettingsBean;
    private final ApplicationMembersListBean applicationMembersListBean;
    private final SessionSettingsBean sessionSettingsBean;
    private final ProfilePermissionService profilePermissionService;
    private final ProjectListBean projectListBean;
    private final PersonService personService;
    private final PersonMapper personMapper;
    private final ExportTemplatesSettingsBean exportTemplatesSettingsBean;
    private final ProjectDetailsBean projectDetailsBean;
    private final ProjectTableFieldSettingsBean projectTableFieldSettingsBean;
    private final ActionUnitService actionUnitService;



    public SettingsController(NavBean navBean, InstitutionListSettingsBean institutionListSettingsBean,
                               ApplicationMembersListBean applicationMembersListBean,
                               SessionSettingsBean sessionSettingsBean,
                               ProfilePermissionService profilePermissionService,
                               ProjectListBean projectListBean,
                               PersonService personService,
                               PersonMapper personMapper,
                               ExportTemplatesSettingsBean exportTemplatesSettingsBean,
                               ProjectDetailsBean projectDetailsBean,
                               ProjectTableFieldSettingsBean projectTableFieldSettingsBean,
                               ActionUnitService actionUnitService) {
        this.projectDetailsBean = projectDetailsBean;
        this.projectTableFieldSettingsBean = projectTableFieldSettingsBean;
        this.actionUnitService = actionUnitService;
        this.exportTemplatesSettingsBean = exportTemplatesSettingsBean;
        this.navBean = navBean;
        this.institutionListSettingsBean = institutionListSettingsBean;
        this.applicationMembersListBean = applicationMembersListBean;
        this.sessionSettingsBean = sessionSettingsBean;
        this.profilePermissionService = profilePermissionService;
        this.projectListBean = projectListBean;
        this.personService = personService;
        this.personMapper = personMapper;
    }

    /** Resolves the {@code memberId} query param (if any) to a {@link PersonDTO}, or {@code null}. */
    private PersonDTO resolveMember(Long memberId) {
        if (memberId == null) {
            return null;
        }
        Person person = personService.findById(memberId);
        return person != null ? personMapper.convert(person) : null;
    }

    @GetMapping("/settings")
    public String goToSettings() {
        navBean.setApplicationMode(NavBean.ApplicationMode.SETTINGS);
        return "forward:/pages/settings/profileSettings.xhtml";
    }

    @GetMapping("/settings/profile")
    public String goToSettingsByProfile() {
        return goToSettings();
    }

    @GetMapping("/settings/profile/thesaurus")
    public String goToThesaurusProfile() {
        navBean.setApplicationMode(NavBean.ApplicationMode.SETTINGS);
        return "forward:/pages/settings/thesaurusSettings.xhtml";
    }

    @GetMapping("/settings/services")
    public String goToServicesSettings() {
        if (!profilePermissionService.canViewInstitutionData(sessionSettingsBean.getUserInfo().getUser(), sessionSettingsBean.getSelectedInstitution())) {
            throw new ForbiddenException();
        }
        navBean.setApplicationMode(NavBean.ApplicationMode.SETTINGS);
        return "forward:/pages/settings/services.xhtml";
    }

    @GetMapping("/settings/export-templates")
    public String goToExportTemplatesSettings() {
        if (!profilePermissionService.canViewInstitutionData(sessionSettingsBean.getUserInfo().getUser(), sessionSettingsBean.getSelectedInstitution())) {
            throw new ForbiddenException();
        }
        navBean.setApplicationMode(NavBean.ApplicationMode.SETTINGS);
        exportTemplatesSettingsBean.init();
        return "forward:/pages/settings/exportTemplates.xhtml";
    }

    @GetMapping("/settings/organisation")
    public String goToAdminInstitutionSettings(@RequestParam(value = "memberId", required = false) Long memberId) {
        if (!profilePermissionService.canViewInstitutionData(sessionSettingsBean.getUserInfo().getUser(), sessionSettingsBean.getSelectedInstitution())) {
            throw new ForbiddenException();
        }
        navBean.setApplicationMode(NavBean.ApplicationMode.SETTINGS);
        PersonDTO member = resolveMember(memberId);
        if (member != null) {
            institutionListSettingsBean.initFilteredByPerson(member);
        } else {
            institutionListSettingsBean.init();
        }
        return "forward:/pages/settings/institutionListSettings.xhtml";
    }

    @GetMapping("/settings/project")
    public String goToProjectsSettings(@RequestParam(value = "memberId", required = false) Long memberId) {
        navBean.setApplicationMode(NavBean.ApplicationMode.SETTINGS);
        PersonDTO member = resolveMember(memberId);
        if (member != null) {
            projectListBean.initFilteredByPerson(member);
        } else {
            projectListBean.init();
        }
        return "forward:/pages/settings/project/projectList.xhtml";
    }

    /**
     * The project, once the caller is known to manage its settings. The right is checked against the
     * institution owning the project, and an unknown project answers like a forbidden one.
     */
    private ActionUnitDTO requireProjectSettingsAccess(Long projectId) {
        ActionUnitDTO project;
        try {
            project = actionUnitService.findById(projectId);
        } catch (RuntimeException e) {
            throw new ForbiddenException();
        }
        if (!profilePermissionService.hasProjectSettingsPermission(sessionSettingsBean.getAuthenticatedUser(), project)) {
            throw new ForbiddenException();
        }
        return project;
    }

    /**
     * A project's settings page (its tiles: import, members, tables, thesaurus): where the project fiche's
     * settings button leads. Same right as the settings list's own entry.
     */
    @GetMapping("/settings/project/{projectId}")
    public String goToProjectSettings(@PathVariable("projectId") Long projectId) {
        ActionUnitDTO project = requireProjectSettingsAccess(projectId);
        navBean.setApplicationMode(NavBean.ApplicationMode.SETTINGS);
        projectDetailsBean.setProject(project);
        projectDetailsBean.init();
        return "forward:/pages/settings/project/projectSettings.xhtml";
    }

    /**
     * Straight to a project's table configuration (the page behind its "tables" tile), optionally
     * opened on one table: what a creation form links to when a table has no type declared yet. A fiche
     * links here too, on its type ({@code typeId}, a concept id) and, from a field's menu, on that field
     * ({@code fieldId}, a custom field id, or {@code fieldCode}, the field code it reads its values from).
     */
    @GetMapping("/settings/project/{projectId}/tables")
    public String goToProjectTablesSettings(@PathVariable("projectId") Long projectId,
                                            @RequestParam(value = "table", required = false) String table,
                                            @RequestParam(value = "typeId", required = false) Long typeId,
                                            @RequestParam(value = "fieldId", required = false) Long fieldId,
                                            @RequestParam(value = "fieldCode", required = false) String fieldCode) {
        ActionUnitDTO project = requireProjectSettingsAccess(projectId);
        navBean.setApplicationMode(NavBean.ApplicationMode.SETTINGS);
        projectDetailsBean.setProject(project);
        projectDetailsBean.init();
        projectTableFieldSettingsBean.init(project);
        if (table != null) {
            try {
                ConfigurableTable configurable = ConfigurableTable.valueOf(table);
                if (typeId != null) {
                    projectTableFieldSettingsBean.openOn(configurable, typeId, fieldId, fieldCode);
                } else {
                    projectTableFieldSettingsBean.selectTable(configurable);
                }
            } catch (IllegalArgumentException ignored) {
                // an unknown table: the page opens on its first one
            }
        }
        return "forward:/pages/settings/project/projectTablesSettings.xhtml";
    }

    @GetMapping("/settings/administration")
    public String goToUserManagementSettings() {
        if (!profilePermissionService.hasInstancePermission(
                sessionSettingsBean.getUserInfo().getUser(), PermissionConstants.INSTANCE_MANAGE_SETTINGS)) {
            throw new ForbiddenException();
        }
        navBean.setApplicationMode(NavBean.ApplicationMode.SETTINGS);
        applicationMembersListBean.init();
        return "forward:/pages/settings/administration/userManagementSettings.xhtml";
    }

    /**
     * Redirect /focus/{mainToken}?s={secondaryToken} to JSF view
     */
    @GetMapping("/focus/{mainToken}")
    public String goToFocus(@PathVariable("mainToken") String mainToken,
                            @RequestParam(value = "s", required = false) String secondaryToken) {
        navBean.setApplicationMode(NavBean.ApplicationMode.SIAMOIS);
        return "forward:/pages/focus.xhtml?main=" + mainToken
                + (secondaryToken != null ? "&s=" + secondaryToken : "");
    }


}
