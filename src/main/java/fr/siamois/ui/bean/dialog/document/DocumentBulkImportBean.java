package fr.siamois.ui.bean.dialog.document;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.ark.Ark;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.document.DocumentImportDraft;
import fr.siamois.domain.models.document.DocumentImportItem;
import fr.siamois.domain.models.exceptions.InvalidFileSizeException;
import fr.siamois.domain.models.exceptions.InvalidFileTypeException;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import fr.siamois.domain.models.vocabulary.Concept;
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
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.mapper.*;
import fr.siamois.ui.bean.ActionFromBean;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.utils.DocumentUtils;
import fr.siamois.utils.MessageUtils;
import jakarta.servlet.ServletContext;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.PrimeFaces;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.event.SelectEvent;
import org.primefaces.model.file.UploadedFile;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@Getter
@Setter
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class DocumentBulkImportBean implements Serializable {

    public static final int MAX_ITEMS = DocumentImportDraftService.MAX_ITEMS;
    private static final int AUTOCOMPLETE_LIMIT = 20;
    private static final int TARGET_OPTIONS_LIMIT = 250;
    private static final DateTimeFormatter DRAFT_UPDATED_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final SessionSettingsBean sessionSettingsBean;
    private final transient DocumentService documentService;
    private final transient DocumentImportDraftService draftService;
    private final transient FieldConfigurationService fieldConfigurationService;
    private final LangBean langBean;
    private final transient ServletContext servletContext;
    private final transient ConceptService conceptService;
    private final transient LabelService labelService;
    private final transient ConceptMapper conceptMapper;
    private final transient ArkService arkService;
    private final transient ActionUnitService actionUnitService;
    private final transient RecordingUnitService recordingUnitService;
    private final transient SpecimenService specimenService;
    private final transient SpatialUnitService spatialUnitService;
    private final transient ProfilePermissionService profilePermissionService;
    private final transient ActionUnitMapper actionUnitMapper;
    private final transient RecordingUnitSummaryMapper recordingUnitSummaryMapper;
    private final transient SpecimenSummaryMapper specimenSummaryMapper;
    private final transient SpatialUnitMapper spatialUnitMapper;

    private List<BulkDocumentRow> rows = new ArrayList<>();
    private List<String> reportLines = new ArrayList<>();
    private boolean reportVisible;
    private BulkDocumentRow selectedRow;
    private transient ActionFromBean actionOnComplete;
    /** Last draft update time, for the dialog header. */
    private OffsetDateTime draftUpdatedAt;
    /** True when metadata/targets were edited since the last explicit draft save. */
    private boolean draftDirty;

    /** Listes de choix pour les multi-sélections (UE / mobiliers / lieux). */
    private List<RecordingUnitSummaryDTO> recordingUnitOptions = new ArrayList<>();
    private List<SpecimenSummaryDTO> findOptions = new ArrayList<>();
    private List<SpatialUnitDTO> placeOptions = new ArrayList<>();

    private ActionUnitDTO defaultProject;
    private RecordingUnitSummaryDTO defaultRecordingUnit;
    private SpecimenSummaryDTO defaultFind;
    private SpatialUnitDTO defaultPlace;

    public void init() {
        rows = new ArrayList<>();
        reportLines = new ArrayList<>();
        reportVisible = false;
        selectedRow = null;
        actionOnComplete = null;
        draftUpdatedAt = null;
        draftDirty = false;
        defaultProject = null;
        defaultRecordingUnit = null;
        defaultFind = null;
        defaultPlace = null;
        recordingUnitOptions = new ArrayList<>();
        findOptions = new ArrayList<>();
        placeOptions = new ArrayList<>();
    }

    public void show() {
        loadTargetOptions();
        PrimeFaces.current().ajax().update("bulkDocumentImport");
        PrimeFaces.current().executeScript("PF('bulkDocumentImportDiag').show()");
    }

    /**
     * Ouverture depuis React ({@code siaOpenBulkDocumentImport({targetType, targetId})}).
     * Reprend le brouillon actif (user+org) s'il existe ; les defaults ne s'appliquent qu'aux
     * nouveaux fichiers uploadés pendant cette ouverture.
     */
    public void openFromRemote() {
        var params = jakarta.faces.context.FacesContext.getCurrentInstance()
                .getExternalContext()
                .getRequestParameterMap();
        String targetType = params.get("targetType");
        String targetId = params.get("targetId");
        init();
        applyRemoteDefault(targetType, targetId);
        reloadRowsFromDraft();
        rememberDefaultTarget(targetType, targetId);
        setActionOnComplete(() ->
                PrimeFaces.current().executeScript(
                        "window.dispatchEvent(new CustomEvent('sia-bulk-documents-done'));"));
        show();
    }

    private void rememberDefaultTarget(String targetType, String targetId) {
        Long personId = currentPersonId();
        Long institutionId = currentInstitutionId();
        if (personId == null || institutionId == null) {
            return;
        }
        Long id = null;
        if (StringUtils.hasText(targetId)) {
            try {
                id = Long.parseLong(targetId.trim());
            } catch (NumberFormatException ignored) {
                // keep null
            }
        }
        draftService.rememberDefaultTarget(personId, institutionId, targetType, id);
    }

    private void reloadRowsFromDraft() {
        Long personId = currentPersonId();
        Long institutionId = currentInstitutionId();
        if (personId == null || institutionId == null) {
            rows = new ArrayList<>();
            selectedRow = null;
            draftUpdatedAt = null;
            return;
        }
        DocumentImportDraft draft = draftService.loadDraftWithItems(personId, institutionId);
        draftUpdatedAt = draft.getUpdatedAt();
        rows = draft.getItems().stream().map(this::toRow).collect(Collectors.toCollection(ArrayList::new));
        selectedRow = rows.isEmpty() ? null : rows.get(0);
        draftDirty = false;
    }

    private Long currentPersonId() {
        PersonDTO user = sessionSettingsBean.getUserInfo() != null
                ? sessionSettingsBean.getUserInfo().getUser() : null;
        return user != null ? user.getId() : null;
    }

    private Long currentInstitutionId() {
        InstitutionDTO institution = sessionSettingsBean.getSelectedInstitution();
        return institution != null ? institution.getId() : null;
    }

    public String draftStatusLabel() {
        if (rows.isEmpty()) {
            return langBean.msg("documents.bulk.draft.empty");
        }
        String when = draftUpdatedAt == null ? "" : DRAFT_UPDATED_FMT.format(draftUpdatedAt);
        return langBean.msg("documents.bulk.draft.status", rows.size(), when);
    }

    void applyRemoteDefault(String targetType, String targetId) {
        if (!StringUtils.hasText(targetType) || !StringUtils.hasText(targetId)) {
            return;
        }
        try {
            long id = Long.parseLong(targetId.trim());
            switch (targetType.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "projects" -> setDefaultProject(actionUnitService.findById(id));
                case "recording-units" -> {
                    RecordingUnitDTO full = recordingUnitService.findById(id);
                    setDefaultRecordingUnit(new RecordingUnitSummaryDTO(full));
                    setDefaultProjectFromSummary(full.getActionUnit());
                }
                case "finds" -> {
                    SpecimenDTO specimen = specimenService.findById(id);
                    SpecimenSummaryDTO summary = new SpecimenSummaryDTO();
                    summary.setId(specimen.getId());
                    summary.setFullIdentifier(specimen.getFullIdentifier());
                    setDefaultFind(summary);
                    setDefaultProjectFromSummary(specimen.getActionUnit());
                }
                case "places" -> setDefaultPlace(spatialUnitService.findById(id));
                default -> log.warn("Unknown bulk document targetType: {}", targetType);
            }
        } catch (RuntimeException e) {
            log.warn("Unable to resolve bulk document default target {}/{}: {}", targetType, targetId, e.getMessage());
        }
    }

    private void setDefaultProjectFromSummary(ActionUnitSummaryDTO summary) {
        if (summary == null || summary.getId() == null) {
            return;
        }
        setDefaultProject(actionUnitService.findById(summary.getId()));
    }

    /**
     * Charge les listes de choix multi-sélection pour l'institution courante.
     */
    public void loadTargetOptions() {
        InstitutionDTO institution = sessionSettingsBean.getSelectedInstitution();
        if (institution == null) {
            recordingUnitOptions = new ArrayList<>();
            findOptions = new ArrayList<>();
            placeOptions = new ArrayList<>();
            return;
        }
        recordingUnitOptions = new ArrayList<>(
                recordingUnitService.findMatchingInInstitutionByFullIdentifier(institution, "", TARGET_OPTIONS_LIMIT));
        findOptions = new ArrayList<>(
                specimenService.findMatchingInInstitutionByFullIdentifier(institution, "", TARGET_OPTIONS_LIMIT));
        placeOptions = new ArrayList<>(
                spatialUnitService.findMatchingInInstitutionByName(institution, "", TARGET_OPTIONS_LIMIT));
        ensureDefaultInOptions();
    }

    private void ensureDefaultInOptions() {
        if (defaultRecordingUnit != null && defaultRecordingUnit.getId() != null
                && recordingUnitOptions.stream().noneMatch(o -> defaultRecordingUnit.getId().equals(o.getId()))) {
            recordingUnitOptions.add(0, defaultRecordingUnit);
        }
        if (defaultFind != null && defaultFind.getId() != null
                && findOptions.stream().noneMatch(o -> defaultFind.getId().equals(o.getId()))) {
            findOptions.add(0, defaultFind);
        }
        if (defaultPlace != null && defaultPlace.getId() != null
                && placeOptions.stream().noneMatch(o -> defaultPlace.getId().equals(o.getId()))) {
            placeOptions.add(0, defaultPlace);
        }
    }

    public void handleFileUpload(FileUploadEvent event) {
        UploadedFile file = event.getFile();
        if (file == null) {
            return;
        }
        Long personId = currentPersonId();
        Long institutionId = currentInstitutionId();
        if (personId == null || institutionId == null) {
            MessageUtils.displayErrorMessage(langBean, "documents.nofileset");
            return;
        }
        if (rows.size() >= MAX_ITEMS) {
            MessageUtils.displayErrorMessage(langBean, "documents.bulk.maxItems", MAX_ITEMS);
            return;
        }
        try {
            DocumentImportDraftService.ItemDefaults defaults = currentUploadDefaults();
            DocumentImportItem item = draftService.addFile(
                    personId,
                    institutionId,
                    file.getFileName(),
                    file.getContentType(),
                    file.getInputStream(),
                    defaults);
            BulkDocumentRow row = toRow(item);
            rows.add(row);
            selectedRow = row;
            draftUpdatedAt = OffsetDateTime.now();
        } catch (IllegalStateException e) {
            if ("MAX_ITEMS".equals(e.getMessage())) {
                MessageUtils.displayErrorMessage(langBean, "documents.bulk.maxItems", MAX_ITEMS);
            } else {
                MessageUtils.displayErrorMessage(langBean, "documents.nofileset");
            }
        } catch (IllegalArgumentException e) {
            MessageUtils.displayErrorMessage(langBean, "documents.nofileset");
        } catch (IOException e) {
            log.error("Unable to store draft file {}", file.getFileName(), e);
            MessageUtils.displayErrorMessage(langBean, "documents.nofileset");
        }
    }

    private DocumentImportDraftService.ItemDefaults currentUploadDefaults() {
        Set<Long> projects = new HashSet<>();
        Set<Long> rus = new HashSet<>();
        Set<Long> finds = new HashSet<>();
        Set<Long> places = new HashSet<>();
        if (defaultProject != null && defaultProject.getId() != null) {
            projects.add(defaultProject.getId());
        }
        if (defaultRecordingUnit != null && defaultRecordingUnit.getId() != null) {
            rus.add(defaultRecordingUnit.getId());
        }
        if (defaultFind != null && defaultFind.getId() != null) {
            finds.add(defaultFind.getId());
        }
        if (defaultPlace != null && defaultPlace.getId() != null) {
            places.add(defaultPlace.getId());
        }
        return new DocumentImportDraftService.ItemDefaults(projects, rus, finds, places);
    }

    public void selectRow(BulkDocumentRow row) {
        selectedRow = row;
    }

    public void onRowSelect(SelectEvent<BulkDocumentRow> event) {
        selectedRow = event.getObject();
    }

    /**
     * Marks the draft dirty when the selected row is edited (AJAX listeners).
     * Persistence happens on {@link #saveDraft()} or {@link #submit()}.
     */
    public void persistSelectedRow() {
        if (selectedRow != null) {
            sanitizeTargetLists(selectedRow);
        }
        draftDirty = true;
    }

    /** Persists every row into the draft and keeps the dialog open. */
    public void saveDraft() {
        if (rows.isEmpty()) {
            MessageUtils.displayErrorMessage(langBean, "documents.bulk.noFiles");
            return;
        }
        syncSelectedRowIntoList();
        for (BulkDocumentRow row : rows) {
            sanitizeTargetLists(row);
            persistRow(row);
        }
        draftDirty = false;
        MessageUtils.displayInfoMessage(langBean, "documents.bulk.draft.saved");
    }

    /** Ensures edits on {@link #selectedRow} are visible on the summary row before persist. */
    private void syncSelectedRowIntoList() {
        if (selectedRow == null || selectedRow.getItemId() == null) {
            return;
        }
        sanitizeTargetLists(selectedRow);
        for (int i = 0; i < rows.size(); i++) {
            if (Objects.equals(rows.get(i).getItemId(), selectedRow.getItemId())) {
                rows.set(i, selectedRow);
                return;
            }
        }
    }

    public void persistRow(BulkDocumentRow row) {
        if (row == null || row.getItemId() == null) {
            return;
        }
        Long personId = currentPersonId();
        Long institutionId = currentInstitutionId();
        if (personId == null || institutionId == null) {
            return;
        }
        try {
            DocumentImportItem updated = draftService.updateItemMetadata(
                    personId, institutionId, row.getItemId(), toMetadata(row));
            BulkDocumentRow refreshed = toRow(updated);
            replaceRowInList(refreshed);
            if (selectedRow != null && Objects.equals(selectedRow.getItemId(), refreshed.getItemId())) {
                selectedRow = refreshed;
            }
            draftUpdatedAt = OffsetDateTime.now();
        } catch (RuntimeException e) {
            log.warn("Unable to persist draft item {}: {}", row.getItemId(), e.getMessage());
        }
    }

    /**
     * Close request: confirm only when there are unsaved edits; otherwise just hide.
     * Does not abandon the persisted draft.
     */
    public void requestClose() {
        if (draftDirty) {
            PrimeFaces.current().executeScript("SiaBulkDocDialog.showCloseConfirm();");
        } else {
            PrimeFaces.current().executeScript("PF('bulkDocumentImportDiag').hide();");
        }
    }

    /**
     * User confirmed discarding unsaved edits: reload last saved draft and close.
     * Persisted draft remains available for later resume.
     */
    public void discardUnsavedAndClose() {
        reloadRowsFromDraft();
        draftDirty = false;
    }

    private void replaceRowInList(BulkDocumentRow refreshed) {
        for (int i = 0; i < rows.size(); i++) {
            if (Objects.equals(rows.get(i).getItemId(), refreshed.getItemId())) {
                rows.set(i, refreshed);
                return;
            }
        }
    }

    private DocumentImportDraftService.ItemMetadata toMetadata(BulkDocumentRow row) {
        sanitizeTargetLists(row);
        return new DocumentImportDraftService.ItemMetadata(
                row.getTitle(),
                row.getDescription(),
                conceptId(row.getNature()),
                conceptId(row.getType()),
                conceptId(row.getScale()),
                row.getProjects().stream().map(ActionUnitDTO::getId).filter(Objects::nonNull).collect(Collectors.toSet()),
                row.getRecordingUnits().stream().map(RecordingUnitSummaryDTO::getId).filter(Objects::nonNull).collect(Collectors.toSet()),
                row.getFinds().stream().map(SpecimenSummaryDTO::getId).filter(Objects::nonNull).collect(Collectors.toSet()),
                row.getPlaces().stream().map(SpatialUnitDTO::getId).filter(Objects::nonNull).collect(Collectors.toSet())
        );
    }

    private static Long conceptId(ConceptAutocompleteDTO dto) {
        return dto == null || dto.concept() == null ? null : dto.concept().getId();
    }

    /** Reloads last saved draft (discards unsaved UI edits). Prefer {@link #requestClose()}. */
    public void cancel() {
        discardUnsavedAndClose();
    }

    public void removeRow(BulkDocumentRow row) {
        if (row == null) {
            return;
        }
        Long personId = currentPersonId();
        Long institutionId = currentInstitutionId();
        if (personId != null && institutionId != null && row.getItemId() != null) {
            try {
                draftService.removeItem(personId, institutionId, row.getItemId());
            } catch (RuntimeException e) {
                log.warn("Unable to remove draft item {}: {}", row.getItemId(), e.getMessage());
            }
        }
        String key = row.getClientKey();
        rows.removeIf(r -> key != null && key.equals(r.getClientKey()));
        if (selectedRow != null && key != null && key.equals(selectedRow.getClientKey())) {
            selectedRow = rows.isEmpty() ? null : rows.get(Math.min(rows.size() - 1, 0));
        }
        draftUpdatedAt = OffsetDateTime.now();
    }

    public int countTargets(BulkDocumentRow row) {
        if (row == null) {
            return 0;
        }
        return countTyped(row.getProjects(), ActionUnitDTO.class)
                + countTyped(row.getRecordingUnits(), RecordingUnitSummaryDTO.class)
                + countTyped(row.getFinds(), SpecimenSummaryDTO.class)
                + countTyped(row.getPlaces(), SpatialUnitDTO.class);
    }

    private static int countTyped(List<?> values, Class<?> type) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (Object o : values) {
            if (type.isInstance(o)) {
                n++;
            }
        }
        return n;
    }

    public boolean hasNoTargets(BulkDocumentRow row) {
        return countTargets(row) == 0;
    }

    public String projectsTooltip(BulkDocumentRow row) {
        if (row == null || row.getProjects() == null || row.getProjects().isEmpty()) {
            return "";
        }
        return row.getProjects().stream()
                .map(this::projectLabel)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    public String recordingUnitsTooltip(BulkDocumentRow row) {
        if (row == null || row.getRecordingUnits() == null || row.getRecordingUnits().isEmpty()) {
            return "";
        }
        return row.getRecordingUnits().stream()
                .map(this::recordingUnitLabel)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    public String findsTooltip(BulkDocumentRow row) {
        if (row == null || row.getFinds() == null || row.getFinds().isEmpty()) {
            return "";
        }
        return row.getFinds().stream()
                .map(this::findLabel)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    public String placesTooltip(BulkDocumentRow row) {
        if (row == null || row.getPlaces() == null || row.getPlaces().isEmpty()) {
            return "";
        }
        return row.getPlaces().stream()
                .map(this::placeLabel)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    /** Premiers libellés de cibles à afficher en chips (tous types confondus). */
    public List<String> previewTargetLabels(BulkDocumentRow row) {
        if (row == null) {
            return List.of();
        }
        List<String> labels = new ArrayList<>();
        if (row.getProjects() != null) {
            for (Object o : row.getProjects()) {
                if (!(o instanceof ActionUnitDTO project)) {
                    continue;
                }
                if (labels.size() >= 4) {
                    break;
                }
                labels.add(projectLabel(project));
            }
        }
        if (row.getRecordingUnits() != null && labels.size() < 4) {
            for (Object o : row.getRecordingUnits()) {
                if (!(o instanceof RecordingUnitSummaryDTO ru)) {
                    continue;
                }
                if (labels.size() >= 4) {
                    break;
                }
                labels.add(recordingUnitLabel(ru));
            }
        }
        if (row.getFinds() != null && labels.size() < 4) {
            for (Object o : row.getFinds()) {
                if (!(o instanceof SpecimenSummaryDTO find)) {
                    continue;
                }
                if (labels.size() >= 4) {
                    break;
                }
                labels.add(findLabel(find));
            }
        }
        if (row.getPlaces() != null && labels.size() < 4) {
            for (Object o : row.getPlaces()) {
                if (!(o instanceof SpatialUnitDTO place)) {
                    continue;
                }
                if (labels.size() >= 4) {
                    break;
                }
                labels.add(placeLabel(place));
            }
        }
        return labels;
    }

    private String projectLabel(ActionUnitDTO project) {
        if (project == null) {
            return "";
        }
        return project.getName() != null ? project.getName() : "#" + project.getId();
    }

    private String recordingUnitLabel(RecordingUnitSummaryDTO ru) {
        if (ru == null) {
            return "";
        }
        return ru.getFullIdentifier() != null ? ru.getFullIdentifier() : "#" + ru.getId();
    }

    private String findLabel(SpecimenSummaryDTO find) {
        if (find == null) {
            return "";
        }
        return find.getFullIdentifier() != null ? find.getFullIdentifier() : "#" + find.getId();
    }

    private String placeLabel(SpatialUnitDTO place) {
        if (place == null) {
            return "";
        }
        return place.getName() != null ? place.getName() : "#" + place.getId();
    }

    /**
     * AutoComplete/selectCheckboxMenu can leave raw Strings in typed lists when converters are
     * missing or fail; drop anything that is not the expected DTO type.
     */
    @SuppressWarnings("rawtypes")
    private void sanitizeTargetLists(BulkDocumentRow row) {
        if (row == null) {
            return;
        }
        if (row.getProjects() != null) {
            List cleaned = new ArrayList<>();
            for (Object o : (List) row.getProjects()) {
                if (o instanceof ActionUnitDTO dto) {
                    cleaned.add(dto);
                }
            }
            row.setProjects(cleaned);
        }
        if (row.getRecordingUnits() != null) {
            List cleaned = new ArrayList<>();
            for (Object o : (List) row.getRecordingUnits()) {
                if (o instanceof RecordingUnitSummaryDTO dto) {
                    cleaned.add(dto);
                }
            }
            row.setRecordingUnits(cleaned);
        }
        if (row.getFinds() != null) {
            List cleaned = new ArrayList<>();
            for (Object o : (List) row.getFinds()) {
                if (o instanceof SpecimenSummaryDTO dto) {
                    cleaned.add(dto);
                }
            }
            row.setFinds(cleaned);
        }
        if (row.getPlaces() != null) {
            List cleaned = new ArrayList<>();
            for (Object o : (List) row.getPlaces()) {
                if (o instanceof SpatialUnitDTO dto) {
                    cleaned.add(dto);
                }
            }
            row.setPlaces(cleaned);
        }
    }

    public int remainingTargetsCount(BulkDocumentRow row) {
        return Math.max(0, countTargets(row) - previewTargetLabels(row).size());
    }

    public String targetSummary(BulkDocumentRow row) {
        if (row == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        if (!row.getProjects().isEmpty()) {
            parts.add(langBean.msg("documents.bulk.summary.projects", row.getProjects().size()));
        }
        if (!row.getRecordingUnits().isEmpty()) {
            parts.add(langBean.msg("documents.bulk.summary.recordingUnits", row.getRecordingUnits().size()));
        }
        if (!row.getFinds().isEmpty()) {
            parts.add(langBean.msg("documents.bulk.summary.finds", row.getFinds().size()));
        }
        if (!row.getPlaces().isEmpty()) {
            parts.add(langBean.msg("documents.bulk.summary.places", row.getPlaces().size()));
        }
        if (parts.isEmpty()) {
            return langBean.msg("documents.bulk.summary.noTargets");
        }
        return String.join(" · ", parts);
    }

    public boolean isRowReady(BulkDocumentRow row) {
        if (row == null) {
            return false;
        }
        return StringUtils.hasText(row.getTitle())
                && row.getNature() != null
                && row.getType() != null
                && countTargets(row) > 0
                && row.getItemId() != null
                && StringUtils.hasText(row.getMd5Sum())
                && !isFullDuplicate(row);
    }

    /**
     * True si le fichier est déjà rattaché à toutes les cibles sélectionnées (rien à créer).
     */
    public boolean isFullDuplicate(BulkDocumentRow row) {
        if (row == null || !StringUtils.hasText(row.getMd5Sum()) || countTargets(row) == 0) {
            return false;
        }
        return countNewTargets(row, row.getMd5Sum(), Set.of()) == 0;
    }

    public String rowReadinessLabel(BulkDocumentRow row) {
        if (isRowReady(row)) {
            return langBean.msg("documents.bulk.status.ready");
        }
        if (row == null) {
            return "";
        }
        if (!StringUtils.hasText(row.getTitle())) {
            return langBean.msg("documents.titleRequired");
        }
        if (row.getNature() == null) {
            return langBean.msg("documents.natureRequired");
        }
        if (row.getType() == null) {
            return langBean.msg("documents.typeRequired");
        }
        if (countTargets(row) == 0) {
            return langBean.msg("documents.bulk.targetRequired");
        }
        if (isFullDuplicate(row)) {
            return langBean.msg("documents.bulk.alreadyExists");
        }
        return langBean.msg("documents.bulk.status.incomplete");
    }

    public long readyCount() {
        return rows.stream().filter(this::isRowReady).count();
    }

    public boolean isSubmitEnabled() {
        return !rows.isEmpty() && rows.stream().allMatch(this::isRowReady);
    }

    /** Save-as-draft is offered while at least one document is incomplete. */
    public boolean isSaveDraftEnabled() {
        return !rows.isEmpty() && !isSubmitEnabled();
    }

    public List<ConceptAutocompleteDTO> autocompleteNature(String input) throws NoConfigForFieldException {
        return fieldConfigurationService.fetchAutocomplete(
                sessionSettingsBean.getUserInfo(), Document.NATURE_FIELD_CODE, input);
    }

    public List<ConceptAutocompleteDTO> autocompleteScale(String input) throws NoConfigForFieldException {
        return fieldConfigurationService.fetchAutocomplete(
                sessionSettingsBean.getUserInfo(), Document.SCALE_FIELD_CODE, input);
    }

    public List<ConceptAutocompleteDTO> autocompleteType(String input) throws NoConfigForFieldException {
        return fieldConfigurationService.fetchAutocomplete(
                sessionSettingsBean.getUserInfo(), Document.FORMAT_FIELD_CODE, input);
    }

    public List<ActionUnitDTO> completeProject(String query) {
        InstitutionDTO institution = sessionSettingsBean.getSelectedInstitution();
        if (institution == null) {
            return List.of();
        }
        return actionUnitService.findMatchingInInstitutionByName(institution, query, AUTOCOMPLETE_LIMIT);
    }

    public List<RecordingUnitSummaryDTO> completeRecordingUnit(String query) {
        InstitutionDTO institution = sessionSettingsBean.getSelectedInstitution();
        if (institution == null) {
            return List.of();
        }
        return recordingUnitService.findMatchingInInstitutionByFullIdentifier(institution, query, AUTOCOMPLETE_LIMIT);
    }

    public List<SpecimenSummaryDTO> completeFind(String query) {
        InstitutionDTO institution = sessionSettingsBean.getSelectedInstitution();
        if (institution == null) {
            return List.of();
        }
        return specimenService.findMatchingInInstitutionByFullIdentifier(institution, query, AUTOCOMPLETE_LIMIT);
    }

    public List<SpatialUnitDTO> completePlace(String query) {
        InstitutionDTO institution = sessionSettingsBean.getSelectedInstitution();
        if (institution == null) {
            return List.of();
        }
        return spatialUnitService.findMatchingInInstitutionByName(institution, query, AUTOCOMPLETE_LIMIT);
    }

    public void submit() {
        reportLines = new ArrayList<>();
        if (rows.isEmpty()) {
            MessageUtils.displayErrorMessage(langBean, "documents.bulk.noFiles");
            return;
        }
        // Persist latest edits before sending.
        for (BulkDocumentRow row : rows) {
            persistRow(row);
        }
        draftDirty = false;
        if (!isSubmitEnabled()) {
            MessageUtils.displayErrorMessage(langBean, "documents.bulk.notAllReady");
            return;
        }

        UserInfo userInfo = sessionSettingsBean.getUserInfo();
        Long personId = currentPersonId();
        Long institutionId = currentInstitutionId();
        int created = 0;
        int failed = 0;
        Set<String> linkedInBatch = new HashSet<>();
        List<BulkDocumentRow> remaining = new ArrayList<>();

        for (BulkDocumentRow row : rows) {
            String outcome = processRow(userInfo, row, linkedInBatch);
            reportLines.add(row.getFileName() + " — " + outcome);
            if (outcome.startsWith("OK")) {
                created++;
                if (personId != null && institutionId != null && row.getItemId() != null) {
                    draftService.removeItemAfterSuccessfulSubmit(personId, institutionId, row.getItemId());
                }
            } else {
                failed++;
                remaining.add(row);
                if (personId != null && institutionId != null && row.getItemId() != null) {
                    draftService.markItemError(personId, institutionId, row.getItemId(), outcome);
                }
            }
        }

        rows = remaining;
        selectedRow = rows.isEmpty() ? null : rows.get(0);
        reportVisible = true;
        MessageUtils.displayInfoMessage(langBean, "documents.bulk.done", created, failed);

        if (created > 0 && actionOnComplete != null) {
            actionOnComplete.apply();
        }

        if (created > 0 && failed == 0) {
            PrimeFaces.current().executeScript(
                    "if (PF('buiBulkSubmit')) { PF('buiBulkSubmit').hide(); } PF('bulkDocumentImportDiag').hide()");
        } else {
            PrimeFaces.current().ajax().update("bulkDocumentImport");
        }
    }

    private String processRow(UserInfo userInfo, BulkDocumentRow row, Set<String> linkedInBatch) {
        if (!StringUtils.hasText(row.getTitle())) {
            return langBean.msg("documents.titleRequired");
        }
        if (row.getNature() == null) {
            return langBean.msg("documents.natureRequired");
        }
        if (row.getType() == null) {
            return langBean.msg("documents.typeRequired");
        }
        if (row.getItemId() == null) {
            return langBean.msg("documents.nofileset");
        }
        boolean hasTarget = !row.getProjects().isEmpty()
                || !row.getRecordingUnits().isEmpty()
                || !row.getFinds().isEmpty()
                || !row.getPlaces().isEmpty();
        if (!hasTarget) {
            return langBean.msg("documents.bulk.targetRequired");
        }

        Long personId = currentPersonId();
        Long institutionId = currentInstitutionId();
        byte[] fileContent;
        try {
            fileContent = draftService.readFileBytes(personId, institutionId, row.getItemId());
        } catch (IOException e) {
            log.error("IO error reading draft file for item {}", row.getItemId(), e);
            return "IO error";
        }
        if (fileContent == null || fileContent.length == 0) {
            return langBean.msg("documents.nofileset");
        }
        if (!StringUtils.hasText(row.getMd5Sum())) {
            row.setMd5Sum(DocumentUtils.md5(fileContent));
        }
        String md5 = row.getMd5Sum();

        int newTargets = countNewTargets(row, md5, linkedInBatch);
        if (newTargets == 0) {
            return langBean.msg("documents.bulk.alreadyExists");
        }

        Document document = buildDocument(row);
        try (InputStream inputStream = new ByteArrayInputStream(fileContent)) {
            document = documentService.saveFile(userInfo, document, inputStream, servletContext.getContextPath());
        } catch (InvalidFileTypeException e) {
            return langBean.msg("documents.unsupportedtype");
        } catch (InvalidFileSizeException e) {
            return langBean.msg("documents.toolarge");
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate document rejected by database for {}", row.getFileName(), e);
            return langBean.msg("documents.bulk.alreadyExists");
        } catch (IOException e) {
            log.error("IO error on bulk document upload", e);
            return "IO error";
        }

        int linked = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        for (ActionUnitDTO project : row.getProjects()) {
            String key = targetKey("projects", project.getId());
            if (linkedInBatch.contains(key) || documentService.existInActionUnitByHash(project, md5)) {
                skipped++;
                continue;
            }
            LinkOutcome out = linkProject(userInfo, document, project);
            if (out == LinkOutcome.LINKED) {
                linked++;
                linkedInBatch.add(key);
            } else if (out == LinkOutcome.SKIPPED) {
                skipped++;
                linkedInBatch.add(key);
            } else {
                errors.add("project/" + project.getId());
            }
        }
        for (RecordingUnitSummaryDTO summary : row.getRecordingUnits()) {
            String key = targetKey("recording-units", summary.getId());
            RecordingUnitDTO ru = recordingUnitService.findById(summary.getId());
            if (linkedInBatch.contains(key) || documentService.existInRecordingUnitByHash(ru, md5)) {
                skipped++;
                continue;
            }
            LinkOutcome out = linkRecordingUnit(userInfo, document, ru);
            if (out == LinkOutcome.LINKED) {
                linked++;
                linkedInBatch.add(key);
            } else if (out == LinkOutcome.SKIPPED) {
                skipped++;
                linkedInBatch.add(key);
            } else {
                errors.add("recording-unit/" + summary.getId());
            }
        }
        for (SpecimenSummaryDTO summary : row.getFinds()) {
            String key = targetKey("finds", summary.getId());
            SpecimenDTO specimen = specimenService.findById(summary.getId());
            if (linkedInBatch.contains(key) || documentService.existInSpecimenByHash(specimen, md5)) {
                skipped++;
                continue;
            }
            LinkOutcome out = linkFind(userInfo, document, specimen);
            if (out == LinkOutcome.LINKED) {
                linked++;
                linkedInBatch.add(key);
            } else if (out == LinkOutcome.SKIPPED) {
                skipped++;
                linkedInBatch.add(key);
            } else {
                errors.add("find/" + summary.getId());
            }
        }
        for (SpatialUnitDTO place : row.getPlaces()) {
            String key = targetKey("places", place.getId());
            if (linkedInBatch.contains(key) || documentService.existInSpatialUnitByHash(place, md5)) {
                skipped++;
                continue;
            }
            LinkOutcome out = linkPlace(userInfo, document, place);
            if (out == LinkOutcome.LINKED) {
                linked++;
                linkedInBatch.add(key);
            } else if (out == LinkOutcome.SKIPPED) {
                skipped++;
                linkedInBatch.add(key);
            } else {
                errors.add("place/" + place.getId());
            }
        }

        if (linked == 0 && !errors.isEmpty()) {
            return "FAILED: " + String.join(", ", errors);
        }
        if (linked == 0) {
            return langBean.msg("documents.bulk.alreadyExists");
        }
        StringBuilder ok = new StringBuilder("OK (linked=").append(linked).append(", skipped=").append(skipped).append(")");
        if (!errors.isEmpty()) {
            ok.append("; refused: ").append(String.join(", ", errors));
        }
        return ok.toString();
    }

    private int countNewTargets(BulkDocumentRow row, String md5, Set<String> linkedInBatch) {
        int count = 0;
        for (ActionUnitDTO project : row.getProjects()) {
            String key = targetKey("projects", project.getId());
            if (!linkedInBatch.contains(key) && !documentService.existInActionUnitByHash(project, md5)) {
                count++;
            }
        }
        for (RecordingUnitSummaryDTO summary : row.getRecordingUnits()) {
            String key = targetKey("recording-units", summary.getId());
            if (linkedInBatch.contains(key)) {
                continue;
            }
            RecordingUnitDTO ru = recordingUnitService.findById(summary.getId());
            if (!documentService.existInRecordingUnitByHash(ru, md5)) {
                count++;
            }
        }
        for (SpecimenSummaryDTO summary : row.getFinds()) {
            String key = targetKey("finds", summary.getId());
            if (linkedInBatch.contains(key)) {
                continue;
            }
            SpecimenDTO specimen = specimenService.findById(summary.getId());
            if (!documentService.existInSpecimenByHash(specimen, md5)) {
                count++;
            }
        }
        for (SpatialUnitDTO place : row.getPlaces()) {
            String key = targetKey("places", place.getId());
            if (!linkedInBatch.contains(key) && !documentService.existInSpatialUnitByHash(place, md5)) {
                count++;
            }
        }
        return count;
    }

    private static String targetKey(String type, Long id) {
        return type + "/" + id;
    }

    private Document buildDocument(BulkDocumentRow row) {
        Document document = new Document();
        document.setTitle(row.getTitle().trim());
        document.setDescription(row.getDescription() == null ? null : row.getDescription().trim());
        document.replaceNatureBy(resolveConcept(row.getNature()));
        document.setScale(resolveConcept(row.getScale()));
        document.setFormatConcept(resolveConcept(row.getType()));
        document.setFileName(row.getFileName());
        document.setMimeType(row.getContentType());
        document.setSize(row.getFileSize());

        ActionUnitDTO owningProject = row.getProjects().isEmpty() ? null : row.getProjects().get(0);
        if (owningProject != null) {
            document.setActionUnit(actionUnitMapper.invertConvert(owningProject));
        }

        if (Boolean.TRUE.equals(sessionSettingsBean.getInstitutionSettings().getArkIsEnabled())) {
            Ark ark = arkService.generateAndSave(sessionSettingsBean.getInstitutionSettings());
            document.setArk(ark);
        }
        return document;
    }

    private Concept resolveConcept(ConceptAutocompleteDTO dto) {
        if (dto == null || dto.concept() == null) {
            return null;
        }
        return conceptService.findById(dto.concept().getId())
                .orElseThrow(() -> new IllegalArgumentException("No concept for id " + dto.concept().getId()));
    }

    private LinkOutcome linkProject(UserInfo userInfo, Document document, ActionUnitDTO project) {
        // Aligné sur la création unitaire / API projet : pas d'exigence PROJECT_MANAGE_SETTINGS.
        if (project == null || project.getId() == null) {
            return LinkOutcome.REFUSED;
        }
        if (document.getMd5Sum() != null && documentService.existInActionUnitByHash(project, document.getMd5Sum())) {
            return LinkOutcome.SKIPPED;
        }
        try {
            documentService.addToActionUnit(document, project);
            return LinkOutcome.LINKED;
        } catch (DataIntegrityViolationException e) {
            return LinkOutcome.SKIPPED;
        }
    }

    private LinkOutcome linkRecordingUnit(UserInfo userInfo, Document document, RecordingUnitDTO ru) {
        if (!profilePermissionService.hasRecordingUnitWritePermission(userInfo, ru)) {
            return LinkOutcome.REFUSED;
        }
        if (document.getMd5Sum() != null && documentService.existInRecordingUnitByHash(ru, document.getMd5Sum())) {
            return LinkOutcome.SKIPPED;
        }
        try {
            documentService.addToRecordingUnit(document, ru);
            return LinkOutcome.LINKED;
        } catch (DataIntegrityViolationException e) {
            return LinkOutcome.SKIPPED;
        }
    }

    private LinkOutcome linkFind(UserInfo userInfo, Document document, SpecimenDTO specimen) {
        if (!profilePermissionService.hasSpecimenWritePermission(userInfo, specimen)) {
            return LinkOutcome.REFUSED;
        }
        if (document.getMd5Sum() != null && documentService.existInSpecimenByHash(specimen, document.getMd5Sum())) {
            return LinkOutcome.SKIPPED;
        }
        try {
            documentService.addToSpecimen(document, specimen);
            return LinkOutcome.LINKED;
        } catch (DataIntegrityViolationException e) {
            return LinkOutcome.SKIPPED;
        }
    }

    private LinkOutcome linkPlace(UserInfo userInfo, Document document, SpatialUnitDTO place) {
        // Aligné sur la création unitaire : pas d'exigence ORGANIZATION_MANAGE_PLACES ici.
        if (place == null || place.getId() == null) {
            return LinkOutcome.REFUSED;
        }
        if (document.getMd5Sum() != null && documentService.existInSpatialUnitByHash(place, document.getMd5Sum())) {
            return LinkOutcome.SKIPPED;
        }
        try {
            documentService.addToSpatialUnit(document, place);
            return LinkOutcome.LINKED;
        } catch (DataIntegrityViolationException e) {
            return LinkOutcome.SKIPPED;
        }
    }

    public String regexSupportedTypes() {
        List<MimeType> supported = documentService.supportedMimeTypes();
        return DocumentUtils.allowedTypesRegex(supported);
    }

    public String allowedExtensions() {
        List<MimeType> supported = documentService.supportedMimeTypes();
        return DocumentUtils.allowedExtensionsStringList(supported);
    }

    public long maxFileSize() {
        return documentService.maxFileSize();
    }

    public int maxTitleLength() {
        return Document.MAX_TITLE_LENGTH;
    }

    public int maxDescriptionSize() {
        return Document.MAX_DESCRIPTION_LENGTH;
    }

    private enum LinkOutcome {
        LINKED, SKIPPED, REFUSED
    }

    @Data
    public static class BulkDocumentRow implements Serializable {
        private Long itemId;
        private String clientKey;
        private String fileName;
        private String contentType;
        private long fileSize;
        /** Empreinte MD5 du contenu, calculée à l'upload pour détecter les doublons. */
        private String md5Sum;
        private String title;
        private String description;
        private ConceptAutocompleteDTO nature;
        private ConceptAutocompleteDTO scale;
        private ConceptAutocompleteDTO type;
        private List<ActionUnitDTO> projects = new ArrayList<>();
        private List<RecordingUnitSummaryDTO> recordingUnits = new ArrayList<>();
        private List<SpecimenSummaryDTO> finds = new ArrayList<>();
        private List<SpatialUnitDTO> places = new ArrayList<>();

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof BulkDocumentRow that)) {
                return false;
            }
            return clientKey != null && clientKey.equals(that.clientKey);
        }

        @Override
        public int hashCode() {
            return clientKey != null ? clientKey.hashCode() : 0;
        }
    }

    private BulkDocumentRow toRow(DocumentImportItem item) {
        BulkDocumentRow row = new BulkDocumentRow();
        row.setItemId(item.getId());
        row.setClientKey(item.getClientKey());
        row.setFileName(item.getOriginalFileName());
        row.setContentType(item.getContentType());
        row.setFileSize(item.getFileSize() == null ? 0L : item.getFileSize());
        row.setMd5Sum(item.getMd5Sum());
        row.setTitle(item.getTitle());
        row.setDescription(item.getDescription());
        row.setNature(toAutocomplete(item.getNature()));
        row.setType(toAutocomplete(item.getFormatConcept()));
        row.setScale(toAutocomplete(item.getScale()));
        row.setProjects(item.getProjects().stream().map(actionUnitMapper::convert).collect(Collectors.toCollection(ArrayList::new)));
        row.setRecordingUnits(item.getRecordingUnits().stream().map(recordingUnitSummaryMapper::convert).collect(Collectors.toCollection(ArrayList::new)));
        row.setFinds(item.getFinds().stream().map(specimenSummaryMapper::convert).collect(Collectors.toCollection(ArrayList::new)));
        row.setPlaces(item.getPlaces().stream().map(spatialUnitMapper::convert).collect(Collectors.toCollection(ArrayList::new)));
        return row;
    }

    private ConceptAutocompleteDTO toAutocomplete(Concept concept) {
        if (concept == null) {
            return null;
        }
        String lang = sessionSettingsBean.getUserInfo() != null ? sessionSettingsBean.getUserInfo().getLang() : "fr";
        String label = labelService.findLabelOf(concept, lang).getLabel();
        return new ConceptAutocompleteDTO(conceptMapper.convert(concept), label, lang);
    }
}