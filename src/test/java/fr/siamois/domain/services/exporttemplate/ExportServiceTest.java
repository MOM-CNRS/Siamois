package fr.siamois.domain.services.exporttemplate;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.permission.ForbiddenOperationException;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.*;
import fr.siamois.domain.models.exporttemplate.InvalidExportTemplateException;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.permissions.PersonProfileAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    @Mock private ExportTemplateService templateService;
    @Mock private ProfilePermissionService permissions;
    @Mock private ActionUnitRepository actionUnitRepository;
    @Mock private PersonProfileAssignmentRepository assignmentRepository;
    @Mock private ExportEngine engine;

    private ExportService service;
    private UserInfo userInfo;
    private PersonDTO person;
    private InstitutionDTO institutionDto;

    @BeforeEach
    void setUp() {
        service = new ExportService(templateService, permissions, actionUnitRepository, assignmentRepository, engine);
        person = new PersonDTO();
        institutionDto = new InstitutionDTO();
        institutionDto.setId(1L);
        userInfo = new UserInfo(institutionDto, person, "fr");
        org.mockito.Mockito.lenient().when(actionUnitRepository.existsById(9L)).thenReturn(true);
        org.mockito.Mockito.lenient().when(actionUnitRepository.existsByIdAndCreatedByInstitutionId(9L, 1L)).thenReturn(true);
    }

    private static ExportTemplateDefinition definition(Source source, Rule rule) {
        return new ExportTemplateDefinition(3, "u", "1", "T", null, List.of(new Sheet("S", false, List.of(source), List.of(),
                List.of(new Column("c", OutputType.TEXT, List.of(rule))))));
    }

    @Test
    void export_withRights_runsTheEngineInTheUsersLanguage() {
        ExportTemplateDefinition d = definition(new ProjectSource(), new ConstantRule(List.of(), "x"));
        ExportEngine.Result result = new ExportEngine.Result(new byte[0], "f.xlsx", List.of(), java.util.Map.of());
        when(permissions.canViewProject(person, institutionDto, 9L)).thenReturn(true);
        when(templateService.getDefinition(userInfo, 4L)).thenReturn(d);
        when(engine.run(d, 9L, "fr")).thenReturn(result);

        assertThat(service.export(userInfo, 4L, 9L)).isSameAs(result);
    }

    @Test
    void export_withoutViewRightOnTheProject_isForbidden() {
        when(permissions.canViewProject(person, institutionDto, 9L)).thenReturn(false);

        assertThatThrownBy(() -> service.export(userInfo, 4L, 9L)).isInstanceOf(ForbiddenOperationException.class);
        verifyNoInteractions(engine, templateService);
    }

    @Test
    void export_ofAProjectOfAnotherInstitution_isForbidden() {
        when(actionUnitRepository.existsByIdAndCreatedByInstitutionId(9L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> service.export(userInfo, 4L, 9L)).isInstanceOf(ForbiddenOperationException.class);
        verifyNoInteractions(engine, templateService);
    }

    @Test
    void export_ofAnUnknownProject_isNotFound() {
        when(actionUnitRepository.existsById(10L)).thenReturn(false);

        assertThatThrownBy(() -> service.export(userInfo, 4L, 10L)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void export_ofATemplateThatDoesNotMatchTheRegistries_isRejectedBeforeAnyRead() {
        when(permissions.canViewProject(person, institutionDto, 9L)).thenReturn(true);
        when(templateService.getDefinition(userInfo, 4L)).thenReturn(definition(new TableSource("Mobilier"), new ConstantRule(List.of(), "x")));

        assertThatThrownBy(() -> service.export(userInfo, 4L, 9L))
                .isInstanceOf(InvalidExportTemplateException.class)
                .hasMessageContaining("Mobilier");
        verify(engine, never()).run(any(), eq(9L), any());
    }

    @Test
    void preview_appliesTheSameChecksAsTheExport() {
        ExportTemplateDefinition d = definition(new ProjectSource(), new ConstantRule(List.of(), "x"));
        ExportEngine.Preview preview = new ExportEngine.Preview(List.of(), List.of());
        when(permissions.canViewProject(person, institutionDto, 9L)).thenReturn(true);
        when(engine.preview(d, 9L, "fr", 20)).thenReturn(preview);

        assertThat(service.preview(userInfo, d, 9L, 20)).isSameAs(preview);
    }

    @Test
    void preview_withoutRight_orWithAForeignTemplate_isRefused() {
        when(permissions.canViewProject(person, institutionDto, 9L)).thenReturn(false);
        ExportTemplateDefinition projectTemplate = definition(new ProjectSource(), new ConstantRule(List.of(), "x"));
        assertThatThrownBy(() -> service.preview(userInfo, projectTemplate, 9L, 20))
                .isInstanceOf(ForbiddenOperationException.class);

        when(permissions.canViewProject(person, institutionDto, 9L)).thenReturn(true);
        ExportTemplateDefinition tableTemplate = definition(new TableSource("T"), new ConstantRule(List.of(), "x"));
        assertThatThrownBy(() -> service.preview(userInfo, tableTemplate, 9L, 20))
                .isInstanceOf(InvalidExportTemplateException.class);
        verify(engine, never()).preview(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    private static ActionUnitRepository.Summary summary(long id, String fullIdentifier, String name) {
        return new ActionUnitRepository.Summary() {
            @Override public Long getId() { return id; }
            @Override public String getFullIdentifier() { return fullIdentifier; }
            @Override public String getName() { return name; }
        };
    }

    @Test
    void listReadableProjects_withInstitutionRights_returnsAllSortedByIdentifier() {
        person.setId(5L);
        when(permissions.canViewInstitutionData(person, institutionDto)).thenReturn(true);
        when(actionUnitRepository.findSummariesByCreatedByInstitutionId(1L))
                .thenReturn(List.of(summary(2L, "B-2", "Deux"), summary(1L, "A-1", "Un"), summary(3L, null, "Sans")));

        assertThat(service.listReadableProjects(userInfo)).extracting(ExportService.ProjectChoice::id)
                .containsExactly(3L, 1L, 2L);
        verifyNoInteractions(assignmentRepository);
    }

    @Test
    void listReadableProjects_withoutInstitutionRights_keepsOnlyProjectsWithAProfile() {
        person.setId(5L);
        when(permissions.canViewInstitutionData(person, institutionDto)).thenReturn(false);
        when(assignmentRepository.findInstitutionActionUnitIdsWithAnyProfile(5L, 1L)).thenReturn(java.util.Set.of(2L));
        when(actionUnitRepository.findSummariesByCreatedByInstitutionId(1L))
                .thenReturn(List.of(summary(1L, "A-1", "Un"), summary(2L, "B-2", "Deux")));

        assertThat(service.listReadableProjects(userInfo)).extracting(ExportService.ProjectChoice::id).containsExactly(2L);
    }
}
