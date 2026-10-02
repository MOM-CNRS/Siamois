package fr.siamois.domain.services.document;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.exceptions.permission.ForbiddenOperationException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.services.form.CustomFieldAnswerService;
import fr.siamois.domain.services.identifier.EntityIdentifierGenerator;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.DocumentDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.DocumentRepository;
import fr.siamois.infrastructure.files.DocumentStorage;
import fr.siamois.mapper.DocumentMapper;
import fr.siamois.mapper.InstitutionMapper;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.utils.context.ExecutionContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The document as an entity of the configurable tables: identifier, mandatory project, permission. */
@ExtendWith(MockitoExtension.class)
class DocumentServiceSaveTest {

    private static final long PROJECT_ID = 7L;

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private PersonMapper personMapper;
    @Mock
    private InstitutionMapper institutionMapper;
    @Mock
    private DocumentMapper documentMapper;
    @Mock
    private EntityIdentifierGenerator identifierGenerator;
    @Mock
    private ProfilePermissionService profilePermissionService;
    @Mock
    private CustomFieldAnswerService customFieldAnswerService;
    @Mock
    private TableFieldConfigService tableFieldConfigService;
    @Mock
    private DocumentStorage documentStorage;

    private DocumentService service;
    private DocumentDTO dto;
    private Document entity;
    private ActionUnit project;

    @BeforeEach
    void setUp() {
        service = new DocumentService(documentRepository, personMapper, institutionMapper, documentMapper,
                identifierGenerator, profilePermissionService, customFieldAnswerService, tableFieldConfigService,
                documentStorage, List.of());
        ExecutionContextHolder.set(new UserInfo(new InstitutionDTO(), new PersonDTO(), "fr"));

        project = new ActionUnit();
        project.setId(PROJECT_ID);
        entity = new Document();
        entity.setActionUnit(project);
        entity.setIdentifier("DOC0001");

        dto = new DocumentDTO();
        ActionUnitSummaryDTO projectDto = new ActionUnitSummaryDTO();
        projectDto.setId(PROJECT_ID);
        dto.setActionUnit(projectDto);

        lenient().when(documentMapper.invertConvert(dto)).thenReturn(entity);
        lenient().when(documentMapper.convert(any(Document.class))).thenReturn(dto);
        lenient().when(documentRepository.save(any(Document.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(personMapper.invertConvert(any(PersonDTO.class))).thenReturn(null);
        lenient().when(institutionMapper.invertConvert(any(InstitutionDTO.class))).thenReturn(new Institution());
    }

    @AfterEach
    void tearDown() {
        ExecutionContextHolder.clear();
    }

    private void allowEditing(boolean allowed) {
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(PROJECT_ID),
                eq(PermissionConstants.INSTANCE_EDIT_DOCUMENTS),
                eq(PermissionConstants.ORGANIZATION_EDIT_DOCUMENTS),
                eq(PermissionConstants.PROJECT_EDIT_DOCUMENTS))).thenReturn(allowed);
    }

    @Test
    void save_newDocument_generatesItsIdentifierAndSaves() {
        allowEditing(true);
        entity.setIdentifier(null);
        when(tableFieldConfigService.isTypeFieldConfigured(PROJECT_ID, ConfigurableTable.DOCUMENT)).thenReturn(true);
        // the generator is a mock: it stands in for the project's format
        when(identifierGenerator.generateIdentifierIfRequired(any(Document.class), any())).thenAnswer(i -> {
            ((Document) i.getArgument(0)).setIdentifier("DOC0001");
            return Optional.empty();
        });

        service.save(dto);

        verify(identifierGenerator).generateIdentifierIfRequired(any(Document.class), any());
        verify(documentRepository).save(entity);
        assertThat(entity.getIdentifier()).isEqualTo("DOC0001");
    }

    @Test
    void save_newDocument_withAnIdentifier_keepsItWithoutGenerating() {
        allowEditing(true);

        service.save(dto);

        verify(identifierGenerator, never()).generateIdentifierIfRequired(any(Document.class), any());
        verify(documentRepository).save(entity);
    }

    @Test
    void save_newDocument_whenTheCategoryFieldIsNotConfigured_fallsBackToDocId() {
        allowEditing(true);
        entity.setIdentifier(null);
        when(tableFieldConfigService.isTypeFieldConfigured(PROJECT_ID, ConfigurableTable.DOCUMENT)).thenReturn(false);
        when(documentRepository.save(any(Document.class))).thenAnswer(i -> {
            Document d = i.getArgument(0);
            if (d.getId() == null) d.setId(41L);
            return d;
        });

        service.save(dto);

        verify(identifierGenerator, never()).generateIdentifierIfRequired(any(Document.class), any());
        assertThat(entity.getIdentifier()).isEqualTo("DOC-41");
    }

    @Test
    void save_withoutProject_isRefused() {
        entity.setActionUnit(null);

        assertThatThrownBy(() -> service.save(dto)).isInstanceOf(IllegalArgumentException.class);
        verify(documentRepository, never()).save(any());
    }

    @Test
    void save_withoutPermissionOnTheProject_isForbidden() {
        allowEditing(false);

        assertThatThrownBy(() -> service.save(dto)).isInstanceOf(ForbiddenOperationException.class);
        verify(documentRepository, never()).save(any());
    }

    @Test
    void save_identifierAlreadyUsedInTheProject_isRefused() {
        allowEditing(true);
        Document other = new Document();
        other.setId(99L);
        when(documentRepository.findByIdentifierAndActionUnitId("DOC0001", PROJECT_ID)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.save(dto)).isInstanceOf(IllegalStateException.class);
        verify(documentRepository, never()).save(any());
    }

    @Test
    void save_existingDocument_keepsTheStoredFileColumns() {
        allowEditing(true);
        entity.setId(5L);
        Document managed = new Document();
        managed.setId(5L);
        managed.setActionUnit(project);
        managed.setFileCode("abcdefghij");
        managed.setMd5Sum("md5");
        managed.setTitle("old");
        entity.setTitle("new");
        when(documentRepository.findById(5L)).thenReturn(Optional.of(managed));

        service.save(dto);

        assertThat(managed.getTitle()).isEqualTo("new");
        assertThat(managed.getFileCode()).isEqualTo("abcdefghij");
        assertThat(managed.getMd5Sum()).isEqualTo("md5");
        verify(documentRepository).save(managed);
    }

    @Test
    void save_existingDocument_replacesItsLinks() {
        allowEditing(true);
        entity.setId(5L);
        var ru = new fr.siamois.domain.models.recordingunit.RecordingUnit();
        ru.setId(3L);
        entity.getRecordingUnits().add(ru);
        Document managed = new Document();
        managed.setId(5L);
        managed.setActionUnit(project);
        var stale = new fr.siamois.domain.models.recordingunit.RecordingUnit();
        stale.setId(1L);
        managed.getRecordingUnits().add(stale);
        when(documentRepository.findById(5L)).thenReturn(Optional.of(managed));

        service.save(dto);

        assertThat(managed.getRecordingUnits()).extracting("id").containsExactly(3L);
    }

    // ---- search, counts, lookups

    @Test
    void searchDocuments_mapsThePageOfTheRepository() {
        fr.siamois.dto.FilterDTO filters = new fr.siamois.dto.FilterDTO(false);
        filters.add(fr.siamois.infrastructure.database.repositories.specs.DocumentSpec.IDENTIFIER_FILTER, "DOC",
                fr.siamois.dto.FilterDTO.FilterType.CONTAINS);
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        when(documentRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), eq(pageable)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(entity)));
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(1L);

        var page = service.searchDocuments(institution, filters, pageable);

        assertThat(page.getContent()).containsExactly(dto);
    }

    @Test
    void searchDocuments_withGlobalAndProjectFilters_stillQueriesTheRepository() {
        fr.siamois.dto.FilterDTO filters = new fr.siamois.dto.FilterDTO(false);
        filters.add(fr.siamois.infrastructure.database.repositories.specs.DocumentSpec.GLOBAL_FILTER, "plan",
                fr.siamois.dto.FilterDTO.FilterType.CONTAINS);
        filters.add(fr.siamois.infrastructure.database.repositories.specs.DocumentSpec.ACTION_UNIT_FILTER, List.of(PROJECT_ID),
                fr.siamois.dto.FilterDTO.FilterType.EQUAL);
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        when(documentRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), eq(pageable)))
                .thenReturn(org.springframework.data.domain.Page.empty());
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(1L);

        assertThat(service.searchDocuments(institution, filters, pageable).getContent()).isEmpty();
    }

    @Test
    void countSearchResults_andCountByActionContext_delegateToTheRepository() {
        when(documentRepository.count(any(org.springframework.data.jpa.domain.Specification.class))).thenReturn(4L);
        when(documentRepository.countByActionUnitId(PROJECT_ID)).thenReturn(3);
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(1L);
        fr.siamois.dto.entity.ActionUnitDTO projectDto = new fr.siamois.dto.entity.ActionUnitDTO();
        projectDto.setId(PROJECT_ID);

        assertThat(service.countSearchResults(institution, new fr.siamois.dto.FilterDTO(false))).isEqualTo(4);
        assertThat(service.countByActionContext(projectDto)).isEqualTo(3);
    }

    @Test
    void identifierAlreadyExistInProject_isTrueOnlyForAnotherDocument() {
        dto.setIdentifier("DOC0001");
        dto.setId(5L);
        Document other = new Document();
        other.setId(6L);
        Document same = new Document();
        same.setId(5L);

        when(documentRepository.findByIdentifierAndActionUnitId("DOC0001", PROJECT_ID)).thenReturn(Optional.of(other));
        assertThat(service.identifierAlreadyExistInProject(dto)).isTrue();

        when(documentRepository.findByIdentifierAndActionUnitId("DOC0001", PROJECT_ID)).thenReturn(Optional.of(same));
        assertThat(service.identifierAlreadyExistInProject(dto)).isFalse();

        dto.setActionUnit(null);
        assertThat(service.identifierAlreadyExistInProject(dto)).isFalse();
    }

    @Test
    void findDtoById_mapsTheDocument_orReturnsNull() {
        when(documentRepository.findById(5L)).thenReturn(Optional.of(entity));
        when(documentRepository.findById(6L)).thenReturn(Optional.empty());

        assertThat(service.findDtoById(5L)).isSameAs(dto);
        assertThat(service.findDtoById(6L)).isNull();
        assertThat(service.findDtoById(null)).isNull();
    }

    @Test
    void save_abstractEntityDto_savesADocumentDto() {
        allowEditing(true);

        assertThat(service.save((fr.siamois.dto.entity.AbstractEntityDTO) dto)).isSameAs(dto);
    }

    @Test
    void save_withAdditionalAnswers_savesThemForTheSavedDocument() {
        allowEditing(true);
        java.util.Map<fr.siamois.domain.models.form.customfield.CustomField,
                fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel> answers = java.util.Map.of();

        service.save(dto, answers);

        verify(customFieldAnswerService).saveAdditionalFieldAnswers(dto, answers);
    }

    // ---- creation through the file upload (mobile)

    private UserInfo uploader() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(1L);
        return new UserInfo(institution, new PersonDTO(), "fr");
    }

    private Document uploadedDocument() {
        Document document = new Document();
        document.setActionUnit(project);
        document.setFileName("plan.pdf");
        document.setMimeType("application/pdf");
        document.setSize(10L);
        return document;
    }

    private void storageAccepts() {
        when(documentStorage.supportedMimeTypes()).thenReturn(List.of(org.springframework.util.MimeType.valueOf("application/pdf")));
        when(documentStorage.getMaxUploadSize()).thenReturn("10MB");
    }

    @Test
    void saveFile_ofADocumentOfAProject_generatesItsIdentifierWhenTheCategoryIsConfigured() throws Exception {
        storageAccepts();
        Document document = uploadedDocument();
        when(tableFieldConfigService.isTypeFieldConfigured(PROJECT_ID, ConfigurableTable.DOCUMENT)).thenReturn(true);

        service.saveFile(uploader(), document, new java.io.ByteArrayInputStream("x".getBytes()), "/ctx");

        verify(identifierGenerator).generateIdentifierIfRequired(eq(document), any());
    }

    @Test
    void saveFile_ofADocumentOfAProject_fallsBackToDocIdWhenTheCategoryIsNotConfigured() throws Exception {
        storageAccepts();
        Document document = uploadedDocument();
        when(tableFieldConfigService.isTypeFieldConfigured(PROJECT_ID, ConfigurableTable.DOCUMENT)).thenReturn(false);
        when(documentRepository.save(any(Document.class))).thenAnswer(i -> {
            Document d = i.getArgument(0);
            if (d.getId() == null) d.setId(77L);
            return d;
        });

        Document saved = service.saveFile(uploader(), document, new java.io.ByteArrayInputStream("x".getBytes()), "/ctx");

        assertThat(saved.getIdentifier()).isEqualTo("DOC-77");
        verify(identifierGenerator, never()).generateIdentifierIfRequired(any(Document.class), any());
    }

    @Test
    void saveWithoutFile_ofADocumentOfAProject_getsAnIdentifier() {
        Document document = uploadedDocument();
        when(tableFieldConfigService.isTypeFieldConfigured(PROJECT_ID, ConfigurableTable.DOCUMENT)).thenReturn(false);
        when(documentRepository.save(any(Document.class))).thenAnswer(i -> {
            Document d = i.getArgument(0);
            if (d.getId() == null) d.setId(78L);
            return d;
        });

        assertThat(service.saveWithoutFile(uploader(), document).getIdentifier()).isEqualTo("DOC-78");
    }

    // --- replacing and removing the stored file

    private Document documentWithAFile() {
        Document document = new Document();
        document.setId(5L);
        document.setActionUnit(project);
        document.setIdentifier("DOC0005");
        document.setFileName("old.pdf");
        document.setMimeType("application/pdf");
        document.setFileCode("OLDCODE");
        document.setStoredFileName("OLDCODE.pdf");
        return document;
    }

    @Test
    void replaceFile_swapsTheStoredFileAndFillsInSizeAndFormat() throws Exception {
        storageAccepts();
        Document document = documentWithAFile();

        Document saved = service.replaceFile(uploader(), document, "plan.pdf", "application/pdf", 2_097_152L,
                new java.io.ByteArrayInputStream("abc".getBytes()), "/ctx");

        verify(documentStorage).deleteStoredFile(document);
        verify(documentStorage).save(any(UserInfo.class), eq(document), any());
        assertThat(saved.getFileName()).isEqualTo("plan.pdf");
        assertThat(saved.getFileCode()).isNotEqualTo("OLDCODE");
        assertThat(saved.getUrl()).startsWith("/ctx/content/");
        assertThat(saved.getSizeMb()).isEqualByComparingTo("2");
        assertThat(saved.getFormat()).isEqualTo("pdf");
        assertThat(saved.getMd5Sum()).isNotBlank();
    }

    @Test
    void replaceFile_keepsAFormatThatWasTyped() throws Exception {
        storageAccepts();
        Document document = documentWithAFile();
        document.setFormat("PDF/A");

        service.replaceFile(uploader(), document, "plan.pdf", "application/pdf", 10L,
                new java.io.ByteArrayInputStream("abc".getBytes()), "/ctx");

        assertThat(document.getFormat()).isEqualTo("PDF/A");
    }

    @Test
    void replaceFile_ofADocumentWithoutFile_hasNothingToDeleteFirst() throws Exception {
        storageAccepts();
        Document document = documentWithAFile();
        document.clearFile();

        service.replaceFile(uploader(), document, "plan.pdf", "application/pdf", 10L,
                new java.io.ByteArrayInputStream("abc".getBytes()), "/ctx");

        verify(documentStorage, never()).deleteStoredFile(any());
        assertThat(document.hasFile()).isTrue();
    }

    @Test
    void replaceFile_aRefusedFileLeavesTheCurrentOneAlone() {
        storageAccepts();
        Document document = documentWithAFile();
        var user = uploader();
        var content = new java.io.ByteArrayInputStream("abc".getBytes());

        assertThatThrownBy(() -> service.replaceFile(user, document, "big.pdf", "application/pdf", 50_000_000L, content, "/ctx"))
                .isInstanceOf(fr.siamois.domain.models.exceptions.InvalidFileSizeException.class);

        verify(documentStorage, never()).deleteStoredFile(any());
        assertThat(document.getFileCode()).isEqualTo("OLDCODE");
    }

    @Test
    void removeFile_deletesTheBytesAndForgetsTheMetadata_butKeepsTheExternalUrl() {
        Document document = documentWithAFile();
        document.setExternalUrl("https://exemple.org");

        Document saved = service.removeFile(document);

        verify(documentStorage).deleteStoredFile(document);
        assertThat(saved.hasFile()).isFalse();
        assertThat(saved.getFileName()).isNull();
        assertThat(saved.getExternalUrl()).isEqualTo("https://exemple.org");
    }

    @Test
    void removeFile_ofADocumentWithoutFile_justSaves() {
        Document document = documentWithAFile();
        document.clearFile();

        service.removeFile(document);

        verify(documentStorage, never()).deleteStoredFile(any());
        verify(documentRepository).save(document);
    }

    // --- the documents of one entity

    @Test
    void searchDocumentsLinkedTo_searchesTheInstitutionsDocumentsLinkedToTheEntity() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(1L);
        Document linked = new Document();
        when(documentRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(linked)));
        when(documentMapper.convert(linked)).thenReturn(dto);

        var page = service.searchDocumentsLinkedTo(institution, DocumentLinkKind.PHASE, 3L,
                new fr.siamois.dto.FilterDTO(), org.springframework.data.domain.PageRequest.of(0, 10));

        assertThat(page.getContent()).containsExactly(dto);
    }

    @Test
    void countLinkedTo_countsTheDocumentsLinkedToTheEntity() {
        when(documentRepository.count(any(org.springframework.data.jpa.domain.Specification.class))).thenReturn(4L);

        assertThat(service.countLinkedTo(DocumentLinkKind.CONTAINER, 3L)).isEqualTo(4L);
    }
}
