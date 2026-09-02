package fr.siamois.ui.bean.settings.project;


import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.settings.SettingsDatatableBean;
import fr.siamois.utils.MessageUtils;
import fr.siamois.utils.context.ExecutionContextHolder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Getter
@Setter
@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class ProjectListBean implements SettingsDatatableBean {

    private final transient ActionUnitService actionUnitService;
    private final NavBean navBean;
    private final transient SessionSettingsBean sessionSettingsBean;
    private final transient ProjectDetailsBean projectDetailsBean;
    private final transient ProjectMembersListBean projectMembersListBean;
    private final transient InstitutionService institutionService;
    private final transient ProfilePermissionService profilePermissionService;
    private final transient PersonService personService;
    private final LangBean langBean;
    private String searchInput;

    private Set<ActionUnitDTO> actionUnits;
    private List<ActionUnitDTO> filteredActionUnits;
    private Map<Long, Integer> memberCountsByActionUnitId = Collections.emptyMap();
    private Set<Long> actionUnitIdsWithManageSettingsPermission = Collections.emptySet();

    /** Set when the list is filtered down to one member's projects (see {@link #initFilteredByPerson}). */
    private PersonDTO filterPerson;

    @Override
    public void add() {
        throw new UnsupportedOperationException("Adding action units is not supported in this context.");
    }

    @Override
    public void filter() {
        if (searchInput == null || searchInput.isEmpty()) {
            filteredActionUnits = new ArrayList<>(actionUnits);
        } else {
            filteredActionUnits = new ArrayList<>();
            for (ActionUnitDTO actionUnit : actionUnits) {
                if (actionUnit.getName().toLowerCase().contains(searchInput.toLowerCase())) {
                    filteredActionUnits.add(actionUnit);
                }
            }
        }
    }

    public int numberOfMemberInActionUnit(ActionUnitDTO actionUnit) {
        return memberCountsByActionUnitId.getOrDefault(actionUnit.getId(), 0);
    }

    /** Whether the current user may reach this project's settings — backs the row's burger button. */
    public boolean canManageSettings(ActionUnitDTO actionUnit) {
        return actionUnitIdsWithManageSettingsPermission.contains(actionUnit.getId());
    }

    public void reset() {
        this.searchInput = null;
        this.actionUnits = null;
        this.filteredActionUnits = null;
        this.filterPerson = null;
        this.memberCountsByActionUnitId = Collections.emptyMap();
        this.actionUnitIdsWithManageSettingsPermission = Collections.emptySet();
    }

    public void init() {
        reset();
        UserInfo info = ExecutionContextHolder.get();
        assert info != null;
        this.actionUnits = actionUnitService.findAllEditableByPerson(info.getUser());
        this.filteredActionUnits = new ArrayList<>(actionUnits);
        loadDerivedData();
    }

    /**
     * Loads the projects of an arbitrary member instead of the current admin's own editable ones, so the
     * list can be reached pre-filtered to "projects this person belongs to" (e.g. from the instance user list).
     *
     * @param person the member whose projects to show
     */
    public void initFilteredByPerson(PersonDTO person) {
        reset();
        this.filterPerson = person;
        this.actionUnits = new HashSet<>(actionUnitService.findAllByTeamMember(person));
        this.filteredActionUnits = new ArrayList<>(actionUnits);
        loadDerivedData();
    }

    /** Member counts and the row-level manage-settings permission, both computed in bulk to avoid an N+1. */
    private void loadDerivedData() {
        this.memberCountsByActionUnitId = institutionService.countMembersOf(actionUnits);
        List<Long> actionUnitIds = actionUnits.stream().map(ActionUnitDTO::getId).toList();
        this.actionUnitIdsWithManageSettingsPermission = profilePermissionService.actionUnitIdsWithPermission(
                sessionSettingsBean.getUserInfo(), actionUnitIds, PermissionConstants.PROJECT_MANAGE_SETTINGS);
    }

    /** Drops the person filter and reloads the current admin's own full editable project list. */
    public void clearPersonFilter() {
        init();
    }

    /** Backs the "my projects" chip: toggles the member filter between the current user and cleared. */
    public void toggleFilterByMe() {
        if (isFilteredByMe()) {
            clearPersonFilter();
        } else {
            initFilteredByPerson(sessionSettingsBean.getAuthenticatedUser());
        }
    }

    /** Whether the member filter is currently set to the current user — highlights the "my projects" chip. */
    public boolean isFilteredByMe() {
        PersonDTO me = sessionSettingsBean.getAuthenticatedUser();
        return filterPerson != null && me != null && filterPerson.getId().equals(me.getId());
    }

    /** Autocomplete source for the person filter: matches by username or e-mail. */
    public List<PersonDTO> completePerson(String query) {
        return personService.findClosestByUsernameOrEmail(query);
    }


    public String redirectToProject(ActionUnitDTO actionUnit) {
        if (!profilePermissionService.hasProjectPermission(
                sessionSettingsBean.getUserInfo(), actionUnit.getId(), PermissionConstants.PROJECT_MANAGE_SETTINGS)) {
            log.warn("Person {} tried to access settings of project {} without permission",
                    sessionSettingsBean.getUserInfo().getUser(), actionUnit.getId());
            MessageUtils.displayWarnMessage(langBean, "common.error.forbidden");
            return null;
        }
        projectDetailsBean.setProject(actionUnit);
        projectDetailsBean.init();
         return "/pages/settings/project/projectSettings.xhtml?faces-redirect=true";
    }

    /** Navigates straight to the project's member page — backs the row's "members" chip. */
    public String redirectToProjectMembers(ActionUnitDTO actionUnit) {
        if (!canManageSettings(actionUnit)) {
            log.warn("Person {} tried to access members of project {} without permission",
                    sessionSettingsBean.getUserInfo().getUser(), actionUnit.getId());
            MessageUtils.displayWarnMessage(langBean, "common.error.forbidden");
            return null;
        }
        projectDetailsBean.setProject(actionUnit);
        projectMembersListBean.init(actionUnit);
        return "/pages/settings/project/projectMembersSettings.xhtml?faces-redirect=true";
    }
}
