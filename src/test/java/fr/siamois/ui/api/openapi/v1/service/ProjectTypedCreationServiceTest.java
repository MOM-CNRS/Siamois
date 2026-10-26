package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.dto.entity.DocumentDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.api.openapi.v1.service.ProjectTypedCreationService.Kind;
import fr.siamois.ui.api.openapi.v1.service.ProjectTypedCreationService.ProjectContext;
import fr.siamois.utils.context.ExecutionContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectTypedCreationServiceTest {

    private static final Set<Long> SCOPE = Set.of(10L);

    @Mock
    private ActionUnitService actionUnitService;
    @Mock
    private ConceptService conceptService;
    @Mock
    private ConceptMapper conceptMapper;
    @Mock
    private ProfilePermissionService profilePermissionService;

    private ProjectTypedCreationService service;

    private PersonDTO person;
    private InstitutionDTO institution;
    private ActionUnitDTO project;

    @BeforeEach
    void setUp() {
        service = new ProjectTypedCreationService(actionUnitService, conceptService, conceptMapper, profilePermissionService);
        person = new PersonDTO();
        person.setId(1L);
        institution = new InstitutionDTO();
        institution.setId(10L);
        project = new ActionUnitDTO();
        project.setId(7L);
        project.setCreatedByInstitution(institution);
    }

    private static HttpStatusCode statusOf(Throwable ex) {
        return ((ResponseStatusException) ex).getStatusCode();
    }

    private void projectIsAccessible() {
        when(actionUnitService.findAccessibleProjectByKey("7", SCOPE)).thenReturn(new AccessibleProjectForApi(project, 0L, 0L));
    }

    private ConceptDTO typeExists() {
        Concept concept = new Concept();
        concept.setId(2L);
        when(conceptService.findById(2L)).thenReturn(Optional.of(concept));
        ConceptDTO type = new ConceptDTO();
        type.setId(2L);
        when(conceptMapper.convert(concept)).thenReturn(type);
        return type;
    }

    @Test
    void resolveProject_givesTheProjectAndTheCallersContextInItsOrganization() {
        when(actionUnitService.findAccessibleProjectByKey("7", SCOPE)).thenReturn(new AccessibleProjectForApi(project, 0L, 0L));

        ProjectContext context = service.resolveProject(" 7 ", person, SCOPE, "en");

        assertThat(context.project()).isSameAs(project);
        assertThat(context.institution()).isSameAs(institution);
        assertThat(context.userInfo().getInstitution()).isSameAs(institution);
        assertThat(context.userInfo().getUser()).isSameAs(person);
        assertThat(context.userInfo().getLang()).isEqualTo("en");
        assertThat(context.projectSummary().getId()).isEqualTo(7L);
    }

    @Test
    void resolveProject_blankKey_throws400() {
        assertThatThrownBy(() -> service.resolveProject(" ", person, SCOPE, "fr"))
                .satisfies(ex -> assertThat(statusOf(ex)).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(actionUnitService);
    }

    @Test
    void resolveProject_withoutOrganization_throws400() {
        project.setCreatedByInstitution(null);
        projectIsAccessible();

        assertThatThrownBy(() -> service.resolveProject("7", person, SCOPE, "fr"))
                .satisfies(ex -> assertThat(statusOf(ex)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void requirePermission_checksTheThreeScopesOnTheProject() {
        projectIsAccessible();
        ProjectContext context = service.resolveProject("7", person, SCOPE, "fr");
        when(profilePermissionService.hasProjectPermission(context.userInfo(), 7L,
                PermissionConstants.INSTANCE_EDIT_PHASES, PermissionConstants.ORGANIZATION_EDIT_PHASES,
                PermissionConstants.PROJECT_EDIT_PHASES)).thenReturn(true, false);

        service.requirePermission(context, "Interdit", PermissionConstants.INSTANCE_EDIT_PHASES,
                PermissionConstants.ORGANIZATION_EDIT_PHASES, PermissionConstants.PROJECT_EDIT_PHASES);
        assertThatThrownBy(() -> service.requirePermission(context, "Interdit", PermissionConstants.INSTANCE_EDIT_PHASES,
                PermissionConstants.ORGANIZATION_EDIT_PHASES, PermissionConstants.PROJECT_EDIT_PHASES))
                .hasMessageContaining("Interdit")
                .satisfies(ex -> assertThat(statusOf(ex)).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void resolveType_mapsTheConcept() {
        ConceptDTO type = typeExists();

        assertThat(service.resolveType("2", Kind.PHASE)).isSameAs(type);
    }

    @Test
    void resolveType_unknown_throws404WithTheGivenMessage() {
        when(conceptService.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveType("2", Kind.CONTAINER))
                .hasMessageContaining("Type de contenant introuvable")
                .satisfies(ex -> assertThat(statusOf(ex)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void resolveType_notNumeric_throws400() {
        assertThatThrownBy(() -> service.resolveType("abc", Kind.PHASE))
                .satisfies(ex -> assertThat(statusOf(ex)).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(conceptService);
    }

    @Test
    void suggestIdentifier_runsTheSuggestionInTheCallersContext_thenUnbindsIt() {
        projectIsAccessible();
        ConceptDTO type = typeExists();

        String suggested = service.suggestIdentifier(Kind.PHASE, "7", "2", person, SCOPE, "fr",
                phase -> {
                    // The identifier configuration is read in the context of a user: without one bound
                    // to the thread, its resolution fails ("No user bound to the current thread").
                    UserInfo current = ExecutionContextHolder.get();
                    assertThat(current).isNotNull();
                    assertThat(current.getInstitution().getId()).isEqualTo(10L);
                    assertThat(current.getUser()).isSameAs(person);
                    assertThat(current.getLang()).isEqualTo("fr");
                    // The new, unsaved phase of the type in the project.
                    assertThat(phase.getId()).isNull();
                    assertThat(phase.getActionUnit().getId()).isEqualTo(7L);
                    assertThat(phase.getType()).isSameAs(type);
                    return "P-1";
                });

        assertThat(suggested).isEqualTo("P-1");
        assertThat(ExecutionContextHolder.get()).isNull();
        // Nothing is written, so no edit right is required.
        verify(profilePermissionService, never()).hasProjectPermission(any(UserInfo.class), any(), any(), any(), any());
    }

    @Test
    void projectSummary_keepsTheProjectsIdentifiers_readByTheIdentifierFormats() {
        project.setName("Fouille");
        project.setIdentifier("PROJ7");
        project.setFullIdentifier("ORG-PROJ7");
        projectIsAccessible();

        ActionUnitSummaryDTO summary = service.resolveProject("7", person, SCOPE, "fr").projectSummary();

        assertThat(summary.getId()).isEqualTo(7L);
        assertThat(summary.getName()).isEqualTo("Fouille");
        assertThat(summary.getIdentifier()).isEqualTo("PROJ7");
        // {ID_UA}: a summary built from the id and name alone renders XXX.
        assertThat(summary.getFullIdentifier()).isEqualTo("ORG-PROJ7");
    }

    @Test
    void newEntity_isTheUnsavedEntityOfTheTypeInTheProject_createdByTheCaller() {
        projectIsAccessible();
        ProjectContext context = service.resolveProject("7", person, SCOPE, "fr");
        ConceptDTO type = new ConceptDTO();
        type.setId(2L);

        DocumentDTO document = service.newEntity(Kind.DOCUMENT, context, type);
        RecordingUnitDTO unit = service.newEntity(Kind.RECORDING_UNIT, context, type);

        assertThat(document.getId()).isNull();
        assertThat(document.getActionUnit().getId()).isEqualTo(7L);
        assertThat(document.getType()).isSameAs(type);
        // TraceableEntity's NOT NULL author and organization: the caller, in the project's organization.
        assertThat(document.getCreatedBy()).isSameAs(person);
        assertThat(document.getCreatedByInstitution()).isSameAs(institution);
        assertThat(unit.getActionUnit().getId()).isEqualTo(7L);
        assertThat(unit.getType()).isSameAs(type);
    }
}
