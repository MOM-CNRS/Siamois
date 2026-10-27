package fr.siamois.ui.bean.dialog.document;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.document.DocumentImportDraft;
import fr.siamois.domain.models.document.DocumentImportItem;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.ark.ArkService;
import fr.siamois.domain.services.document.DocumentImportDraftService;
import fr.siamois.domain.services.document.DocumentService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.dto.entity.*;
import fr.siamois.mapper.*;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import jakarta.servlet.ServletContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.file.UploadedFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentBulkImportBeanTest {

    @Mock private SessionSettingsBean sessionSettingsBean;
    @Mock private DocumentService documentService;
    @Mock private DocumentImportDraftService draftService;
    @Mock private FieldConfigurationService fieldConfigurationService;
    @Mock private LangBean langBean;
    @Mock private ServletContext servletContext;
    @Mock private ConceptService conceptService;
    @Mock private LabelService labelService;
    @Mock private ConceptMapper conceptMapper;
    @Mock private ArkService arkService;
    @Mock private ActionUnitService actionUnitService;
    @Mock private RecordingUnitService recordingUnitService;
    @Mock private SpecimenService specimenService;
    @Mock private SpatialUnitService spatialUnitService;
    @Mock private ProfilePermissionService profilePermissionService;
    @Mock private ActionUnitMapper actionUnitMapper;
    @Mock private RecordingUnitSummaryMapper recordingUnitSummaryMapper;
    @Mock private SpecimenSummaryMapper specimenSummaryMapper;
    @Mock private SpatialUnitMapper spatialUnitMapper;

    private DocumentBulkImportBean bean;
    private InstitutionDTO institution;
    private PersonDTO person;

    @BeforeEach
    void setUp() {
        bean = new DocumentBulkImportBean(
                sessionSettingsBean,
                documentService,
                draftService,
                fieldConfigurationService,
                langBean,
                servletContext,
                conceptService,
                labelService,
                conceptMapper,
                arkService,
                actionUnitService,
                recordingUnitService,
                specimenService,
                spatialUnitService,
                profilePermissionService,
                actionUnitMapper,
                recordingUnitSummaryMapper,
                specimenSummaryMapper,
                spatialUnitMapper);
        bean.init();

        institution = new InstitutionDTO();
        institution.setId(10L);
        person = new PersonDTO();
        person.setId(7L);
        // Not every test touches the session; keep these available without strict unused-stub failures.
        lenient().when(sessionSettingsBean.getSelectedInstitution()).thenReturn(institution);
        lenient().when(sessionSettingsBean.getUserInfo()).thenReturn(new UserInfo(institution, person, "fr"));
    }

    private UploadedFile uploadedFile(String fileName) throws IOException {
        UploadedFile file = mock(UploadedFile.class);
        when(file.getFileName()).thenReturn(fileName);
        when(file.getContentType()).thenReturn("application/octet-stream");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3, 4}));
        return file;
    }

    /** Build the upload event outside of any open {@code when(...)} to avoid UnfinishedStubbing. */
    private FileUploadEvent fileUploadEvent(String fileName) throws IOException {
        UploadedFile file = uploadedFile(fileName);
        FileUploadEvent event = mock(FileUploadEvent.class);
        when(event.getFile()).thenReturn(file);
        return event;
    }

    private DocumentImportItem stubItem(long id, String fileName) {
        DocumentImportItem item = new DocumentImportItem();
        item.setId(id);
        item.setClientKey("ck-" + id);
        item.setOriginalFileName(fileName);
        item.setContentType("application/octet-stream");
        item.setFileSize(4L);
        item.setMd5Sum("md5");
        item.setStoredFileName("stored.bin");
        item.setTitle(fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName);
        item.setProjects(new HashSet<>());
        item.setRecordingUnits(new HashSet<>());
        item.setFinds(new HashSet<>());
        item.setPlaces(new HashSet<>());
        return item;
    }

    @Test
    void handleFileUpload_persistsThroughDraftService() throws Exception {
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(5L);
        project.setName("Site A");
        bean.setDefaultProject(project);

        DocumentImportItem item = stubItem(101L, "photo-coupe.jpg");
        ActionUnit au = new ActionUnit();
        au.setId(5L);
        item.getProjects().add(au);
        when(draftService.addFile(eq(7L), eq(10L), eq("photo-coupe.jpg"), anyString(), any(), any()))
                .thenReturn(item);
        when(actionUnitMapper.convert(au)).thenReturn(project);

        bean.handleFileUpload(fileUploadEvent("photo-coupe.jpg"));

        assertThat(bean.getRows()).hasSize(1);
        DocumentBulkImportBean.BulkDocumentRow row = bean.getRows().get(0);
        assertThat(row.getItemId()).isEqualTo(101L);
        assertThat(row.getTitle()).isEqualTo("photo-coupe");
        assertThat(row.getFileName()).isEqualTo("photo-coupe.jpg");
        assertThat(row.getMd5Sum()).isEqualTo("md5");
        assertThat(row.getProjects()).containsExactly(project);

        ArgumentCaptor<DocumentImportDraftService.ItemDefaults> defaults =
                ArgumentCaptor.forClass(DocumentImportDraftService.ItemDefaults.class);
        verify(draftService).addFile(eq(7L), eq(10L), eq("photo-coupe.jpg"), anyString(), any(), defaults.capture());
        assertThat(defaults.getValue().projectIds()).containsExactly(5L);
    }

    @Test
    void persistSelectedRow_marksDraftDirtyWithoutPersisting() {
        bean.persistSelectedRow();
        assertThat(bean.isDraftDirty()).isTrue();
        verify(draftService, never()).updateItemMetadata(anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void saveDraft_persistsAllRowsAndClearsDirty() throws Exception {
        DocumentImportItem item = stubItem(101L, "photo.jpg");
        when(draftService.addFile(anyLong(), anyLong(), anyString(), anyString(), any(), any())).thenReturn(item);
        bean.handleFileUpload(fileUploadEvent("photo.jpg"));
        bean.setDraftDirty(true);

        when(draftService.updateItemMetadata(eq(7L), eq(10L), eq(101L), any())).thenReturn(item);

        try {
            bean.saveDraft();
        } catch (Exception ignored) {
        }

        verify(draftService).updateItemMetadata(eq(7L), eq(10L), eq(101L), any());
        assertThat(bean.isDraftDirty()).isFalse();
    }

    @Test
    void discardUnsavedAndClose_reloadsDraftWithoutAbandon() {
        DocumentImportDraft draft = new DocumentImportDraft();
        draft.setId(3L);
        draft.setItems(new ArrayList<>());
        when(draftService.loadDraftWithItems(7L, 10L)).thenReturn(draft);

        bean.setDraftDirty(true);
        bean.discardUnsavedAndClose();

        verify(draftService).loadDraftWithItems(7L, 10L);
        verify(draftService, never()).abandonDraft(anyLong(), anyLong());
        assertThat(bean.isDraftDirty()).isFalse();
    }

    @Test
    void saveDraftEnabled_whenSomeRowsIncomplete() throws Exception {
        DocumentImportItem item = stubItem(101L, "photo.jpg");
        when(draftService.addFile(anyLong(), anyLong(), anyString(), anyString(), any(), any())).thenReturn(item);
        bean.handleFileUpload(fileUploadEvent("photo.jpg"));

        assertThat(bean.isSaveDraftEnabled()).isTrue();
        assertThat(bean.isSubmitEnabled()).isFalse();
    }

    @Test
    void removeRow_removesFromDraftService() throws Exception {
        DocumentImportItem item = stubItem(55L, "a.pdf");
        when(draftService.addFile(anyLong(), anyLong(), anyString(), anyString(), any(), any())).thenReturn(item);
        bean.handleFileUpload(fileUploadEvent("a.pdf"));

        bean.removeRow(bean.getRows().get(0));
        verify(draftService).removeItem(7L, 10L, 55L);
        assertThat(bean.getRows()).isEmpty();
    }

    @Test
    void targetChips_exposeCountsAndPreviewLabels() {
        DocumentBulkImportBean.BulkDocumentRow row = new DocumentBulkImportBean.BulkDocumentRow();
        assertThat(bean.hasNoTargets(row)).isTrue();

        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(1L);
        project.setName("Site A");
        row.getProjects().add(project);
        RecordingUnitSummaryDTO ru = new RecordingUnitSummaryDTO(2L);
        ru.setFullIdentifier("UE-2");
        row.getRecordingUnits().add(ru);

        assertThat(bean.countTargets(row)).isEqualTo(2);
        assertThat(bean.previewTargetLabels(row)).containsExactly("Site A", "UE-2");
    }

    @Test
    void loadTargetOptions_loadsInstitutionChoices() {
        RecordingUnitSummaryDTO ru = new RecordingUnitSummaryDTO(1L);
        SpecimenSummaryDTO find = new SpecimenSummaryDTO();
        find.setId(2L);
        SpatialUnitDTO place = new SpatialUnitDTO();
        place.setId(3L);
        when(recordingUnitService.findMatchingInInstitutionByFullIdentifier(institution, "", 250))
                .thenReturn(List.of(ru));
        when(specimenService.findMatchingInInstitutionByFullIdentifier(institution, "", 250))
                .thenReturn(List.of(find));
        when(spatialUnitService.findMatchingInInstitutionByName(institution, "", 250))
                .thenReturn(List.of(place));

        bean.loadTargetOptions();

        assertThat(bean.getRecordingUnitOptions()).containsExactly(ru);
        assertThat(bean.getFindOptions()).containsExactly(find);
        assertThat(bean.getPlaceOptions()).containsExactly(place);
    }

    @Test
    void isSubmitEnabled_requiresItemIdAndMetadata() {
        DocumentBulkImportBean.BulkDocumentRow row = new DocumentBulkImportBean.BulkDocumentRow();
        row.setClientKey("k1");
        row.setItemId(1L);
        row.setTitle("Doc");
        row.setMd5Sum("abc");
        row.setNature(mock(fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO.class));
        row.setType(mock(fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO.class));
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(1L);
        row.getProjects().add(project);
        bean.getRows().add(row);

        assertThat(bean.isRowReady(row)).isTrue();
        assertThat(bean.isSubmitEnabled()).isTrue();
    }

    @Test
    void isFullDuplicate_whenAlreadyLinkedToAllTargets() {
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(1L);
        DocumentBulkImportBean.BulkDocumentRow row = new DocumentBulkImportBean.BulkDocumentRow();
        row.setItemId(9L);
        row.setClientKey("dup");
        row.setTitle("Doc");
        row.setMd5Sum("abc123");
        row.setNature(mock(fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO.class));
        row.setType(mock(fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO.class));
        row.getProjects().add(project);
        when(documentService.existInActionUnitByHash(project, "abc123")).thenReturn(true);

        assertThat(bean.isFullDuplicate(row)).isTrue();
        assertThat(bean.isRowReady(row)).isFalse();
    }

    @Test
    void applyRemoteDefault_fromRecordingUnit_setsDefaults() {
        ActionUnitSummaryDTO projectSummary = new ActionUnitSummaryDTO();
        projectSummary.setId(50L);
        RecordingUnitDTO full = new RecordingUnitDTO();
        full.setId(11L);
        full.setFullIdentifier("UE-11");
        full.setActionUnit(projectSummary);
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(50L);
        when(recordingUnitService.findById(11L)).thenReturn(full);
        when(actionUnitService.findById(50L)).thenReturn(project);

        bean.applyRemoteDefault("recording-units", "11");

        assertThat(bean.getDefaultRecordingUnit().getId()).isEqualTo(11L);
        assertThat(bean.getDefaultProject()).isEqualTo(project);
    }

    @Test
    void applyRemoteDefault_fromFind_setsDefaults() {
        ActionUnitSummaryDTO projectSummary = new ActionUnitSummaryDTO();
        projectSummary.setId(60L);
        SpecimenDTO specimen = new SpecimenDTO();
        specimen.setId(22L);
        specimen.setFullIdentifier("MOB-22");
        specimen.setActionUnit(projectSummary);
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(60L);
        when(specimenService.findById(22L)).thenReturn(specimen);
        when(actionUnitService.findById(60L)).thenReturn(project);

        bean.applyRemoteDefault("finds", "22");

        assertThat(bean.getDefaultFind().getId()).isEqualTo(22L);
        assertThat(bean.getDefaultProject()).isEqualTo(project);
    }

    @Test
    void applyRemoteDefault_fromPlace_setsPlaceOnly() {
        SpatialUnitDTO place = new SpatialUnitDTO();
        place.setId(33L);
        place.setName("Site nord");
        when(spatialUnitService.findById(33L)).thenReturn(place);

        bean.applyRemoteDefault("places", "33");

        assertThat(bean.getDefaultPlace()).isEqualTo(place);
        assertThat(bean.getDefaultProject()).isNull();
    }

    @Test
    void handleFileUpload_passesDefaultTargetsToService() throws Exception {
        bean.setDefaultRecordingUnit(new RecordingUnitSummaryDTO(11L));
        SpecimenSummaryDTO find = new SpecimenSummaryDTO();
        find.setId(22L);
        bean.setDefaultFind(find);
        SpatialUnitDTO place = new SpatialUnitDTO();
        place.setId(33L);
        bean.setDefaultPlace(place);

        DocumentImportItem item = stubItem(1L, "doc.pdf");
        RecordingUnit ru = new RecordingUnit();
        ru.setId(11L);
        Specimen sp = new Specimen();
        sp.setId(22L);
        SpatialUnit su = new SpatialUnit();
        su.setId(33L);
        item.getRecordingUnits().add(ru);
        item.getFinds().add(sp);
        item.getPlaces().add(su);
        when(draftService.addFile(anyLong(), anyLong(), anyString(), anyString(), any(), any())).thenReturn(item);
        when(recordingUnitSummaryMapper.convert(ru)).thenReturn(new RecordingUnitSummaryDTO(11L));
        when(specimenSummaryMapper.convert(sp)).thenAnswer(inv -> {
            SpecimenSummaryDTO s = new SpecimenSummaryDTO();
            s.setId(22L);
            return s;
        });
        when(spatialUnitMapper.convert(su)).thenReturn(place);

        bean.handleFileUpload(fileUploadEvent("doc.pdf"));

        ArgumentCaptor<DocumentImportDraftService.ItemDefaults> defaults =
                ArgumentCaptor.forClass(DocumentImportDraftService.ItemDefaults.class);
        verify(draftService).addFile(eq(7L), eq(10L), eq("doc.pdf"), anyString(), any(), defaults.capture());
        assertThat(defaults.getValue().recordingUnitIds()).isEqualTo(Set.of(11L));
        assertThat(defaults.getValue().findIds()).isEqualTo(Set.of(22L));
        assertThat(defaults.getValue().placeIds()).isEqualTo(Set.of(33L));
        assertThat(bean.getRows().get(0).getRecordingUnits()).hasSize(1);
        assertThat(bean.getRows().get(0).getFinds()).hasSize(1);
        assertThat(bean.getRows().get(0).getPlaces()).hasSize(1);
    }
}
