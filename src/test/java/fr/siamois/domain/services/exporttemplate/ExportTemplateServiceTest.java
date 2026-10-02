package fr.siamois.domain.services.exporttemplate;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.permission.ForbiddenOperationException;
import fr.siamois.domain.models.exporttemplate.ExportTemplate;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition;
import fr.siamois.domain.models.exporttemplate.ExportTemplateJson;
import fr.siamois.domain.models.exporttemplate.InvalidExportTemplateException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.entity.ExportTemplateDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.exporttemplate.ExportTemplateRepository;
import fr.siamois.mapper.InstitutionMapper;
import fr.siamois.mapper.PersonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExportTemplateServiceTest {

    @Mock private ExportTemplateRepository repository;
    @Mock private ActionUnitRepository actionUnitRepository;
    @Mock private ProfilePermissionService permissions;
    @Mock private InstitutionMapper institutionMapper;
    @Mock private PersonMapper personMapper;

    private ExportTemplateService service;
    private UserInfo userInfo;
    private InstitutionDTO institutionDto;
    private PersonDTO personDto;
    private Institution institution;

    @BeforeEach
    void setUp() {
        service = new ExportTemplateService(repository, actionUnitRepository, permissions, institutionMapper, personMapper);
        institutionDto = new InstitutionDTO();
        institutionDto.setId(7L);
        personDto = new PersonDTO();
        userInfo = new UserInfo(institutionDto, personDto, "fr");
        institution = new Institution();
        institution.setId(7L);
        lenient().when(institutionMapper.invertConvert(institutionDto)).thenReturn(institution);
        lenient().when(personMapper.invertConvert(personDto)).thenReturn(new Person());
        lenient().when(repository.save(any(ExportTemplate.class))).thenAnswer(i -> {
            ExportTemplate t = i.getArgument(0);
            if (t.getId() == null) t.setId(1L);
            return t;
        });
    }

    private void allowView() {
        when(permissions.canViewInstitutionData(personDto, institutionDto)).thenReturn(true);
    }

    private void allowManage() {
        when(permissions.hasInstancePermission(personDto, PermissionConstants.INSTANCE_MANAGE_SETTINGS)).thenReturn(false);
        when(permissions.hasOrganizationPermission(userInfo, PermissionConstants.ORGANIZATION_MANAGE_SETTINGS)).thenReturn(true);
    }

    private ExportTemplate stored(Long id, String json) {
        ExportTemplateDefinition d = ExportTemplateJson.parse(json);
        ExportTemplate t = new ExportTemplate();
        t.setId(id);
        t.setInstitution(institution);
        t.setTemplateUuid(d.id());
        t.setName(d.name());
        t.setVersion(d.version());
        t.setDefinition(json);
        return t;
    }

    private static String sampleJson(String uuid, String name) {
        return "{\"schemaVersion\":1,\"id\":\"" + uuid + "\",\"version\":\"1.0.0\",\"name\":\"" + name + "\","
                + "\"sheets\":[{\"name\":\"OA\",\"sources\":[{\"kind\":\"PROJECT\"}],"
                + "\"columns\":[{\"header\":\"c\",\"rules\":[{\"type\":\"CONSTANT\",\"value\":\"x\"}]}]}]}";
    }

    @Test
    void findAll_withoutViewPermission_isForbidden() {
        when(permissions.canViewInstitutionData(personDto, institutionDto)).thenReturn(false);

        assertThatThrownBy(() -> service.findAll(userInfo)).isInstanceOf(ForbiddenOperationException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void findAll_mapsEntitiesToDtos() {
        allowView();
        when(repository.findByInstitutionOrderByNameAsc(institution)).thenReturn(List.of(stored(3L, sampleJson("u1", "Rapport"))));

        List<ExportTemplateDTO> result = service.findAll(userInfo);

        assertThat(result).singleElement().satisfies(dto -> {
            assertThat(dto.id()).isEqualTo(3L);
            assertThat(dto.name()).isEqualTo("Rapport");
            assertThat(dto.referenceProjectId()).isNull();
        });
    }

    @Test
    void createBlank_withoutManagePermission_isForbidden() {
        when(permissions.hasInstancePermission(personDto, PermissionConstants.INSTANCE_MANAGE_SETTINGS)).thenReturn(false);
        when(permissions.hasOrganizationPermission(userInfo, PermissionConstants.ORGANIZATION_MANAGE_SETTINGS)).thenReturn(false);

        assertThatThrownBy(() -> service.createBlank(userInfo, "N", "S", "C"))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void createBlank_storesAValidDefinitionWithSyncedColumns() {
        allowManage();

        service.createBlank(userInfo, "Mon modèle", "Feuille", "col");

        ArgumentCaptor<ExportTemplate> captor = ArgumentCaptor.forClass(ExportTemplate.class);
        verify(repository).save(captor.capture());
        ExportTemplate saved = captor.getValue();
        assertThat(saved.getInstitution()).isSameAs(institution);
        assertThat(saved.getName()).isEqualTo("Mon modèle");
        ExportTemplateDefinition parsed = ExportTemplateJson.parse(saved.getDefinition());
        assertThat(parsed.id()).isEqualTo(saved.getTemplateUuid());
        assertThat(parsed.sheets().get(0).name()).isEqualTo("Feuille");
    }

    @Test
    void importJson_invalidJson_isRejectedBeforeAnyWrite() {
        allowManage();

        assertThatThrownBy(() -> service.importJson(userInfo, "{\"schemaVersion\":1}"))
                .isInstanceOf(InvalidExportTemplateException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void importJson_keepsTheGlobalId_whenFree() {
        allowManage();
        when(repository.existsByInstitutionAndTemplateUuid(institution, "global-1")).thenReturn(false);

        service.importJson(userInfo, sampleJson("global-1", "Importé"));

        ArgumentCaptor<ExportTemplate> captor = ArgumentCaptor.forClass(ExportTemplate.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTemplateUuid()).isEqualTo("global-1");
    }

    @Test
    void importJson_assignsANewId_whenTheInstitutionAlreadyHasIt() {
        allowManage();
        when(repository.existsByInstitutionAndTemplateUuid(institution, "global-1")).thenReturn(true);

        service.importJson(userInfo, sampleJson("global-1", "Importé"));

        ArgumentCaptor<ExportTemplate> captor = ArgumentCaptor.forClass(ExportTemplate.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTemplateUuid()).isNotEqualTo("global-1");
        assertThat(ExportTemplateJson.parse(captor.getValue().getDefinition()).id()).isEqualTo(captor.getValue().getTemplateUuid());
    }

    @Test
    void updateDefinition_withAnotherId_isRejected() {
        allowManage();
        ExportTemplate existing = stored(5L, sampleJson("u1", "A"));
        when(repository.findByIdAndInstitution(5L, institution)).thenReturn(Optional.of(existing));

        ExportTemplateDefinition other = ExportTemplateJson.parse(sampleJson("different", "A"));

        assertThatThrownBy(() -> service.updateDefinition(userInfo, 5L, other))
                .isInstanceOf(InvalidExportTemplateException.class);
    }

    @Test
    void rename_updatesBothTheColumnAndTheJson() {
        allowManage();
        ExportTemplate existing = stored(5L, sampleJson("u1", "Ancien"));
        when(repository.findByIdAndInstitution(5L, institution)).thenReturn(Optional.of(existing));

        service.rename(userInfo, 5L, "Nouveau");

        assertThat(existing.getName()).isEqualTo("Nouveau");
        assertThat(ExportTemplateJson.parse(existing.getDefinition()).name()).isEqualTo("Nouveau");
    }

    @Test
    void duplicate_getsANewIdAndKeepsTheReferenceProject() {
        allowManage();
        ExportTemplate existing = stored(5L, sampleJson("u1", "A"));
        ActionUnit project = new ActionUnit();
        project.setId(11L);
        existing.setReferenceProject(project);
        when(repository.findByIdAndInstitution(5L, institution)).thenReturn(Optional.of(existing));

        service.duplicate(userInfo, 5L, "A (copie)");

        ArgumentCaptor<ExportTemplate> captor = ArgumentCaptor.forClass(ExportTemplate.class);
        verify(repository, atLeastOnce()).save(captor.capture());
        ExportTemplate copy = captor.getValue();
        assertThat(copy.getTemplateUuid()).isNotEqualTo("u1");
        assertThat(copy.getName()).isEqualTo("A (copie)");
        assertThat(copy.getReferenceProject()).isSameAs(project);
    }

    @Test
    void setReferenceProject_ofAnotherInstitution_isForbidden() {
        allowManage();
        ExportTemplate existing = stored(5L, sampleJson("u1", "A"));
        when(repository.findByIdAndInstitution(5L, institution)).thenReturn(Optional.of(existing));
        Institution other = new Institution();
        other.setId(99L);
        ActionUnit project = new ActionUnit();
        project.setId(11L);
        project.setCreatedByInstitution(other);
        when(actionUnitRepository.findById(11L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.setReferenceProject(userInfo, 5L, 11L))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void setReferenceProject_null_clearsIt() {
        allowManage();
        ExportTemplate existing = stored(5L, sampleJson("u1", "A"));
        existing.setReferenceProject(new ActionUnit());
        when(repository.findByIdAndInstitution(5L, institution)).thenReturn(Optional.of(existing));

        service.setReferenceProject(userInfo, 5L, null);

        assertThat(existing.getReferenceProject()).isNull();
    }

    @Test
    void find_ofAnotherInstitution_isNotFound() {
        allowView();
        when(repository.findByIdAndInstitution(5L, institution)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.find(userInfo, 5L)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void exportJson_returnsTheStoredShareablePart() {
        allowView();
        String json = sampleJson("u1", "A");
        when(repository.findByIdAndInstitution(5L, institution)).thenReturn(Optional.of(stored(5L, json)));

        assertThat(service.exportJson(userInfo, 5L)).isEqualTo(json);
        verify(repository, never()).save(any());
        verify(permissions, never()).hasInstancePermission(any(), anyString());
    }
}
