package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.ark.Ark;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.exceptions.InvalidFileTypeException;
import fr.siamois.domain.models.settings.InstitutionSettings;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.ark.ArkService;
import fr.siamois.domain.services.authorization.PermissionService;
import fr.siamois.domain.services.document.DocumentService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.api.openapi.v1.mapper.ProjectDocumentOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.resource.document.ProjectDocumentResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentWriteOpenApiServiceTest {

    private static final Set<Long> SCOPE = Set.of(10L);

    @Mock
    private ProjectApiService projectApiService;
    @Mock
    private RecordingUnitService recordingUnitService;
    @Mock
    private DocumentContentOpenApiService documentContentOpenApiService;
    @Mock
    private DocumentService documentService;
    @Mock
    private ConceptService conceptService;
    @Mock
    private InstitutionService institutionService;
    @Mock
    private PermissionService permissionService;
    @Mock
    private ArkService arkService;
    @Mock
    private ProjectDocumentOpenApiMapper projectDocumentOpenApiMapper;

    private DocumentWriteOpenApiService service;
    private ProjectApiCaller caller;
    private MockMultipartFile file;

    @BeforeEach
    void setUp() {
        service = new DocumentWriteOpenApiService(
                projectApiService,
                recordingUnitService,
                documentContentOpenApiService,
                documentService,
                conceptService,
                institutionService,
                permissionService,
                arkService,
                projectDocumentOpenApiMapper);

        PersonDTO person = new PersonDTO();
        person.setId(1L);
        caller = new ProjectApiCaller(person, SCOPE, java.util.List.of());
        file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "content".getBytes());
    }

    @Test
    void createForProject_success_savesAndLinksToProject() throws Exception {
        InstitutionDTO institution = institutionDTO();
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(5L);
        project.setCreatedByInstitution(institution);
        AccessibleProjectForApi row = new AccessibleProjectForApi(project, 0L, 0L);
        when(projectApiService.requireAccessibleProject(caller, "5")).thenReturn(row);

        InstitutionSettings settings = mock(InstitutionSettings.class);
        when(settings.getArkIsEnabled()).thenReturn(false);
        when(institutionService.createOrGetSettingsOf(institution)).thenReturn(settings);

        Document saved = new Document();
        saved.setId(99L);
        when(documentService.saveFile(any(UserInfo.class), any(Document.class), any(InputStream.class), eq("/siamois")))
                .thenReturn(saved);

        ProjectDocumentResource resource = new ProjectDocumentResource();
        when(projectDocumentOpenApiMapper.toResource(saved)).thenReturn(resource);

        ProjectDocumentResource result = service.createForProject(
                caller, "5", "Title", "Desc", null, null, null, file, "fr");

        assertThat(result).isSameAs(resource);
        verify(documentService).addToActionUnit(saved, project);
    }

    @Test
    void createForProject_projectWithoutInstitution_throws400() {
        ActionUnitDTO project = new ActionUnitDTO();
        project.setCreatedByInstitution(null);
        when(projectApiService.requireAccessibleProject(caller, "5"))
                .thenReturn(new AccessibleProjectForApi(project, 0L, 0L));

        assertThatThrownBy(() -> service.createForProject(
                caller, "5", "Title", null, null, null, null, file, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createForProject_emptyFile_throws400() {
        InstitutionDTO institution = institutionDTO();
        ActionUnitDTO project = new ActionUnitDTO();
        project.setCreatedByInstitution(institution);
        when(projectApiService.requireAccessibleProject(caller, "5"))
                .thenReturn(new AccessibleProjectForApi(project, 0L, 0L));

        MockMultipartFile empty = new MockMultipartFile("file", "x.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> service.createForProject(
                caller, "5", "Title", null, null, null, null, empty, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("file est obligatoire");
    }

    @Test
    void createForProject_blankTitle_throws400() {
        InstitutionDTO institution = institutionDTO();
        ActionUnitDTO project = new ActionUnitDTO();
        project.setCreatedByInstitution(institution);
        when(projectApiService.requireAccessibleProject(caller, "5"))
                .thenReturn(new AccessibleProjectForApi(project, 0L, 0L));

        assertThatThrownBy(() -> service.createForProject(
                caller, "5", "  ", null, null, null, null, file, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("title est obligatoire");
    }

    @Test
    void createForProject_invalidFileType_throws400() throws Exception {
        InstitutionDTO institution = institutionDTO();
        ActionUnitDTO project = new ActionUnitDTO();
        project.setCreatedByInstitution(institution);
        when(projectApiService.requireAccessibleProject(caller, "5"))
                .thenReturn(new AccessibleProjectForApi(project, 0L, 0L));
        when(institutionService.createOrGetSettingsOf(institution)).thenReturn(mock(InstitutionSettings.class));
        when(documentService.saveFile(any(), any(), any(), any()))
                .thenThrow(new InvalidFileTypeException("bad type"));

        assertThatThrownBy(() -> service.createForProject(
                caller, "5", "Title", null, null, null, null, file, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createForRecordingUnit_forbiddenWithoutWritePermission_throws403() {
        RecordingUnitDTO ru = new RecordingUnitDTO();
        ru.setCreatedByInstitution(institutionDTO());
        when(recordingUnitService.findAccessibleRecordingUnitByKey("UE-1", SCOPE, null)).thenReturn(ru);
        when(permissionService.hasWritePermission(any(UserInfo.class), same(ru))).thenReturn(false);

        assertThatThrownBy(() -> service.createForRecordingUnit(
                caller, "UE-1", "Title", null, null, null, null, file, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void createForRecordingUnit_success_linksToRecordingUnit() throws Exception {
        InstitutionDTO institution = institutionDTO();
        RecordingUnitDTO ru = new RecordingUnitDTO();
        ru.setId(42L);
        ru.setCreatedByInstitution(institution);
        when(recordingUnitService.findAccessibleRecordingUnitByKey("UE-1", SCOPE, null)).thenReturn(ru);
        when(permissionService.hasWritePermission(any(UserInfo.class), same(ru))).thenReturn(true);

        InstitutionSettings settings = mock(InstitutionSettings.class);
        when(settings.getArkIsEnabled()).thenReturn(true);
        when(institutionService.createOrGetSettingsOf(institution)).thenReturn(settings);
        when(arkService.generateAndSave(settings)).thenReturn(mock(Ark.class));

        Document saved = new Document();
        when(documentService.saveFile(any(), any(), any(), any())).thenReturn(saved);
        ProjectDocumentResource resource = new ProjectDocumentResource();
        when(projectDocumentOpenApiMapper.toResource(saved)).thenReturn(resource);

        ProjectDocumentResource result = service.createForRecordingUnit(
                caller, "UE-1", "Title", null, null, null, null, file, "fr");

        assertThat(result).isSameAs(resource);
        verify(documentService).addToRecordingUnit(saved, ru);
    }

    @Test
    void updateDocument_updatesProvidedFields() {
        Document doc = new Document();
        doc.setTitle("Old");
        when(documentContentOpenApiService.requireAccessibleDocument(8L, SCOPE)).thenReturn(doc);

        Concept nature = mock(Concept.class);
        when(conceptService.findById(3L)).thenReturn(Optional.of(nature));

        Document saved = new Document();
        when(documentService.save(doc)).thenReturn(saved);
        ProjectDocumentResource resource = new ProjectDocumentResource();
        when(projectDocumentOpenApiMapper.toResource(saved)).thenReturn(resource);

        ProjectDocumentResource result = service.updateDocument(
                caller, 8L, "New title", "New desc", 3L, null, null);

        assertThat(result).isSameAs(resource);
        assertThat(doc.getTitle()).isEqualTo("New title");
        assertThat(doc.getDescription()).isEqualTo("New desc");
        assertThat(doc.getNature()).isSameAs(nature);
    }

    @Test
    void updateDocument_unknownConcept_throws404() {
        Document doc = new Document();
        when(documentContentOpenApiService.requireAccessibleDocument(8L, SCOPE)).thenReturn(doc);
        when(conceptService.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateDocument(caller, 8L, null, null, 404L, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void createForProject_ioError_throws500() throws Exception {
        InstitutionDTO institution = institutionDTO();
        ActionUnitDTO project = new ActionUnitDTO();
        project.setCreatedByInstitution(institution);
        when(projectApiService.requireAccessibleProject(caller, "5"))
                .thenReturn(new AccessibleProjectForApi(project, 0L, 0L));
        when(institutionService.createOrGetSettingsOf(institution)).thenReturn(mock(InstitutionSettings.class));

        MockMultipartFile broken = mock(MockMultipartFile.class);
        when(broken.isEmpty()).thenReturn(false);
        when(broken.getOriginalFilename()).thenReturn("x.pdf");
        when(broken.getContentType()).thenReturn("application/pdf");
        when(broken.getSize()).thenReturn(1L);
        doThrow(new IOException("read fail")).when(broken).getInputStream();

        assertThatThrownBy(() -> service.createForProject(
                caller, "5", "Title", null, null, null, null, broken, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    private static InstitutionDTO institutionDTO() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(10L);
        return institution;
    }
}
