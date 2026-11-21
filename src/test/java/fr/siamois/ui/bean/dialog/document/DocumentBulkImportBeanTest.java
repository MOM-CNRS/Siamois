package fr.siamois.ui.bean.dialog.document;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.document.DocumentImportDraft;
import fr.siamois.domain.models.document.DocumentImportItem;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.settings.InstitutionSettings;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.ark.ArkService;
import fr.siamois.domain.services.document.DocumentImportDraftService;
import fr.siamois.domain.services.document.DocumentService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.domain.services.vocabulary.ConceptDescriptionService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.dto.entity.*;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.mapper.*;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.utils.MessageUtils;
import jakarta.servlet.ServletContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.primefaces.PrimeFaces;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.file.UploadedFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentBulkImportBeanTest {

    @Mock private SessionSettingsBean sessionSettingsBean;
    @Mock private DocumentService documentService;
    @Mock private DocumentImportDraftService draftService;
    @Mock private TableFieldConfigService tableFieldConfigService;
    @Mock private ConceptDescriptionService conceptDescriptionService;
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
                tableFieldConfigService,
                conceptDescriptionService,
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
    void autocompleteType_usesDocumentTypesCatalogLikeNouveauDocument() {
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(5L);
        DocumentBulkImportBean.BulkDocumentRow row = new DocumentBulkImportBean.BulkDocumentRow();
        row.getProjects().add(project);
        bean.setSelectedRow(row);

        Concept typeA = new Concept();
        typeA.setId(10L);
        Concept typeB = new Concept();
        typeB.setId(11L);
        when(tableFieldConfigService.listConfiguredTypeConcepts(5L, fr.siamois.domain.models.settings.tableconfig.ConfigurableTable.DOCUMENT))
                .thenReturn(List.of(typeB, typeA));
        ConceptAutocompleteDTO dtoA = new ConceptAutocompleteDTO(conceptDto(10L), "Photo", "fr");
        ConceptAutocompleteDTO dtoB = new ConceptAutocompleteDTO(conceptDto(11L), "Plan", "fr");
        when(conceptDescriptionService.describeAll(List.of(typeB, typeA), "fr"))
                .thenReturn(Map.of(10L, dtoA, 11L, dtoB));

        assertThat(bean.autocompleteType(""))
                .extracting(d -> d.getConceptLabelToDisplay().getLabel())
                .containsExactly("Photo", "Plan");
        assertThat(bean.autocompleteType("pla"))
                .extracting(d -> d.getConceptLabelToDisplay().getLabel())
                .containsExactly("Plan");
    }

    @Test
    void isSubmitEnabled_requiresItemIdAndMetadata() {
        DocumentBulkImportBean.BulkDocumentRow row = new DocumentBulkImportBean.BulkDocumentRow();
        row.setClientKey("k1");
        row.setItemId(1L);
        row.setMd5Sum("abc");
        row.setType(mock(ConceptAutocompleteDTO.class));
        row.setIdentifier("DOC-1");
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(1L);
        row.getProjects().add(project);
        bean.getRows().add(row);

        assertThat(bean.isRowReady(row)).isTrue();
        assertThat(bean.isSubmitEnabled()).isTrue();
    }

    @Test
    void isRowReady_requiresIdentifier() {
        when(langBean.msg("documents.bulk.identifierRequired")).thenReturn("documents.bulk.identifierRequired");

        DocumentBulkImportBean.BulkDocumentRow row = new DocumentBulkImportBean.BulkDocumentRow();
        row.setClientKey("k1");
        row.setItemId(1L);
        row.setMd5Sum("abc");
        row.setType(mock(ConceptAutocompleteDTO.class));
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(1L);
        row.getProjects().add(project);

        assertThat(bean.isRowReady(row)).isFalse();
        assertThat(bean.rowReadinessLabel(row)).isEqualTo("documents.bulk.identifierRequired");

        row.setIdentifier("  ");
        assertThat(bean.isRowReady(row)).isFalse();

        row.setIdentifier("DOC-1");
        assertThat(bean.isRowReady(row)).isTrue();
    }

    @Test
    void isSubmitEnabled_whenAtLeastOneRowReady_evenIfOthersIncomplete() {
        DocumentBulkImportBean.BulkDocumentRow ready = new DocumentBulkImportBean.BulkDocumentRow();
        ready.setClientKey("ready");
        ready.setItemId(1L);
        ready.setMd5Sum("abc");
        ready.setType(mock(ConceptAutocompleteDTO.class));
        ready.setIdentifier("DOC-READY");
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(1L);
        ready.getProjects().add(project);

        DocumentBulkImportBean.BulkDocumentRow incomplete = new DocumentBulkImportBean.BulkDocumentRow();
        incomplete.setClientKey("todo");
        incomplete.setItemId(2L);
        incomplete.setMd5Sum("def");
        incomplete.setType(mock(ConceptAutocompleteDTO.class));

        bean.getRows().add(ready);
        bean.getRows().add(incomplete);

        assertThat(bean.isRowReady(ready)).isTrue();
        assertThat(bean.isRowReady(incomplete)).isFalse();
        assertThat(bean.isSubmitEnabled()).isTrue();
        assertThat(bean.isSaveDraftEnabled()).isTrue();
    }

    @Test
    void isFullDuplicate_whenAlreadyLinkedToAllTargets() {
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(1L);
        DocumentBulkImportBean.BulkDocumentRow row = new DocumentBulkImportBean.BulkDocumentRow();
        row.setItemId(9L);
        row.setClientKey("dup");
        row.setMd5Sum("abc123");
        row.setType(mock(ConceptAutocompleteDTO.class));
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
    void submit_countsOwningProjectAsLinked_evenWhenHashAlreadyExistsAfterSave() throws Exception {
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(5L);
        project.setName("Site A");

        ConceptAutocompleteDTO type = new ConceptAutocompleteDTO(conceptDto(147L), "Type", "fr");
        Concept typeConcept = new Concept();
        typeConcept.setId(147L);
        when(conceptService.findById(147L)).thenReturn(Optional.of(typeConcept));

        DocumentImportItem item = stubItem(101L, "photo.png");
        item.setMd5Sum("abc123");
        item.setType(typeConcept);
        item.setIdentifier("DOC-0001");
        ActionUnit au = new ActionUnit();
        au.setId(5L);
        item.getProjects().add(au);
        when(draftService.addFile(anyLong(), anyLong(), anyString(), anyString(), any(), any())).thenReturn(item);
        when(actionUnitMapper.convert(au)).thenReturn(project);
        when(draftService.updateItemMetadata(eq(7L), eq(10L), eq(101L), any())).thenReturn(item);
        when(draftService.readFileBytes(7L, 10L, 101L)).thenReturn(new byte[]{1, 2, 3, 4});

        ConceptLabel conceptLabel = mock(ConceptLabel.class);
        when(conceptLabel.getLabel()).thenReturn("label");
        when(labelService.findLabelOf(any(Concept.class), anyString())).thenReturn(conceptLabel);
        when(conceptMapper.convert(any(Concept.class))).thenAnswer(inv -> {
            Concept c = inv.getArgument(0);
            return conceptDto(c.getId());
        });

        ActionUnit owning = new ActionUnit();
        owning.setId(5L);
        Document saved = new Document();
        saved.setId(900L);
        saved.setMd5Sum("abc123");
        saved.setActionUnit(owning);
        when(actionUnitMapper.invertConvert(project)).thenReturn(owning);
        when(documentService.saveFile(any(), any(), any(), any())).thenReturn(saved);
        when(documentService.existInActionUnitByHash(project, "abc123")).thenReturn(false);

        InstitutionSettings settings = mock(InstitutionSettings.class);
        when(settings.getArkIsEnabled()).thenReturn(false);
        when(sessionSettingsBean.getInstitutionSettings()).thenReturn(settings);
        when(servletContext.getContextPath()).thenReturn("/siamois");
        lenient().when(langBean.msg(anyString())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(langBean.msg(anyString(), any())).thenAnswer(inv -> inv.getArgument(0));

        bean.handleFileUpload(fileUploadEvent("photo.png"));
        DocumentBulkImportBean.BulkDocumentRow row = bean.getRows().get(0);
        row.setType(type);
        row.setIdentifier("DOC-0001");
        row.setMd5Sum("abc123");
        row.getProjects().clear();
        row.getProjects().add(project);

        try (MockedStatic<MessageUtils> messages = mockStatic(MessageUtils.class);
             MockedStatic<PrimeFaces> faces = mockStatic(PrimeFaces.class)) {
            PrimeFaces pf = mock(PrimeFaces.class, RETURNS_DEEP_STUBS);
            faces.when(PrimeFaces::current).thenReturn(pf);
            bean.submit();
        }

        // Owning project from buildDocument/saveFile counts as linked — no second attach needed.
        verify(documentService, never()).addToActionUnit(any(), any());
        verify(draftService).removeItemAfterSuccessfulSubmit(7L, 10L, 101L);
        assertThat(bean.getRows()).isEmpty();
        assertThat(bean.getReportLines()).anyMatch(line -> line.contains("OK"));
    }

    private static ConceptDTO conceptDto(long id) {
        ConceptDTO dto = new ConceptDTO();
        dto.setId(id);
        return dto;
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

    @Test
    void onTypeChange_suggestsIdentifierWhenNotEdited() {
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(5L);
        ConceptAutocompleteDTO type = new ConceptAutocompleteDTO(conceptDto(147L), "Photo", "fr");
        DocumentBulkImportBean.BulkDocumentRow row = new DocumentBulkImportBean.BulkDocumentRow();
        row.setItemId(1L);
        row.setType(type);
        row.getProjects().add(project);
        bean.setSelectedRow(row);

        when(documentService.suggestIdentifier(any())).thenReturn("DOC-42");

        bean.onTypeChange();

        assertThat(row.getIdentifier()).isEqualTo("DOC-42");
        assertThat(bean.isDraftDirty()).isTrue();
        verify(draftService, never()).updateItemMetadata(anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void onIdentifierInput_marksEditedAndPersists() {
        DocumentBulkImportBean.BulkDocumentRow row = new DocumentBulkImportBean.BulkDocumentRow();
        row.setIdentifier("manual");
        bean.setSelectedRow(row);

        bean.onIdentifierInput();

        assertThat(row.isIdentifierEdited()).isTrue();
        assertThat(bean.isDraftDirty()).isTrue();
    }

    @Test
    void onProjectsChange_refreshesSuggestionUnlessEdited() {
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(5L);
        ConceptAutocompleteDTO type = new ConceptAutocompleteDTO(conceptDto(147L), "Photo", "fr");
        DocumentBulkImportBean.BulkDocumentRow row = new DocumentBulkImportBean.BulkDocumentRow();
        row.setType(type);
        row.getProjects().add(project);
        bean.setSelectedRow(row);
        when(documentService.suggestIdentifier(any())).thenReturn("DOC-99");

        bean.onProjectsChange();
        assertThat(row.getIdentifier()).isEqualTo("DOC-99");

        row.setIdentifierEdited(true);
        row.setIdentifier("kept");
        bean.onProjectsChange();
        assertThat(row.getIdentifier()).isEqualTo("kept");
    }

    @Test
    void autocompleteType_withoutProject_returnsEmpty() {
        bean.setSelectedRow(new DocumentBulkImportBean.BulkDocumentRow());
        assertThat(bean.autocompleteType("x")).isEmpty();
    }

    @Test
    void autocompleteType_matchesAltLabelAndPrefLabel() {
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(5L);
        DocumentBulkImportBean.BulkDocumentRow row = new DocumentBulkImportBean.BulkDocumentRow();
        row.getProjects().add(project);
        bean.setSelectedRow(row);

        Concept type = new Concept();
        type.setId(10L);
        when(tableFieldConfigService.listConfiguredTypeConcepts(5L, fr.siamois.domain.models.settings.tableconfig.ConfigurableTable.DOCUMENT))
                .thenReturn(List.of(type));
        ConceptAutocompleteDTO dto = new ConceptAutocompleteDTO(
                new ConceptAutocompleteDTO(conceptDto(10L), "Photo", "fr").getConceptLabelToDisplay(),
                "Photograph",
                List.of("cliché"),
                null,
                null);
        when(conceptDescriptionService.describeAll(List.of(type), "fr")).thenReturn(Map.of(10L, dto));

        assertThat(bean.autocompleteType("CLICH")).containsExactly(dto);
        assertThat(bean.autocompleteType("photograph")).containsExactly(dto);
    }

    @Test
    void autocompleteNatureAndScale_delegateToFieldConfiguration() throws Exception {
        ConceptAutocompleteDTO nature = new ConceptAutocompleteDTO(conceptDto(1L), "Nature", "fr");
        ConceptAutocompleteDTO scale = new ConceptAutocompleteDTO(conceptDto(2L), "Scale", "fr");
        when(fieldConfigurationService.fetchAutocomplete(any(), eq(Document.NATURE_FIELD_CODE), eq("n")))
                .thenReturn(List.of(nature));
        when(fieldConfigurationService.fetchAutocomplete(any(), eq(Document.SCALE_FIELD_CODE), eq("s")))
                .thenReturn(List.of(scale));

        assertThat(bean.autocompleteNature("n")).containsExactly(nature);
        assertThat(bean.autocompleteScale("s")).containsExactly(scale);
    }

    @Test
    void rowReadinessLabel_reportsMissingTypeIdentifierAndTargets() {
        when(langBean.msg(anyString())).thenAnswer(inv -> inv.getArgument(0));

        DocumentBulkImportBean.BulkDocumentRow row = new DocumentBulkImportBean.BulkDocumentRow();
        row.setItemId(1L);
        row.setMd5Sum("abc");
        assertThat(bean.rowReadinessLabel(row)).isEqualTo("documents.typeRequired");

        row.setType(mock(ConceptAutocompleteDTO.class));
        assertThat(bean.rowReadinessLabel(row)).isEqualTo("documents.bulk.identifierRequired");

        row.setIdentifier("DOC-1");
        assertThat(bean.rowReadinessLabel(row)).isEqualTo("documents.bulk.targetRequired");
    }

    @Test
    void completeHelpers_returnEmptyWithoutInstitution() {
        when(sessionSettingsBean.getSelectedInstitution()).thenReturn(null);
        assertThat(bean.completeProject("a")).isEmpty();
        assertThat(bean.completeRecordingUnit("a")).isEmpty();
        assertThat(bean.completeFind("a")).isEmpty();
        assertThat(bean.completePlace("a")).isEmpty();
    }

    @Test
    void submit_linksRecordingUnitFindAndPlaceTargets() throws Exception {
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(5L);
        RecordingUnitSummaryDTO ruSummary = new RecordingUnitSummaryDTO(11L);
        SpecimenSummaryDTO findSummary = new SpecimenSummaryDTO();
        findSummary.setId(22L);
        SpatialUnitDTO place = new SpatialUnitDTO();
        place.setId(33L);

        ConceptAutocompleteDTO type = new ConceptAutocompleteDTO(conceptDto(147L), "Type", "fr");
        Concept typeConcept = new Concept();
        typeConcept.setId(147L);
        when(conceptService.findById(147L)).thenReturn(Optional.of(typeConcept));

        DocumentImportItem item = stubItem(101L, "photo.png");
        item.setMd5Sum("hash1");
        item.setType(typeConcept);
        item.setIdentifier("DOC-1");
        ActionUnit au = new ActionUnit();
        au.setId(5L);
        item.getProjects().add(au);
        RecordingUnit ruEntity = new RecordingUnit();
        ruEntity.setId(11L);
        Specimen spEntity = new Specimen();
        spEntity.setId(22L);
        SpatialUnit placeEntity = new SpatialUnit();
        placeEntity.setId(33L);
        item.getRecordingUnits().add(ruEntity);
        item.getFinds().add(spEntity);
        item.getPlaces().add(placeEntity);

        when(draftService.addFile(anyLong(), anyLong(), anyString(), anyString(), any(), any())).thenReturn(item);
        when(draftService.updateItemMetadata(eq(7L), eq(10L), eq(101L), any())).thenReturn(item);
        when(draftService.readFileBytes(7L, 10L, 101L)).thenReturn(new byte[]{1, 2, 3});
        when(actionUnitMapper.convert(au)).thenReturn(project);
        when(recordingUnitSummaryMapper.convert(ruEntity)).thenReturn(ruSummary);
        when(specimenSummaryMapper.convert(spEntity)).thenReturn(findSummary);
        when(spatialUnitMapper.convert(placeEntity)).thenReturn(place);

        ConceptLabel conceptLabel = mock(ConceptLabel.class);
        when(conceptLabel.getLabel()).thenReturn("label");
        when(labelService.findLabelOf(any(Concept.class), anyString())).thenReturn(conceptLabel);
        when(conceptMapper.convert(any(Concept.class))).thenAnswer(inv -> conceptDto(((Concept) inv.getArgument(0)).getId()));

        ActionUnit owning = new ActionUnit();
        owning.setId(5L);
        Document saved = new Document();
        saved.setId(900L);
        saved.setMd5Sum("hash1");
        saved.setActionUnit(owning);
        when(actionUnitMapper.invertConvert(project)).thenReturn(owning);
        when(documentService.saveFile(any(), any(), any(), any())).thenReturn(saved);
        when(documentService.existInActionUnitByHash(project, "hash1")).thenReturn(false);

        RecordingUnitDTO ru = new RecordingUnitDTO();
        ru.setId(11L);
        SpecimenDTO specimen = new SpecimenDTO();
        specimen.setId(22L);
        when(recordingUnitService.findById(11L)).thenReturn(ru);
        when(specimenService.findById(22L)).thenReturn(specimen);
        when(documentService.existInRecordingUnitByHash(ru, "hash1")).thenReturn(false);
        when(documentService.existInSpecimenByHash(specimen, "hash1")).thenReturn(false);
        when(documentService.existInSpatialUnitByHash(place, "hash1")).thenReturn(false);
        when(profilePermissionService.hasRecordingUnitWritePermission(any(), eq(ru))).thenReturn(true);
        when(profilePermissionService.hasSpecimenWritePermission(any(), eq(specimen))).thenReturn(true);

        InstitutionSettings settings = mock(InstitutionSettings.class);
        when(settings.getArkIsEnabled()).thenReturn(false);
        when(sessionSettingsBean.getInstitutionSettings()).thenReturn(settings);
        when(servletContext.getContextPath()).thenReturn("/siamois");
        lenient().when(langBean.msg(anyString())).thenAnswer(inv -> inv.getArgument(0));

        bean.handleFileUpload(fileUploadEvent("photo.png"));
        DocumentBulkImportBean.BulkDocumentRow row = bean.getRows().get(0);
        row.setType(type);
        row.setIdentifier("DOC-1");
        row.setMd5Sum("hash1");

        try (MockedStatic<MessageUtils> messages = mockStatic(MessageUtils.class);
             MockedStatic<PrimeFaces> faces = mockStatic(PrimeFaces.class)) {
            PrimeFaces pf = mock(PrimeFaces.class, RETURNS_DEEP_STUBS);
            faces.when(PrimeFaces::current).thenReturn(pf);
            bean.submit();
        }

        verify(documentService).addToRecordingUnit(saved, ru);
        verify(documentService).addToSpecimen(saved, specimen);
        verify(documentService).addToSpatialUnit(saved, place);
        assertThat(bean.getReportLines()).anyMatch(line -> line.contains("OK"));
    }

    @Test
    void autocompleteType_usesDefaultProjectAndHandlesEmptyCatalog() {
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(5L);
        bean.setDefaultProject(project);
        bean.setSelectedRow(null);
        when(tableFieldConfigService.listConfiguredTypeConcepts(5L, fr.siamois.domain.models.settings.tableconfig.ConfigurableTable.DOCUMENT))
                .thenReturn(List.of());

        assertThat(bean.autocompleteType("x")).isEmpty();
    }

    @Test
    void typeAndIdentifierHandlers_ignoreNullSelection() {
        bean.setSelectedRow(null);
        bean.onTypeChange();
        bean.onIdentifierInput();
        bean.onProjectsChange();
    }

    @Test
    void refreshSuggestedIdentifier_skipsIncompleteRowsAndSuggestionErrors() {
        DocumentBulkImportBean.BulkDocumentRow incomplete = new DocumentBulkImportBean.BulkDocumentRow();
        incomplete.setType(new ConceptAutocompleteDTO(conceptDto(1L), "T", "fr"));
        bean.setSelectedRow(incomplete);
        bean.onTypeChange();
        assertThat(incomplete.getIdentifier()).isNull();

        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(5L);
        incomplete.getProjects().add(project);
        when(documentService.suggestIdentifier(any())).thenThrow(new RuntimeException("boom"));
        bean.onTypeChange();
        assertThat(incomplete.getIdentifier()).isNull();
    }

    @Test
    void submit_keepsIncompleteRowsAndReportsPending() throws Exception {
        DocumentImportItem incompleteItem = stubItem(101L, "todo.png");
        when(draftService.addFile(anyLong(), anyLong(), anyString(), anyString(), any(), any())).thenReturn(incompleteItem);
        when(draftService.updateItemMetadata(eq(7L), eq(10L), eq(101L), any())).thenReturn(incompleteItem);
        lenient().when(langBean.msg(anyString())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(langBean.msg(anyString(), any(), any())).thenAnswer(inv -> inv.getArgument(0));

        bean.handleFileUpload(fileUploadEvent("todo.png"));

        Concept typeConcept = new Concept();
        typeConcept.setId(1L);
        ActionUnit au = new ActionUnit();
        au.setId(5L);
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(5L);
        DocumentImportItem readyItem = stubItem(202L, "ready.png");
        readyItem.setMd5Sum("abc");
        readyItem.setType(typeConcept);
        readyItem.setIdentifier("DOC-1");
        readyItem.getProjects().add(au);

        DocumentBulkImportBean.BulkDocumentRow ready = new DocumentBulkImportBean.BulkDocumentRow();
        ready.setClientKey("ready");
        ready.setItemId(202L);
        ready.setFileName("ready.png");
        ready.setMd5Sum("abc");
        ready.setType(new ConceptAutocompleteDTO(conceptDto(1L), "T", "fr"));
        ready.setIdentifier("DOC-1");
        ready.getProjects().add(project);
        bean.getRows().add(ready);

        when(conceptService.findById(1L)).thenReturn(Optional.of(typeConcept));
        when(draftService.updateItemMetadata(eq(7L), eq(10L), eq(202L), any())).thenReturn(readyItem);
        when(draftService.readFileBytes(7L, 10L, 202L)).thenReturn(new byte[]{1});
        when(actionUnitMapper.convert(au)).thenReturn(project);
        ConceptLabel conceptLabel = mock(ConceptLabel.class);
        when(conceptLabel.getLabel()).thenReturn("label");
        when(labelService.findLabelOf(any(Concept.class), anyString())).thenReturn(conceptLabel);
        when(conceptMapper.convert(any(Concept.class))).thenAnswer(inv -> conceptDto(((Concept) inv.getArgument(0)).getId()));
        ActionUnit owning = new ActionUnit();
        owning.setId(5L);
        Document saved = new Document();
        saved.setId(1L);
        saved.setMd5Sum("abc");
        saved.setActionUnit(owning);
        when(actionUnitMapper.invertConvert(project)).thenReturn(owning);
        when(documentService.saveFile(any(), any(), any(), any())).thenReturn(saved);
        when(documentService.existInActionUnitByHash(project, "abc")).thenReturn(false);
        InstitutionSettings settings = mock(InstitutionSettings.class);
        when(settings.getArkIsEnabled()).thenReturn(false);
        when(sessionSettingsBean.getInstitutionSettings()).thenReturn(settings);
        when(servletContext.getContextPath()).thenReturn("/siamois");

        try (MockedStatic<MessageUtils> messages = mockStatic(MessageUtils.class);
             MockedStatic<PrimeFaces> faces = mockStatic(PrimeFaces.class)) {
            PrimeFaces pf = mock(PrimeFaces.class, RETURNS_DEEP_STUBS);
            faces.when(PrimeFaces::current).thenReturn(pf);
            bean.submit();
            messages.verify(() -> MessageUtils.displayInfoMessage(eq(langBean), eq("documents.bulk.doneWithPending"), eq(1), eq(1)));
        }

        assertThat(bean.getRows()).extracting(DocumentBulkImportBean.BulkDocumentRow::getFileName)
                .containsExactly("todo.png");
        assertThat(bean.getReportLines()).anyMatch(line -> line.contains("documents.bulk.status.pending"));
    }

    @Test
    void maxIdentifierLength_is255() {
        assertThat(bean.maxIdentifierLength()).isEqualTo(255);
    }

    @Test
    void submit_reportsFailuresWithoutPending() throws Exception {
        Concept typeConcept = new Concept();
        typeConcept.setId(1L);
        ActionUnit au = new ActionUnit();
        au.setId(5L);
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(5L);
        DocumentImportItem item = stubItem(101L, "bad.png");
        item.setMd5Sum("abc");
        item.setType(typeConcept);
        item.setIdentifier("DOC-1");
        item.getProjects().add(au);

        when(draftService.addFile(anyLong(), anyLong(), anyString(), anyString(), any(), any())).thenReturn(item);
        when(draftService.updateItemMetadata(eq(7L), eq(10L), eq(101L), any())).thenReturn(item);
        when(draftService.readFileBytes(7L, 10L, 101L)).thenThrow(new IOException("boom"));
        when(actionUnitMapper.convert(au)).thenReturn(project);
        ConceptLabel conceptLabel = mock(ConceptLabel.class);
        when(conceptLabel.getLabel()).thenReturn("label");
        when(labelService.findLabelOf(any(Concept.class), anyString())).thenReturn(conceptLabel);
        when(conceptMapper.convert(any(Concept.class))).thenAnswer(inv -> conceptDto(((Concept) inv.getArgument(0)).getId()));
        lenient().when(langBean.msg(anyString())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(langBean.msg(anyString(), any(), any())).thenAnswer(inv -> inv.getArgument(0));

        bean.handleFileUpload(fileUploadEvent("bad.png"));
        bean.getRows().get(0).setType(new ConceptAutocompleteDTO(conceptDto(1L), "T", "fr"));
        bean.getRows().get(0).setIdentifier("DOC-1");

        try (MockedStatic<MessageUtils> messages = mockStatic(MessageUtils.class);
             MockedStatic<PrimeFaces> faces = mockStatic(PrimeFaces.class)) {
            PrimeFaces pf = mock(PrimeFaces.class, RETURNS_DEEP_STUBS);
            faces.when(PrimeFaces::current).thenReturn(pf);
            bean.submit();
            messages.verify(() -> MessageUtils.displayInfoMessage(eq(langBean), eq("documents.bulk.done"), eq(0), eq(1)));
        }
    }
}
