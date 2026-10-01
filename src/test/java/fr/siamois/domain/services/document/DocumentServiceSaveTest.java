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
}
