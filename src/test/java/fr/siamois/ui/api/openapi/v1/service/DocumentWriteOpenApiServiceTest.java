package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.ark.Ark;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.exceptions.InvalidFileSizeException;
import fr.siamois.domain.models.exceptions.InvalidFileTypeException;
import fr.siamois.domain.models.exceptions.spatialunit.SpatialUnitNotFoundException;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.settings.InstitutionSettings;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.ark.ArkService;
import fr.siamois.domain.services.document.DocumentService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.*;
import fr.siamois.mapper.ActionUnitMapper;
import fr.siamois.mapper.ActionUnitSummaryMapper;
import fr.siamois.ui.api.openapi.v1.mapper.ProjectDocumentOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.resource.document.DocumentResource;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentWriteOpenApiServiceTest {

    private static final Set<Long> SCOPE = Set.of(10L);

    @Mock
    private ProjectApiService projectApiService;
    @Mock
    private RecordingUnitService recordingUnitService;
    @Mock
    private SpecimenService specimenService;
    @Mock
    private SpatialUnitService spatialUnitService;
    @Mock
    private DocumentContentOpenApiService documentContentOpenApiService;
    @Mock
    private DocumentService documentService;
    @Mock
    private ConceptService conceptService;
    @Mock
    private InstitutionService institutionService;
    @Mock
    private ProfilePermissionService profilePermissionService;
    @Mock
    private ArkService arkService;
    @Mock
    private ProjectDocumentOpenApiMapper projectDocumentOpenApiMapper;
    @Mock
    private ActionUnitMapper actionUnitMapper;
    @Mock
    private ActionUnitSummaryMapper actionUnitSummaryMapper;

    private DocumentWriteOpenApiService service;
    private ProjectApiCaller caller;
    private MockMultipartFile file;

    @BeforeEach
    void setUp() {
        service = new DocumentWriteOpenApiService(
                projectApiService,
                recordingUnitService,
                specimenService,
                spatialUnitService,
                documentContentOpenApiService,
                documentService,
                conceptService,
                institutionService,
                profilePermissionService,
                arkService,
                projectDocumentOpenApiMapper,
                actionUnitMapper,
                actionUnitSummaryMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "contextPath", "/siamois");

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
        when(settings.hasEnabledArkConfig()).thenReturn(false);
        when(institutionService.createOrGetSettingsOf(institution)).thenReturn(settings);

        Document saved = new Document();
        saved.setId(99L);
        when(documentService.saveFile(any(UserInfo.class), any(Document.class), any(InputStream.class), eq("/siamois")))
                .thenReturn(saved);

        DocumentResource resource = new DocumentResource();
        when(projectDocumentOpenApiMapper.toResource(saved)).thenReturn(resource);

        DocumentResource result = service.createForProject(
                caller, "5", "Title", "Desc", null, null, null, file, "fr");

        assertThat(result).isSameAs(resource);
        verify(documentService).saveFile(any(UserInfo.class), any(Document.class), any(InputStream.class), eq("/siamois"));
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
    void createForProject_institutionWithoutId_throws400() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(null);
        ActionUnitDTO project = new ActionUnitDTO();
        project.setCreatedByInstitution(institution);
        when(projectApiService.requireAccessibleProject(caller, "5"))
                .thenReturn(new AccessibleProjectForApi(project, 0L, 0L));

        assertThatThrownBy(() -> service.createForProject(
                caller, "5", "Title", null, null, null, null, file, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createForProject_nullTitle_throws400() {
        InstitutionDTO institution = institutionDTO();
        ActionUnitDTO project = new ActionUnitDTO();
        project.setCreatedByInstitution(institution);
        when(projectApiService.requireAccessibleProject(caller, "5"))
                .thenReturn(new AccessibleProjectForApi(project, 0L, 0L));

        assertThatThrownBy(() -> service.createForProject(
                caller, "5", null, null, null, null, null, file, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("title est obligatoire");
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
    void createForProject_nullFile_throws400() {
        InstitutionDTO institution = institutionDTO();
        ActionUnitDTO project = new ActionUnitDTO();
        project.setCreatedByInstitution(institution);
        when(projectApiService.requireAccessibleProject(caller, "5"))
                .thenReturn(new AccessibleProjectForApi(project, 0L, 0L));

        assertThatThrownBy(() -> service.createForProject(
                caller, "5", "Title", null, null, null, null, null, "fr"))
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
        when(profilePermissionService.hasRecordingUnitWritePermission(any(UserInfo.class), same(ru))).thenReturn(false);

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
        when(profilePermissionService.hasRecordingUnitWritePermission(any(UserInfo.class), same(ru))).thenReturn(true);

        InstitutionSettings settings = mock(InstitutionSettings.class);
        when(settings.hasEnabledArkConfig()).thenReturn(true);
        when(institutionService.createOrGetSettingsOf(institution)).thenReturn(settings);
        when(arkService.generateAndSave(settings)).thenReturn(mock(Ark.class));

        Document saved = new Document();
        when(documentService.saveFile(any(), any(), any(), any())).thenReturn(saved);
        DocumentResource resource = new DocumentResource();
        when(projectDocumentOpenApiMapper.toResource(saved)).thenReturn(resource);

        DocumentResource result = service.createForRecordingUnit(
                caller, "UE-1", "Title", null, null, null, null, file, "fr");

        assertThat(result).isSameAs(resource);
        verify(documentService).addToRecordingUnit(saved, ru);
    }

    @Test
    void updateDocument_updatesProvidedFields() {
        Document doc = new Document();
        doc.setTitle("Old");
        when(documentContentOpenApiService.requireWritableDocument(8L, caller)).thenReturn(doc);

        Concept nature = mock(Concept.class);
        when(conceptService.findById(3L)).thenReturn(Optional.of(nature));

        Document saved = new Document();
        when(documentService.save(doc)).thenReturn(saved);
        DocumentResource resource = new DocumentResource();
        when(projectDocumentOpenApiMapper.toResource(saved)).thenReturn(resource);

        DocumentResource result = service.updateDocument(
                caller, 8L, "New title", "New desc", 3L, null, null);

        assertThat(result).isSameAs(resource);
        assertThat(doc.getTitle()).isEqualTo("New title");
        assertThat(doc.getDescription()).isEqualTo("New desc");
        assertThat(doc.primaryNature()).isSameAs(nature);
    }

    @Test
    void updateDocument_unknownConcept_throws404() {
        Document doc = new Document();
        when(documentContentOpenApiService.requireWritableDocument(8L, caller)).thenReturn(doc);
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

    @Test
    void createForRecordingUnit_withoutInstitution_throws400() {
        RecordingUnitDTO ru = new RecordingUnitDTO();
        ru.setCreatedByInstitution(null);
        when(recordingUnitService.findAccessibleRecordingUnitByKey("UE-1", SCOPE, null)).thenReturn(ru);

        assertThatThrownBy(() -> service.createForRecordingUnit(
                caller, "UE-1", "Title", null, null, null, null, file, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createForRecordingUnit_institutionWithoutId_throws400() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(null);
        RecordingUnitDTO ru = new RecordingUnitDTO();
        ru.setCreatedByInstitution(institution);
        when(recordingUnitService.findAccessibleRecordingUnitByKey("UE-1", SCOPE, null)).thenReturn(ru);

        assertThatThrownBy(() -> service.createForRecordingUnit(
                caller, "UE-1", "Title", null, null, null, null, file, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createForRecordingUnit_invalidFileSize_throws400() throws Exception {
        InstitutionDTO institution = institutionDTO();
        RecordingUnitDTO ru = new RecordingUnitDTO();
        ru.setCreatedByInstitution(institution);
        when(recordingUnitService.findAccessibleRecordingUnitByKey("UE-1", SCOPE, null)).thenReturn(ru);
        when(profilePermissionService.hasRecordingUnitWritePermission(any(UserInfo.class), same(ru))).thenReturn(true);
        when(institutionService.createOrGetSettingsOf(institution)).thenReturn(mock(InstitutionSettings.class));
        when(documentService.saveFile(any(), any(), any(), any()))
                .thenThrow(new InvalidFileSizeException(1L, "too big"));

        assertThatThrownBy(() -> service.createForRecordingUnit(
                caller, "UE-1", "Title", null, null, null, null, file, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createForRecordingUnit_ioError_throws500() throws Exception {
        InstitutionDTO institution = institutionDTO();
        RecordingUnitDTO ru = new RecordingUnitDTO();
        ru.setCreatedByInstitution(institution);
        when(recordingUnitService.findAccessibleRecordingUnitByKey("UE-1", SCOPE, null)).thenReturn(ru);
        when(profilePermissionService.hasRecordingUnitWritePermission(any(UserInfo.class), same(ru))).thenReturn(true);
        when(institutionService.createOrGetSettingsOf(institution)).thenReturn(mock(InstitutionSettings.class));

        MockMultipartFile broken = mock(MockMultipartFile.class);
        when(broken.isEmpty()).thenReturn(false);
        when(broken.getOriginalFilename()).thenReturn("x.pdf");
        when(broken.getContentType()).thenReturn("application/pdf");
        when(broken.getSize()).thenReturn(1L);
        doThrow(new IOException("read fail")).when(broken).getInputStream();

        assertThatThrownBy(() -> service.createForRecordingUnit(
                caller, "UE-1", "Title", null, null, null, null, broken, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    @Test
    void createForFind_success_linksSpecimen() throws Exception {
        InstitutionDTO institution = institutionDTO();
        SpecimenDTO specimen = new SpecimenDTO();
        specimen.setId(3L);
        specimen.setCreatedByInstitution(institution);
        when(specimenService.findAccessibleByKey("F-1", SCOPE)).thenReturn(Optional.of(specimen));
        when(profilePermissionService.hasSpecimenWritePermission(any(UserInfo.class), same(specimen))).thenReturn(true);
        when(institutionService.createOrGetSettingsOf(institution)).thenReturn(mock(InstitutionSettings.class));

        Document saved = new Document();
        when(documentService.saveFile(any(), any(), any(), any())).thenReturn(saved);
        DocumentResource resource = new DocumentResource();
        when(projectDocumentOpenApiMapper.toResource(saved)).thenReturn(resource);

        DocumentResource result = service.createForFind(
                caller, "F-1", "Title", "Desc", null, null, null, file, "fr");

        assertThat(result).isSameAs(resource);
        verify(documentService).addToSpecimen(saved, specimen);
    }

    @Test
    void createForFind_notFound_throws404() {
        when(specimenService.findAccessibleByKey("missing", SCOPE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createForFind(
                caller, "missing", "Title", null, null, null, null, file, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void createForFind_withoutInstitution_throws400() {
        SpecimenDTO specimen = new SpecimenDTO();
        specimen.setCreatedByInstitution(null);
        when(specimenService.findAccessibleByKey("F-1", SCOPE)).thenReturn(Optional.of(specimen));

        assertThatThrownBy(() -> service.createForFind(
                caller, "F-1", "Title", null, null, null, null, file, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createForFind_institutionWithoutId_throws400() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(null);
        SpecimenDTO specimen = new SpecimenDTO();
        specimen.setCreatedByInstitution(institution);
        when(specimenService.findAccessibleByKey("F-1", SCOPE)).thenReturn(Optional.of(specimen));

        assertThatThrownBy(() -> service.createForFind(
                caller, "F-1", "Title", null, null, null, null, file, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createForFind_forbidden_throws403() {
        SpecimenDTO specimen = new SpecimenDTO();
        specimen.setCreatedByInstitution(institutionDTO());
        when(specimenService.findAccessibleByKey("F-1", SCOPE)).thenReturn(Optional.of(specimen));
        when(profilePermissionService.hasSpecimenWritePermission(any(UserInfo.class), same(specimen))).thenReturn(false);

        assertThatThrownBy(() -> service.createForFind(
                caller, "F-1", "Title", null, null, null, null, file, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void createForFind_invalidType_throws400() throws Exception {
        SpecimenDTO specimen = new SpecimenDTO();
        specimen.setCreatedByInstitution(institutionDTO());
        when(specimenService.findAccessibleByKey("F-1", SCOPE)).thenReturn(Optional.of(specimen));
        when(profilePermissionService.hasSpecimenWritePermission(any(UserInfo.class), same(specimen))).thenReturn(true);
        when(institutionService.createOrGetSettingsOf(any())).thenReturn(mock(InstitutionSettings.class));
        when(documentService.saveFile(any(), any(), any(), any()))
                .thenThrow(new InvalidFileTypeException("bad"));

        assertThatThrownBy(() -> service.createForFind(
                caller, "F-1", "Title", null, null, null, null, file, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createForFind_ioError_throws500() throws Exception {
        SpecimenDTO specimen = new SpecimenDTO();
        specimen.setCreatedByInstitution(institutionDTO());
        when(specimenService.findAccessibleByKey("F-1", SCOPE)).thenReturn(Optional.of(specimen));
        when(profilePermissionService.hasSpecimenWritePermission(any(UserInfo.class), same(specimen))).thenReturn(true);
        when(institutionService.createOrGetSettingsOf(any())).thenReturn(mock(InstitutionSettings.class));

        MockMultipartFile broken = mock(MockMultipartFile.class);
        when(broken.isEmpty()).thenReturn(false);
        when(broken.getOriginalFilename()).thenReturn("x.pdf");
        when(broken.getContentType()).thenReturn("application/pdf");
        when(broken.getSize()).thenReturn(1L);
        doThrow(new IOException("io")).when(broken).getInputStream();

        assertThatThrownBy(() -> service.createForFind(
                caller, "F-1", "Title", null, null, null, null, broken, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    @Test
    void createForPlace_success_linksSpatialUnit() throws Exception {
        SpatialUnitDTO place = placeDTO();
        when(spatialUnitService.findById(8L)).thenReturn(place);
        doNothing().when(projectApiService).assertOrganizationInCallerScope(10L, SCOPE);
        when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), eq(PermissionConstants.ORGANIZATION_MANAGE_PLACES)))
                .thenReturn(true);
        when(institutionService.createOrGetSettingsOf(place.getCreatedByInstitution())).thenReturn(mock(InstitutionSettings.class));

        Document saved = new Document();
        when(documentService.saveFile(any(), any(), any(), any())).thenReturn(saved);
        DocumentResource resource = new DocumentResource();
        when(projectDocumentOpenApiMapper.toResource(saved)).thenReturn(resource);

        DocumentResource result = service.createForPlace(
                caller, 8L, "Title", null, null, null, null, file, "fr");

        assertThat(result).isSameAs(resource);
        verify(documentService).addToSpatialUnit(saved, place);
    }

    @Test
    void createForPlace_notFound_throws404() {
        when(spatialUnitService.findById(8L)).thenThrow(new SpatialUnitNotFoundException("missing"));

        assertThatThrownBy(() -> service.createForPlace(
                caller, 8L, "Title", null, null, null, null, file, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void createForPlace_withoutInstitution_throws400() {
        SpatialUnitDTO place = new SpatialUnitDTO();
        place.setId(8L);
        place.setCreatedByInstitution(null);
        when(spatialUnitService.findById(8L)).thenReturn(place);

        assertThatThrownBy(() -> service.createForPlace(
                caller, 8L, "Title", null, null, null, null, file, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createForPlace_institutionWithoutId_throws400() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(null);
        SpatialUnitDTO place = new SpatialUnitDTO();
        place.setId(8L);
        place.setCreatedByInstitution(institution);
        when(spatialUnitService.findById(8L)).thenReturn(place);

        assertThatThrownBy(() -> service.createForPlace(
                caller, 8L, "Title", null, null, null, null, file, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createForPlace_forbidden_throws403() {
        SpatialUnitDTO place = placeDTO();
        when(spatialUnitService.findById(8L)).thenReturn(place);
        doNothing().when(projectApiService).assertOrganizationInCallerScope(10L, SCOPE);
        when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), eq(PermissionConstants.ORGANIZATION_MANAGE_PLACES)))
                .thenReturn(false);

        assertThatThrownBy(() -> service.createForPlace(
                caller, 8L, "Title", null, null, null, null, file, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void createForPlace_invalidType_throws400() throws Exception {
        SpatialUnitDTO place = placeDTO();
        when(spatialUnitService.findById(8L)).thenReturn(place);
        doNothing().when(projectApiService).assertOrganizationInCallerScope(10L, SCOPE);
        when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), eq(PermissionConstants.ORGANIZATION_MANAGE_PLACES)))
                .thenReturn(true);
        when(institutionService.createOrGetSettingsOf(any())).thenReturn(mock(InstitutionSettings.class));
        when(documentService.saveFile(any(), any(), any(), any()))
                .thenThrow(new InvalidFileTypeException("bad"));

        assertThatThrownBy(() -> service.createForPlace(
                caller, 8L, "Title", null, null, null, null, file, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createForPlace_ioError_throws500() throws Exception {
        SpatialUnitDTO place = placeDTO();
        when(spatialUnitService.findById(8L)).thenReturn(place);
        doNothing().when(projectApiService).assertOrganizationInCallerScope(10L, SCOPE);
        when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), eq(PermissionConstants.ORGANIZATION_MANAGE_PLACES)))
                .thenReturn(true);
        when(institutionService.createOrGetSettingsOf(any())).thenReturn(mock(InstitutionSettings.class));

        MockMultipartFile broken = mock(MockMultipartFile.class);
        when(broken.isEmpty()).thenReturn(false);
        when(broken.getOriginalFilename()).thenReturn("x.pdf");
        when(broken.getContentType()).thenReturn("application/pdf");
        when(broken.getSize()).thenReturn(1L);
        doThrow(new IOException("io")).when(broken).getInputStream();

        assertThatThrownBy(() -> service.createForPlace(
                caller, 8L, "Title", null, null, null, null, broken, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    @Test
    void updateDocument_updatesScaleAndFormat() {
        Document doc = new Document();
        when(documentContentOpenApiService.requireWritableDocument(8L, caller)).thenReturn(doc);
        Concept scale = mock(Concept.class);
        Concept format = mock(Concept.class);
        when(conceptService.findById(4L)).thenReturn(Optional.of(scale));
        when(conceptService.findById(5L)).thenReturn(Optional.of(format));
        when(documentService.save(doc)).thenReturn(doc);
        when(projectDocumentOpenApiMapper.toResource(doc)).thenReturn(new DocumentResource());

        service.updateDocument(caller, 8L, null, null, null, 4L, 5L);

        assertThat(doc.getScale()).isSameAs(scale);
        assertThat(doc.getFormatConcept()).isSameAs(format);
    }

    @Test
    void saveUploadedDocument_success() throws Exception {
        InstitutionDTO institution = institutionDTO();
        when(institutionService.createOrGetSettingsOf(institution)).thenReturn(mock(InstitutionSettings.class));
        Document saved = new Document();
        when(documentService.saveFile(any(), any(), any(), any())).thenReturn(saved);

        Document result = service.saveUploadedDocument(
                new UserInfo(institution, caller.person(), "fr"),
                "Title", null, null, null, null, institution, file);

        assertThat(result).isSameAs(saved);
    }

    @Test
    void saveUploadedDocument_invalidType_throws400() throws Exception {
        InstitutionDTO institution = institutionDTO();
        when(institutionService.createOrGetSettingsOf(institution)).thenReturn(mock(InstitutionSettings.class));
        when(documentService.saveFile(any(), any(), any(), any()))
                .thenThrow(new InvalidFileTypeException("bad"));

        assertThatThrownBy(() -> service.saveUploadedDocument(
                new UserInfo(institution, caller.person(), "fr"),
                "Title", null, null, null, null, institution, file))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void saveUploadedDocument_ioError_throws500() throws Exception {
        InstitutionDTO institution = institutionDTO();
        when(institutionService.createOrGetSettingsOf(institution)).thenReturn(mock(InstitutionSettings.class));

        MockMultipartFile broken = mock(MockMultipartFile.class);
        when(broken.isEmpty()).thenReturn(false);
        when(broken.getOriginalFilename()).thenReturn("x.pdf");
        when(broken.getContentType()).thenReturn("application/pdf");
        when(broken.getSize()).thenReturn(1L);
        doThrow(new IOException("io")).when(broken).getInputStream();

        assertThatThrownBy(() -> service.saveUploadedDocument(
                new UserInfo(institution, caller.person(), "fr"),
                "Title", null, null, null, null, institution, broken))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    @Test
    void requireAccessibleProject_delegates() {
        AccessibleProjectForApi row = new AccessibleProjectForApi(new ActionUnitDTO(), 0L, 0L);
        when(projectApiService.requireAccessibleProject(caller, "5")).thenReturn(row);
        assertThat(service.requireAccessibleProject(caller, "5")).isSameAs(row);
    }

    @Test
    void toResource_delegates() {
        Document doc = new Document();
        DocumentResource resource = new DocumentResource();
        when(projectDocumentOpenApiMapper.toResource(doc)).thenReturn(resource);
        assertThat(service.toResource(doc)).isSameAs(resource);
    }

    @Test
    void requireWritableRecordingUnit_successAndErrors() {
        RecordingUnitDTO ru = new RecordingUnitDTO();
        ru.setCreatedByInstitution(institutionDTO());
        when(recordingUnitService.findAccessibleRecordingUnitByKey("UE-1", SCOPE, null)).thenReturn(ru);
        when(profilePermissionService.hasRecordingUnitWritePermission(any(UserInfo.class), same(ru))).thenReturn(true);
        assertThat(service.requireWritableRecordingUnit(caller, "UE-1", "fr")).isSameAs(ru);

        RecordingUnitDTO noInst = new RecordingUnitDTO();
        noInst.setCreatedByInstitution(null);
        when(recordingUnitService.findAccessibleRecordingUnitByKey("UE-2", SCOPE, null)).thenReturn(noInst);
        assertThatThrownBy(() -> service.requireWritableRecordingUnit(caller, "UE-2", "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        InstitutionDTO noId = new InstitutionDTO();
        noId.setId(null);
        RecordingUnitDTO ruNoId = new RecordingUnitDTO();
        ruNoId.setCreatedByInstitution(noId);
        when(recordingUnitService.findAccessibleRecordingUnitByKey("UE-3", SCOPE, null)).thenReturn(ruNoId);
        assertThatThrownBy(() -> service.requireWritableRecordingUnit(caller, "UE-3", "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        when(profilePermissionService.hasRecordingUnitWritePermission(any(UserInfo.class), same(ru))).thenReturn(false);
        assertThatThrownBy(() -> service.requireWritableRecordingUnit(caller, "UE-1", "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void requireWritableFind_successAndErrors() {
        SpecimenDTO specimen = new SpecimenDTO();
        specimen.setCreatedByInstitution(institutionDTO());
        when(specimenService.findAccessibleByKey("F-1", SCOPE)).thenReturn(Optional.of(specimen));
        when(profilePermissionService.hasSpecimenWritePermission(any(UserInfo.class), same(specimen))).thenReturn(true);
        assertThat(service.requireWritableFind(caller, "F-1", "fr")).isSameAs(specimen);

        when(specimenService.findAccessibleByKey("missing", SCOPE)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.requireWritableFind(caller, "missing", "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        SpecimenDTO noInst = new SpecimenDTO();
        noInst.setCreatedByInstitution(null);
        when(specimenService.findAccessibleByKey("F-2", SCOPE)).thenReturn(Optional.of(noInst));
        assertThatThrownBy(() -> service.requireWritableFind(caller, "F-2", "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        InstitutionDTO noId = new InstitutionDTO();
        noId.setId(null);
        SpecimenDTO findNoId = new SpecimenDTO();
        findNoId.setCreatedByInstitution(noId);
        when(specimenService.findAccessibleByKey("F-3", SCOPE)).thenReturn(Optional.of(findNoId));
        assertThatThrownBy(() -> service.requireWritableFind(caller, "F-3", "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        when(profilePermissionService.hasSpecimenWritePermission(any(UserInfo.class), same(specimen))).thenReturn(false);
        assertThatThrownBy(() -> service.requireWritableFind(caller, "F-1", "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void requireWritablePlace_successAndForbidden() {
        SpatialUnitDTO place = placeDTO();
        when(spatialUnitService.findById(8L)).thenReturn(place);
        doNothing().when(projectApiService).assertOrganizationInCallerScope(10L, SCOPE);
        when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), eq(PermissionConstants.ORGANIZATION_MANAGE_PLACES)))
                .thenReturn(true);
        assertThat(service.requireWritablePlace(caller, 8L, "fr")).isSameAs(place);

        when(profilePermissionService.hasOrganizationPermission(any(UserInfo.class), eq(PermissionConstants.ORGANIZATION_MANAGE_PLACES)))
                .thenReturn(false);
        assertThatThrownBy(() -> service.requireWritablePlace(caller, 8L, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void linkIfAbsent_and_existsByHash_coverAllTargetTypes() {
        Document document = new Document();
        document.setMd5Sum("hash");

        ActionUnitDTO project = new ActionUnitDTO();
        when(documentService.existInActionUnitByHash(project, "hash")).thenReturn(false);
        assertThat(service.linkIfAbsent(document, project)).isTrue();
        verify(documentService).addToActionUnit(document, project);
        when(documentService.existInActionUnitByHash(project, "hash")).thenReturn(true);
        assertThat(service.linkIfAbsent(document, project)).isFalse();
        assertThat(service.existsByHash(project, "hash")).isTrue();
        when(documentService.existInActionUnitByHash(project, "hash")).thenReturn(false);
        assertThat(service.existsByHash(project, "hash")).isFalse();
        assertThat(service.existsByHash(project, null)).isFalse();

        RecordingUnitDTO ru = new RecordingUnitDTO();
        when(documentService.existInRecordingUnitByHash(ru, "hash")).thenReturn(false);
        assertThat(service.linkIfAbsent(document, ru)).isTrue();
        verify(documentService).addToRecordingUnit(document, ru);
        when(documentService.existInRecordingUnitByHash(ru, "hash")).thenReturn(true);
        assertThat(service.linkIfAbsent(document, ru)).isFalse();
        assertThat(service.existsByHash(ru, "hash")).isTrue();
        when(documentService.existInRecordingUnitByHash(ru, "hash")).thenReturn(false);
        assertThat(service.existsByHash(ru, "hash")).isFalse();
        assertThat(service.existsByHash(ru, null)).isFalse();

        SpecimenDTO specimen = new SpecimenDTO();
        when(documentService.existInSpecimenByHash(specimen, "hash")).thenReturn(false);
        assertThat(service.linkIfAbsent(document, specimen)).isTrue();
        verify(documentService).addToSpecimen(document, specimen);
        when(documentService.existInSpecimenByHash(specimen, "hash")).thenReturn(true);
        assertThat(service.linkIfAbsent(document, specimen)).isFalse();
        assertThat(service.existsByHash(specimen, "hash")).isTrue();
        when(documentService.existInSpecimenByHash(specimen, "hash")).thenReturn(false);
        assertThat(service.existsByHash(specimen, "hash")).isFalse();
        assertThat(service.existsByHash(specimen, null)).isFalse();

        SpatialUnitDTO place = new SpatialUnitDTO();
        when(documentService.existInSpatialUnitByHash(place, "hash")).thenReturn(false);
        assertThat(service.linkIfAbsent(document, place)).isTrue();
        verify(documentService).addToSpatialUnit(document, place);
        when(documentService.existInSpatialUnitByHash(place, "hash")).thenReturn(true);
        assertThat(service.linkIfAbsent(document, place)).isFalse();
        assertThat(service.existsByHash(place, "hash")).isTrue();
        when(documentService.existInSpatialUnitByHash(place, "hash")).thenReturn(false);
        assertThat(service.existsByHash(place, "hash")).isFalse();
        assertThat(service.existsByHash(place, null)).isFalse();

        Document withoutHash = new Document();
        assertThat(service.linkIfAbsent(withoutHash, project)).isTrue();
        assertThat(service.linkIfAbsent(withoutHash, ru)).isTrue();
        assertThat(service.linkIfAbsent(withoutHash, specimen)).isTrue();
        assertThat(service.linkIfAbsent(withoutHash, place)).isTrue();
    }

    @Test
    void createForProject_withConcepts_resolvesThem() throws Exception {
        InstitutionDTO institution = institutionDTO();
        ActionUnitDTO project = new ActionUnitDTO();
        project.setCreatedByInstitution(institution);
        when(projectApiService.requireAccessibleProject(caller, "5"))
                .thenReturn(new AccessibleProjectForApi(project, 0L, 0L));
        when(institutionService.createOrGetSettingsOf(institution)).thenReturn(mock(InstitutionSettings.class));
        Concept nature = mock(Concept.class);
        Concept scale = mock(Concept.class);
        Concept format = mock(Concept.class);
        when(conceptService.findById(1L)).thenReturn(Optional.of(nature));
        when(conceptService.findById(2L)).thenReturn(Optional.of(scale));
        when(conceptService.findById(3L)).thenReturn(Optional.of(format));
        Document saved = new Document();
        when(documentService.saveFile(any(), any(), any(), any())).thenReturn(saved);
        when(projectDocumentOpenApiMapper.toResource(saved)).thenReturn(new DocumentResource());

        service.createForProject(caller, "5", "Title", " Desc ", 1L, 2L, 3L, file, "fr");

        verify(conceptService).findById(1L);
        verify(conceptService).findById(2L);
        verify(conceptService).findById(3L);
    }

    private static InstitutionDTO institutionDTO() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(10L);
        return institution;
    }

    private static SpatialUnitDTO placeDTO() {
        SpatialUnitDTO place = new SpatialUnitDTO();
        place.setId(8L);
        place.setCreatedByInstitution(institutionDTO());
        return place;
    }
}
