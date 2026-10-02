package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerTextViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.services.form.EffectiveFormResolver;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.document.DocumentService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.DocumentDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.ui.api.openapi.v1.mapper.DocumentOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.request.document.DocumentCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.document.DocumentPatchRequest;
import fr.siamois.ui.api.openapi.v1.resource.form.AnswerInput;
import fr.siamois.ui.api.openapi.v1.resource.document.DocumentResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentOpenApiServiceTest {

    @Mock
    private DocumentService documentService;
    @Mock
    private ActionUnitService actionUnitService;
    @Mock
    private ConceptService conceptService;
    @Mock
    private ConceptMapper conceptMapper;
    @Mock
    private ProfilePermissionService profilePermissionService;
    @Mock
    private DocumentOpenApiMapper documentOpenApiMapper;
    @Mock
    private DocumentListProjectionService documentListProjectionService;

    @Mock
    private FieldAnswerPatchService fieldAnswerPatchService;
    @Mock
    private EffectiveFormResolver effectiveFormResolver;
    @Mock
    private FieldAnswerWireService fieldAnswerWireService;
    @Mock
    private fr.siamois.domain.services.form.CustomFieldAnswerService customFieldAnswerService;

    private DocumentOpenApiService service;

    private PersonDTO personDto;
    private InstitutionDTO institution;

    @Mock
    private fr.siamois.domain.services.ValidationStatusService validationStatusService;

    @BeforeEach
    void setUp() {
        service = new DocumentOpenApiService(documentService, actionUnitService, conceptService, conceptMapper,
                profilePermissionService, documentOpenApiMapper, documentListProjectionService, ListQueryStubs.multiValueAnswers(), mock(ResourceBookmarkService.class), mock(EntitySiblingsService.class), new ValidationOpenApiService(validationStatusService),
                fieldAnswerPatchService, fieldAnswerWireService, customFieldAnswerService, effectiveFormResolver);

        lenient().when(fieldAnswerWireService.additionalAnswers(any(), any())).thenReturn(Map.of());
        personDto = new PersonDTO();
        personDto.setId(1L);
        institution = new InstitutionDTO();
        institution.setId(10L);

        lenient().when(documentListProjectionService.buildOne(any(), any()))
                .thenReturn(new DocumentListProjectionService.DocumentListProjection(Map.of(), Map.of()));
        lenient().when(documentOpenApiMapper.toResource(any(), any(), any())).thenAnswer(inv -> {
            DocumentDTO dto = inv.getArgument(0);
            DocumentResource r = new DocumentResource();
            r.setResourceType("documents");
            r.setId(dto.getId() != null ? String.valueOf(dto.getId()) : null);
            return r;
        });
    }

    private DocumentDTO documentOn(ActionUnitDTO au) {
        DocumentDTO document = new DocumentDTO();
        document.setId(5L);
        document.setActionUnit(new ActionUnitSummaryDTO(au));
        return document;
    }

    private ActionUnitDTO projectWithInstitution() {
        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(7L);
        au.setCreatedByInstitution(institution);
        return au;
    }

    // --- getDocumentById ---

    @Test
    void getDocumentById_notFound_throws404() {
        when(documentService.findDtoById(5L)).thenReturn(null);

        var arg3 = Set.of(10L);
        assertThatThrownBy(() -> service.getDocumentById(5L, personDto, arg3, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getDocumentById_outsideInstitutionScope_throws404() {
        DocumentDTO document = documentOn(projectWithInstitution());
        when(documentService.findDtoById(5L)).thenReturn(document);

        var arg3 = Set.of(999L);
        assertThatThrownBy(() -> service.getDocumentById(5L, personDto, arg3, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getDocumentById_cannotViewProject_throws404() {
        DocumentDTO document = documentOn(projectWithInstitution());
        when(documentService.findDtoById(5L)).thenReturn(document);
        when(profilePermissionService.canViewProject(personDto, institution, 7L)).thenReturn(false);

        var arg3 = Set.of(10L);
        assertThatThrownBy(() -> service.getDocumentById(5L, personDto, arg3, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getDocumentById_setsPermissionsFromWriteCheck() {
        DocumentDTO document = documentOn(projectWithInstitution());
        when(documentService.findDtoById(5L)).thenReturn(document);
        when(profilePermissionService.canViewProject(personDto, institution, 7L)).thenReturn(true);
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(7L),
                eq(PermissionConstants.INSTANCE_EDIT_DOCUMENTS), eq(PermissionConstants.ORGANIZATION_EDIT_DOCUMENTS),
                eq(PermissionConstants.PROJECT_EDIT_DOCUMENTS)))
                .thenReturn(true);

        DocumentResource result = service.getDocumentById(5L, personDto, Set.of(10L), "fr");

        assertThat(result.getPermissions().canEdit()).isTrue();
    }

    // --- createDocument ---

    @Test
    void createDocument_blankProjectId_throws400() {
        DocumentCreateRequest req = new DocumentCreateRequest();
        req.setProjectId(" ");
        req.setCategoryId("2");

        var arg3 = Set.of(10L);
        assertThatThrownBy(() -> service.createDocument(req, personDto, arg3, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createDocument_withoutWritePermission_throws403() {
        ActionUnitDTO au = projectWithInstitution();
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", Set.of(10L))).thenReturn(row);
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(7L),
                eq(PermissionConstants.INSTANCE_EDIT_DOCUMENTS), eq(PermissionConstants.ORGANIZATION_EDIT_DOCUMENTS),
                eq(PermissionConstants.PROJECT_EDIT_DOCUMENTS)))
                .thenReturn(false);

        DocumentCreateRequest req = new DocumentCreateRequest();
        req.setProjectId("7");
        req.setCategoryId("2");

        var arg3 = Set.of(10L);
        assertThatThrownBy(() -> service.createDocument(req, personDto, arg3, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verifyNoInteractions(documentService);
    }

    @Test
    void createDocument_unknownType_throws404() {
        ActionUnitDTO au = projectWithInstitution();
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", Set.of(10L))).thenReturn(row);
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(7L), any(), any(), any()))
                .thenReturn(true);
        when(conceptService.findById(2L)).thenReturn(java.util.Optional.empty());

        DocumentCreateRequest req = new DocumentCreateRequest();
        req.setProjectId("7");
        req.setCategoryId("2");

        var arg3 = Set.of(10L);
        assertThatThrownBy(() -> service.createDocument(req, personDto, arg3, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void createDocument_success_savesWithActionUnitAndType() {
        ActionUnitDTO au = projectWithInstitution();
        AccessibleProjectForApi row = new AccessibleProjectForApi(au, 0L, 0L);
        when(actionUnitService.findAccessibleProjectByKey("7", Set.of(10L))).thenReturn(row);
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(7L), any(), any(), any()))
                .thenReturn(true);

        Concept typeConcept = new Concept();
        typeConcept.setId(2L);
        when(conceptService.findById(2L)).thenReturn(java.util.Optional.of(typeConcept));
        ConceptDTO typeDto = new ConceptDTO();
        typeDto.setId(2L);
        when(conceptMapper.convert(typeConcept)).thenReturn(typeDto);

        DocumentDTO saved = documentOn(au);
        when(documentService.save(any(DocumentDTO.class))).thenReturn(saved);

        DocumentCreateRequest req = new DocumentCreateRequest();
        req.setProjectId("7");
        req.setCategoryId("2");
        req.setTitle("Document 1");

        DocumentResource result = service.createDocument(req, personDto, Set.of(10L), "fr");

        ArgumentCaptor<DocumentDTO> captor = ArgumentCaptor.forClass(DocumentDTO.class);
        verify(documentService).save(captor.capture());
        assertThat(captor.getValue().getActionUnit().getId()).isEqualTo(7L);
        assertThat(captor.getValue().getCategory()).isEqualTo(typeDto);
        assertThat(captor.getValue().getTitle()).isEqualTo("Document 1");
        // TraceableEntity's NOT NULL author and organization: the caller, in the project's organization.
        assertThat(captor.getValue().getCreatedBy()).isSameAs(personDto);
        assertThat(captor.getValue().getCreatedByInstitution()).isSameAs(institution);
        assertThat(result.getId()).isEqualTo("5");
    }

    // --- patchDocument ---

    @Test
    void patchDocument_withoutWritePermission_throws403() {
        DocumentDTO document = documentOn(projectWithInstitution());
        when(documentService.findDtoById(5L)).thenReturn(document);
        when(profilePermissionService.canViewProject(personDto, institution, 7L)).thenReturn(true);
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(7L),
                eq(PermissionConstants.INSTANCE_EDIT_DOCUMENTS), eq(PermissionConstants.ORGANIZATION_EDIT_DOCUMENTS),
                eq(PermissionConstants.PROJECT_EDIT_DOCUMENTS)))
                .thenReturn(false);

        var arg2 = new DocumentPatchRequest();
        var arg4 = Set.of(10L);
        assertThatThrownBy(() -> service.patchDocument(5L, arg2, personDto, arg4, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(documentService, never()).save(any(DocumentDTO.class));
    }

    // `validated` rides the same PATCH: the edit right covers en cours/terminé/annulé, the validator
    // right covers validé — alone, a validator without the edit right may change the status only.
    @Test
    void patchDocument_validatorWithoutEditRight_mayChangeOnlyTheStatus() {
        DocumentDTO document = documentOn(projectWithInstitution());
        when(documentService.findDtoById(5L)).thenReturn(document);
        when(profilePermissionService.canViewProject(personDto, institution, 7L)).thenReturn(true);
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(7L),
                eq(PermissionConstants.INSTANCE_EDIT_DOCUMENTS), eq(PermissionConstants.ORGANIZATION_EDIT_DOCUMENTS),
                eq(PermissionConstants.PROJECT_EDIT_DOCUMENTS))).thenReturn(false);
        when(profilePermissionService.hasValidatePermission(any(UserInfo.class), eq(7L))).thenReturn(true);

        DocumentPatchRequest statusOnly = new DocumentPatchRequest();
        statusOnly.setValidated(fr.siamois.domain.models.ValidationStatus.VALIDATED);
        service.patchDocument(5L, statusOnly, personDto, Set.of(10L), "fr");

        verify(validationStatusService).setStatus(fr.siamois.domain.models.document.Document.class, 5L,
                fr.siamois.domain.models.ValidationStatus.VALIDATED, personDto.getId());
        verify(documentService, never()).save(any(DocumentDTO.class));

        DocumentPatchRequest withAnswers = new DocumentPatchRequest();
        withAnswers.setValidated(fr.siamois.domain.models.ValidationStatus.VALIDATED);
        withAnswers.setAnswers(Map.of("-503", new AnswerInput("Titre", null)));
        var arg4 = Set.of(10L);
        assertThatThrownBy(() -> service.patchDocument(5L, withAnswers, personDto, arg4, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void patchDocument_editorWithoutValidatorRight_cannotValidate() {
        DocumentDTO document = documentOn(projectWithInstitution());
        when(documentService.findDtoById(5L)).thenReturn(document);
        when(profilePermissionService.canViewProject(personDto, institution, 7L)).thenReturn(true);
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(7L), any(), any(), any())).thenReturn(true);
        when(profilePermissionService.hasValidatePermission(any(UserInfo.class), eq(7L))).thenReturn(false);

        DocumentPatchRequest req = new DocumentPatchRequest();
        req.setValidated(fr.siamois.domain.models.ValidationStatus.VALIDATED);

        var arg4 = Set.of(10L);
        assertThatThrownBy(() -> service.patchDocument(5L, req, personDto, arg4, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(validationStatusService, never()).setStatus(any(), anyLong(), any(), any());
    }


    @Test
    void patchDocument_appliesAnswersOnTheTypesEffectiveFormAndSavesTheAdditionalOnes() {
        DocumentDTO document = documentOn(projectWithInstitution());
        ConceptDTO type = new ConceptDTO();
        type.setId(40L);
        document.setCategory(type);
        when(documentService.findDtoById(5L)).thenReturn(document);
        when(profilePermissionService.canViewProject(personDto, institution, 7L)).thenReturn(true);
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(7L), any(), any(), any()))
                .thenReturn(true);
        FormUiDto effectiveForm = new FormUiDto();
        when(effectiveFormResolver.resolveEffectiveForm(7L,
                ConfigurableTable.DOCUMENT, 40L)).thenReturn(effectiveForm);
        Map<CustomField, CustomFieldAnswerViewModel> additional = Map.of(new CustomFieldText(), new CustomFieldAnswerTextViewModel());
        Map<String, AnswerInput> answers = Map.of("-503", new AnswerInput("Nouveau titre", null));
        when(fieldAnswerPatchService.apply(document, effectiveForm, answers, 7L)).thenReturn(additional);

        DocumentPatchRequest req = new DocumentPatchRequest();
        req.setAnswers(answers);
        service.patchDocument(5L, req, personDto, Set.of(10L), "fr");

        verify(documentService).save(document, additional);
    }

    // --- siblings, access check

    @Test
    void findSiblings_isScopedToTheProjectOfTheDocument() {
        DocumentDTO document = documentOn(projectWithInstitution());
        when(documentService.findDtoById(5L)).thenReturn(document);
        when(profilePermissionService.canViewProject(personDto, institution, 7L)).thenReturn(true);
        fr.siamois.ui.api.openapi.v1.resource.sibling.SiblingsResource siblings =
                new fr.siamois.ui.api.openapi.v1.resource.sibling.SiblingsResource(null, null);
        EntitySiblingsService siblingsService = mock(EntitySiblingsService.class);
        when(siblingsService.findSiblings(EntitySiblingsService.Kind.DOCUMENT, 7L, 5L)).thenReturn(siblings);
        DocumentOpenApiService withSiblings = new DocumentOpenApiService(documentService, actionUnitService, conceptService,
                conceptMapper, profilePermissionService, documentOpenApiMapper, documentListProjectionService,
                ListQueryStubs.multiValueAnswers(), mock(ResourceBookmarkService.class), siblingsService,
                new ValidationOpenApiService(validationStatusService), fieldAnswerPatchService, fieldAnswerWireService,
                customFieldAnswerService, effectiveFormResolver);

        assertThat(withSiblings.findSiblings(5L, personDto, Set.of(10L))).isSameAs(siblings);
    }

    @Test
    void requireAccessible_refusesADocumentWithoutProject() {
        DocumentDTO orphan = new DocumentDTO();
        orphan.setId(9L);
        when(documentService.findDtoById(9L)).thenReturn(orphan);
        var scope = Set.of(10L);

        assertThatThrownBy(() -> service.requireAccessible(9L, personDto, scope))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
