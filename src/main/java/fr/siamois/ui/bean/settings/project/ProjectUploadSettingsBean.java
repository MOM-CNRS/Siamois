package fr.siamois.ui.bean.settings.project;


import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.misc.ImportProgress;
import fr.siamois.domain.models.misc.SeedCounts;
import fr.siamois.domain.services.dataimport.ImportAsyncRunner;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.infrastructure.database.initializer.seeder.ProjectDataSeeder;
import fr.siamois.infrastructure.database.initializer.seeder.SeedException;
import fr.siamois.infrastructure.dataimport.*;
import fr.siamois.utils.MessageUtils;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.primefaces.PrimeFaces;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;
import org.primefaces.model.file.UploadedFile;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
@Data
public class ProjectUploadSettingsBean {

    public static final String TEMPLATE_FORM_CC_TEMPLATE_FORM_TEMPLATE_GROWL = "templateFormCC:templateForm:templateGrowl";
    public static final String FEUILLE = " feuille";
    public static final String ERREUR = " erreur";
    private static final String SEVERITY_ERROR = "error";
    private static final String SEVERITY_WARN = "warn";
    public static final String PHASE = "phase";
    public static final String STRATI = "strati";
    public static final String UE_REL = "uerel";
    public static final String LIEU_REL = "lieurel";
    public static final String LIGNE = " ligne";

    @Value
    public static class SheetMappingView {
        String sheetName;
        String tableId;
        List<ColumnAliasView> columnAliases;
        boolean unmapped;
        public boolean hasAliases() { return !columnAliases.isEmpty(); }
        /** French label for the table this sheet maps to (falls back to the technical ID if unknown). */
        public String getTableLabel() { return ImportSchema.TABLE_LABELS.getOrDefault(tableId, tableId); }
        public int getUnmappedColumnCount() {
            return (int) columnAliases.stream().filter(ColumnAliasView::isColumnUnmapped).count();
        }
        public boolean isHasUnmappedColumns() { return getUnmappedColumnCount() > 0; }
    }

    @Value
    public static class ColumnAliasView {
        String alias;
        String canonical;
        boolean columnUnmapped;
    }

    /**
     * One row in the post-import summary, representing what actually happened in the DB for one
     * entity type: rows created, rows updated (upsert match onto an already-existing entity), and
     * rows ignored as duplicates of another row within the same file. For entity types without an
     * upsert-aware seeder, {@link #simple(String, int)} reports only a plain row count, matching
     * today's behavior for those types.
     */
    @Value
    public static class ImportSummaryRow {
        String label;
        int created;
        int updated;
        int skippedDuplicate;

        public static ImportSummaryRow simple(String label, int count) {
            return new ImportSummaryRow(label, count, 0, 0);
        }

        public String getSummaryLabel() {
            List<String> parts = new ArrayList<>();
            if (created > 0) parts.add(created + LIGNE + pluralSuffix(created) + " créée" + pluralSuffix(created));
            if (updated > 0) parts.add(updated + LIGNE + pluralSuffix(updated) + " modifiée" + pluralSuffix(updated));
            if (skippedDuplicate > 0) {
                parts.add(skippedDuplicate + " doublon" + pluralSuffix(skippedDuplicate) + " ignoré" + pluralSuffix(skippedDuplicate));
            }
            return parts.isEmpty() ? "0" + LIGNE : String.join(", ", parts);
        }
    }

    /** One tab in the validation section, representing one entity type. */
    @Value
    public static class ValidationTabView {
        String tableId;
        String label;
        List<ImportError> errors;
        int totalCount;
        public int getErrorCount() { return errors.size(); }
        public boolean hasErrors() { return !errors.isEmpty(); }
        /** True when this tab's errors hit the global collection cap — some errors from this sheet may not be shown. */
        public boolean isErrorsCapped() { return errors.size() >= ExcelCellHelper.MAX_ERRORS; }
    }

    private final OOXMLImportService importService;
    private final ProjectDataSeeder seeder;
    private final ImportAsyncRunner asyncRunner;

    ActionUnitDTO project;
    UploadedFile originalFile;
    StreamedContent templateFile;
    ImportResult importResult;
    List<ImportError> persistenceErrors = new ArrayList<>();
    boolean readyToUpload = false;
    String uploadedFileName = "";
    long uploadedFileSize = 0;

    boolean importDone = false;
    List<ImportSummaryRow> importSummary = new ArrayList<>();
    int importedTotal = 0;
    int importedCreated = 0;
    int importedUpdated = 0;
    int importedSkippedDuplicate = 0;

    final ImportProgress progress = new ImportProgress();
    final SeedCounts seedCounts = new SeedCounts();
    // set by the background parse/persist task once it finishes, flushed to a real growl message
    // by pollProgress() — never touched from the background thread itself, since FacesContext is
    // thread-local to the request thread and would be null/stale there.
    private volatile String pendingGrowlSeverity;
    private volatile String pendingGrowlTarget;
    private volatile String pendingGrowlSummary;
    private volatile String pendingGrowlDetail;

    public void init(ActionUnitDTO project) {
        reset();
        this.project = project;

        templateFile = DefaultStreamedContent.builder()
                .name("Import_Chartres_Projet.xlsx")
                .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .stream(() -> {
                    try {
                        return new ClassPathResource(
                                "datasets/Import_Chartres_Projet.xlsx")
                                .getInputStream();
                    } catch (IOException e) {

                        throw new UncheckedIOException(e);
                    }
                })
                .build();
    }

    @EventListener(LoginEvent.class)
    public void reset() {
        project = null;
        clearImportState();
    }

    /**
     * Clears the parse/persist flow state but keeps {@link #project} set — used when finishing or
     * restarting an import while staying on the page, so that {@code ProjectDetailsBean.checkProjectOrRedirect()}'s
     * resync guard (triggered whenever {@link #project} is null) doesn't re-run {@link #init} and wipe
     * the just-set {@link #importDone} summary before it can be rendered.
     */
    private void clearImportState() {
        readyToUpload = false;
        importResult = null;
        originalFile = null;
        persistenceErrors = new ArrayList<>();
        uploadedFileName = "";
        uploadedFileSize = 0;
        importDone = false;
        importSummary = new ArrayList<>();
        importedTotal = 0;
        importedCreated = 0;
        importedUpdated = 0;
        importedSkippedDuplicate = 0;
        seedCounts.reset();
    }

    public void resetForNewFile() {
        importResult = null;
        persistenceErrors = new ArrayList<>();
        readyToUpload = false;
        uploadedFileName = "";
        uploadedFileSize = 0;
    }

    public String getUploadedFileSizeLabel() {
        if (uploadedFileSize <= 0) return "";
        if (uploadedFileSize < 1024) return uploadedFileSize + " o";
        if (uploadedFileSize < 1024 * 1024) return (uploadedFileSize / 1024) + " Ko";
        return String.format("%.1f Mo", uploadedFileSize / (1024.0 * 1024));
    }

    public String getUploadedFileDetail() {
        if (importResult == null) return "";
        int sheets = importResult.meta().tableToSheets().size();
        return sheets + FEUILLE + (sheets > 1 ? "s" : "") + " · mapping détecté";
    }

    public String getMappingHeadBg() {
        return isMappingOk() ? "#f2f7f2" : "#fffbe9";
    }

    public String getValidationHeadBg() {
        return isImportBlocked() ? "#fdf1f0" : "#f2f7f2";
    }

    // ─── Sheet mapping ──────────────────────────────────────────────────────

    public List<SheetMappingView> getSheetMappings() {
        if (importResult == null) return List.of();
        SheetMetadata meta = importResult.meta();
        Map<String, String> sheetToTable = buildSheetToTableMap(meta);

        List<SheetMappingView> result = new ArrayList<>();
        for (Map.Entry<String, List<String>> sheetEntry : importResult.allSheetColumns().entrySet()) {
            result.add(buildSheetMappingView(sheetEntry.getKey(), sheetEntry.getValue(), sheetToTable, meta));
        }
        return result;
    }

    /** sheetName → tableId, derived from the (possibly multi-sheet-per-table) tableToSheets mapping. */
    private Map<String, String> buildSheetToTableMap(SheetMetadata meta) {
        Map<String, String> sheetToTable = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : meta.tableToSheets().entrySet()) {
            for (String sheetName : entry.getValue()) {
                sheetToTable.put(sheetName, entry.getKey());
            }
        }
        return sheetToTable;
    }

    private SheetMappingView buildSheetMappingView(String sheetName, List<String> columns,
                                                    Map<String, String> sheetToTable, SheetMetadata meta) {
        String tableId = sheetToTable.get(sheetName);
        boolean unmapped = (tableId == null);

        // aliases already combines explicit _meta aliases with auto-matched canonical names
        // (columns whose normalized header equals one of EXPECTED_COLUMNS for this table) —
        // see OOXMLImportService.readSheetMetadata(). Anything not in this map is genuinely
        // unmapped: neither a known default column nor aliased. An unmapped sheet has no
        // aliases at all, so every one of its columns naturally falls into that case below.
        Map<String, String> aliases = unmapped ? Map.of() : meta.columnAliases().getOrDefault(sheetName, Map.of());

        List<ColumnAliasView> aliasList = new ArrayList<>();
        for (String col : columns) {
            aliasList.add(buildColumnAliasView(col, aliases));
        }

        return new SheetMappingView(sheetName, unmapped ? "" : tableId, aliasList, unmapped);
    }

    private ColumnAliasView buildColumnAliasView(String col, Map<String, String> aliases) {
        String norm = ExcelCellHelper.normalize(col);
        if (aliases.containsKey(norm)) {
            return new ColumnAliasView(col, aliases.get(norm), false);
        }
        return new ColumnAliasView(col, "", true);
    }

    public String getMappingSummary() {
        if (importResult == null) return "";
        int sheets = getSheetMappings().size();
        long tables = importResult.meta().tableToSheets().size();
        return sheets + FEUILLE + (sheets > 1 ? "s" : "") + " → " + tables + " table" + (tables > 1 ? "s" : "");
    }

    public boolean isMappingOk() {
        return importResult != null && getUnmappedSheetCount() == 0 && getUnmappedColumnCount() == 0;
    }

    /** Sheets not recognized as belonging to any table. */
    public int getUnmappedSheetCount() {
        return (int) getSheetMappings().stream().filter(SheetMappingView::isUnmapped).count();
    }

    /** Unmapped columns within recognized sheets (columns of an already-unmapped sheet aren't counted twice). */
    public int getUnmappedColumnCount() {
        return getSheetMappings().stream()
                .filter(m -> !m.isUnmapped())
                .mapToInt(SheetMappingView::getUnmappedColumnCount)
                .sum();
    }

    // ─── Errors ─────────────────────────────────────────────────────────────

    /** Combined parsing errors + persistence errors. */
    public List<ImportError> getImportErrors() {
        List<ImportError> all = new ArrayList<>(importResult == null ? List.of() : importResult.errors());
        all.addAll(persistenceErrors);
        return all;
    }

    public int getErrorCount() {
        return getImportErrors().size();
    }

    /** Display label for an error's "sheet" — real sheet names pass through, table IDs (from persistence errors) get their French label. */
    public String getErrorSheetLabel(ImportError e) {
        return ImportSchema.TABLE_LABELS.getOrDefault(e.sheet(), e.sheet());
    }

    public boolean isImportBlocked() {
        return getErrorCount() > 0;
    }

    // ─── Validation tabs ─────────────────────────────────────────────────────

    private Map<String, String> buildSheetToKeyMap() {
        if (importResult == null) return Map.of();
        Map<String, String> map = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : importResult.meta().tableToSheets().entrySet()) {
            String tableId = entry.getKey();
            String key = resolveTableKey(tableId);
            for (String sheet : entry.getValue()) {
                map.put(sheet, key);
            }
        }
        return map;
    }

    private String resolveTableKey(String tableId) {
        // handles both snake_case IDs from _meta sheet and French display names from DEFAULT_SHEET_NAMES
        return switch (tableId) {
            case "lieu", "spatial_unit"                          -> "lieu";
            case "ue", "recording_unit"                          -> "ue";
            case "relation stratigraphique", "stratirel", "stratiRel",
                 "recording_unit_strati_rel"                     -> STRATI;
            case "groupement d'ue", "recordingrel", "recordingRel",
                 "recording_unit_rel"                            -> UE_REL;
            case "relations lieu", "spatialunitrel", "spatialUnitRel" -> LIEU_REL;
            case "specimen", "mobilier"                          -> "mob";
            case PHASE -> PHASE;
            default                                              -> tableId;
        };
    }

    private int getSpecCountForKey(String key) {
        if (importResult == null) return 0;
        var specs = importResult.specs();
        return switch (key) {
            case "lieu"   -> specs.spatialUnits().size();
            case "ue"     -> specs.recordingUnits().size();
            case UE_REL -> specs.recordingUnitRelSpecs().size();
            case STRATI -> specs.recordingUnitStratiRelSpecs().size();
            case LIEU_REL -> specs.spatialUnitRelSpecs().size();
            case "mob"    -> specs.specimenSpecs().size();
            case PHASE -> specs.phaseSpecs().size();
            default       -> 0;
        };
    }

    public int getTotalImportRows() {
        if (importResult == null) return 0;
        var s = importResult.specs();
        return s.spatialUnits().size()
             + s.recordingUnits().size()
             + s.recordingUnitRelSpecs().size()
             + s.recordingUnitStratiRelSpecs().size()
             + s.spatialUnitRelSpecs().size()
             + s.specimenSpecs().size()
             + s.phaseSpecs().size();
    }

    public String getSummaryText() {
        if (importResult == null) return "";
        int errors = getErrorCount();
        int rows = getTotalImportRows();
        if (errors > 0) return errors + ERREUR + (errors > 1 ? "s" : "") + " à corriger avant import";
        return rows + LIGNE + (rows > 1 ? "s" : "") + " prête" + (rows > 1 ? "s" : "") + " à importer";
    }

    public String getImportButtonLabel() {
        int rows = getTotalImportRows();
        return "Importer " + rows + LIGNE + (rows > 1 ? "s" : "");
    }

    // ─── Post-import summary ────────────────────────────────────────────────

    private List<ImportSummaryRow> buildImportSummary() {
        List<ImportSummaryRow> rows = new ArrayList<>();
        addUpsertSummaryRow(rows, "Lieu", ImportSchema.SPATIAL_UNIT);
        addSummaryRow(rows, "Relations Lieu", getSpecCountForKey(LIEU_REL));
        addUpsertSummaryRow(rows, "UE", ImportSchema.RECORDING_UNIT);
        addSummaryRow(rows, "Relations UE", getSpecCountForKey(UE_REL));
        addSummaryRow(rows, "Stratigraphie", getSpecCountForKey(STRATI));
        addUpsertSummaryRow(rows, "Mobilier", ImportSchema.SPECIMEN);
        addUpsertSummaryRow(rows, "Phase", ImportSchema.PHASE);
        return rows;
    }

    private void addSummaryRow(List<ImportSummaryRow> rows, String label, int count) {
        if (count > 0) rows.add(ImportSummaryRow.simple(label, count));
    }

    private void addUpsertSummaryRow(List<ImportSummaryRow> rows, String label, String tableId) {
        SeedCounts.Counts c = seedCounts.get(tableId);
        if (c.created() > 0 || c.updated() > 0 || c.skippedDuplicate() > 0) {
            rows.add(new ImportSummaryRow(label, c.created(), c.updated(), c.skippedDuplicate()));
        }
    }

    public String getImportDoneSummaryText() {
        List<String> parts = new ArrayList<>();
        if (importedCreated > 0) {
            parts.add(importedCreated + LIGNE + pluralSuffix(importedCreated) + " créée" + pluralSuffix(importedCreated));
        }
        if (importedUpdated > 0) {
            parts.add(importedUpdated + LIGNE + pluralSuffix(importedUpdated) + " modifiée" + pluralSuffix(importedUpdated));
        }
        if (importedSkippedDuplicate > 0) {
            parts.add(importedSkippedDuplicate + " doublon" + pluralSuffix(importedSkippedDuplicate)
                    + " ignoré" + pluralSuffix(importedSkippedDuplicate));
        }
        String detail = parts.isEmpty() ? "0" + LIGNE : String.join(", ", parts);
        return "✓ Import terminé · " + detail + " dans le projet";
    }

    private static String pluralSuffix(int n) {
        return n > 1 ? "s" : "";
    }

    /** Discards the completed-import summary and returns to the empty upload dropzone. */
    public void startNewImport() {
        clearImportState();
    }

    public String getValidationStatusLabel() {
        if (isImportBlocked()) {
            int n = getErrorCount();
            return "⚠ " + n + ERREUR + (n > 1 ? "s" : "");
        }
        return "✓ Aucune erreur";
    }

    public String getValidationStatusStyle() {
        return isImportBlocked()
            ? "margin-left:auto;font-size:12px;border-radius:6px;padding:2px 9px;color:#b5403a;background:#fbeeee;border:1px solid #eccfcf;"
            : "margin-left:auto;font-size:12px;border-radius:6px;padding:2px 9px;color:#3a5a3c;background:#eaf3ea;border:1px solid #cfe3cf;";
    }

    public String getMappingStatusLabel() {
        if (isMappingOk()) return "✓ Mapping complet";

        int sheets = getUnmappedSheetCount();
        int cols = getUnmappedColumnCount();
        List<String> parts = new ArrayList<>();
        if (sheets > 0) parts.add(sheets + FEUILLE + (sheets > 1 ? "s" : "") + " non mappée" + (sheets > 1 ? "s" : ""));
        if (cols > 0) parts.add(cols + " colonne" + (cols > 1 ? "s" : "") + " non mappée" + (cols > 1 ? "s" : ""));
        return "⚠ Mapping incomplet (" + String.join(", ", parts) + ")";
    }

    public String getMappingStatusStyle() {
        return isMappingOk()
            ? "margin-left:auto;font-size:12px;border-radius:6px;padding:2px 9px;color:#3a5a3c;background:#eaf3ea;border:1px solid #cfe3cf;"
            : "margin-left:auto;font-size:12px;border-radius:6px;padding:2px 9px;color:#8a6d1e;background:#fdf6e3;border:1px solid #ecdca0;";
    }

    public String getBlockedMessage() {
        int n = getErrorCount();
        return "Corrigez les " + n + ERREUR + (n > 1 ? "s" : "") + " pour importer";
    }

    // ─── Per-tab accessors (used from hardcoded tabs in XHTML) ───────────────

    public ValidationTabView getLieuTab()    { return buildTabForKey("lieu",   "Lieu"); }
    public ValidationTabView getLieuRelTab() { return buildTabForKey(LIEU_REL, "Relations Lieu"); }
    public ValidationTabView getUeTab()      { return buildTabForKey("ue",     "UE"); }
    public ValidationTabView getUeRelTab()   { return buildTabForKey(UE_REL, "Relations UE"); }
    public ValidationTabView getStratiTab()  { return buildTabForKey(STRATI, "Stratigraphie"); }
    public ValidationTabView getMobTab()     { return buildTabForKey("mob",    "Mobilier"); }
    public ValidationTabView getPhaseTab()   { return buildTabForKey(PHASE,  "Phase"); }

    private ValidationTabView buildTabForKey(String key, String label) {
        Map<String, String> sheetToKey = buildSheetToKeyMap();
        Map<String, List<ImportError>> errorsByKey = new LinkedHashMap<>();
        for (ImportError e : getImportErrors()) {
            // persistence errors are tagged with a table ID (not a real sheet name) — resolveTableKey
            // handles both, so fall back to it when the sheet isn't in the workbook's sheetToKey map.
            String k = sheetToKey.containsKey(e.sheet()) ? sheetToKey.get(e.sheet()) : resolveTableKey(e.sheet());
            errorsByKey.computeIfAbsent(k, x -> new ArrayList<>()).add(e);
        }
        List<ImportError> errs = errorsByKey.getOrDefault(key, List.of());
        return new ValidationTabView(key, label, errs, getSpecCountForKey(key));
    }

    // ─── Upload / persistence ────────────────────────────────────────────────
    // Both phases run on a background thread (ImportAsyncRunner) so the UI can poll ImportProgress
    // for live status. The async callbacks below only ever touch plain bean fields — never
    // FacesContext/PrimeFaces, which are thread-local to the JSF request thread and would be
    // null/stale from a background thread. Growl messages are queued as "pending" and flushed by
    // pollProgress(), which runs on a real p:poll request thread.

    /**
     * Dispatches the persist phase (seeding the previously-parsed {@link #importResult} into the
     * database) to a background thread; a no-op if there's nothing to persist, import is blocked,
     * or a phase is already running.
     */
    public void uploadSpec() {
        if (importResult == null || isImportBlocked() || progress.isRunning()) return;
        persistenceErrors = new ArrayList<>();
        seedCounts.reset();
        // set synchronously, before dispatching, so the initiating request's own response already
        // reflects PERSISTING — otherwise the progress panel/p:poll might not render on the first
        // response if the background thread hasn't reached its own progress.start(...) call yet,
        // and nothing would ever refresh the view again.
        progress.start(ImportProgress.Phase.PERSISTING, 0);
        asyncRunner.persistAsync(importResult.specs(), project, progress, seedCounts, this::onPersistSuccess, this::onPersistError);
    }

    private static final List<String> UPSERT_TABLE_IDS = List.of(
            ImportSchema.SPATIAL_UNIT, ImportSchema.RECORDING_UNIT, ImportSchema.SPECIMEN, ImportSchema.PHASE);

    /** Sums created/updated/skipped-duplicate counts across the 4 upsert-aware entity types. */
    private SeedCounts.Counts sumUpsertCounts() {
        int created = 0;
        int updated = 0;
        int skippedDuplicate = 0;
        for (String tableId : UPSERT_TABLE_IDS) {
            SeedCounts.Counts c = seedCounts.get(tableId);
            created += c.created();
            updated += c.updated();
            skippedDuplicate += c.skippedDuplicate();
        }
        return new SeedCounts.Counts(created, updated, skippedDuplicate);
    }

    private void onPersistSuccess() {
        List<ImportSummaryRow> summary = buildImportSummary();
        int total = getTotalImportRows();
        SeedCounts.Counts upsertTotals = sumUpsertCounts();
        // types without an upsert-aware seeder (relations) have no created/updated split — their rows
        // are still counted as "created" for the top summary, matching today's behavior for them.
        int created = upsertTotals.created() + getSpecCountForKey(LIEU_REL) + getSpecCountForKey(UE_REL) + getSpecCountForKey(STRATI);
        // pure field resets, no FacesContext — safe here; progress itself is reset separately by
        // pollProgress(). Keeps project set (unlike reset()) — see clearImportState() javadoc.
        clearImportState();
        importDone = true;
        importSummary = summary;
        importedTotal = total;
        importedCreated = created;
        importedUpdated = upsertTotals.updated();
        importedSkippedDuplicate = upsertTotals.skippedDuplicate();
    }

    private void onPersistError(Exception e) {
        if (e instanceof SeedException se) {
            persistenceErrors.add(new ImportError(se.getTableId(), 0, "—", MessageUtils.describe(se)));
        } else {
            persistenceErrors.add(new ImportError("Import", 0, "—", MessageUtils.describe(e)));
        }
        readyToUpload = false;
        // panels re-render via the poll's update attribute
    }

    /**
     * Reads the uploaded file's bytes and dispatches the parse phase to a background thread; a no-op
     * if a phase is already running.
     *
     * @param event PrimeFaces upload event carrying the uploaded file
     */
    public void handleFileUpload(FileUploadEvent event) {
        if (progress.isRunning()) return;
        this.originalFile = null;
        persistenceErrors = new ArrayList<>();
        UploadedFile file = event.getFile();

        if (file != null && file.getFileName() != null) {
            uploadedFileName = file.getFileName();
            uploadedFileSize = file.getSize();
            byte[] bytes;
            try (InputStream is = file.getInputStream()) {
                bytes = is.readAllBytes();
            } catch (IOException e) {
                onParseError(e);
                return;
            }
            progress.start(ImportProgress.Phase.OPENING, 0);
            asyncRunner.parseAsync(bytes, OOXMLImportService.ImportScope.PROJECT, project, progress,
                    this::onParseSuccess, this::onParseError);
        }
    }

    private void onParseSuccess(ImportResult result) {
        this.importResult = result;
        this.readyToUpload = !result.hasErrors();
        if (result.hasErrors()) {
            String msg = result.errors().size() + " ligne(s) ignorée(s) lors du chargement";
            setPendingGrowl(SEVERITY_WARN, TEMPLATE_FORM_CC_TEMPLATE_FORM_TEMPLATE_GROWL, "Import partiel", msg);
        }
    }

    private void onParseError(Exception e) {
        setPendingGrowl(SEVERITY_ERROR, null, "Erreur", "Échec du chargement du fichier : " + MessageUtils.describe(e));
    }

    private void setPendingGrowl(String severity, String target, String summary, String detail) {
        pendingGrowlSeverity = severity;
        pendingGrowlTarget = target;
        pendingGrowlSummary = summary;
        pendingGrowlDetail = detail;
    }

    /** Bound to p:poll — runs on a real request thread, so this is where the pending growl (if any) is safely flushed. */
    public void pollProgress() {
        if (progress.isFinished()) {
            flushPendingGrowl();
            progress.reset();
        }
    }

    private void flushPendingGrowl() {
        if (pendingGrowlSeverity == null) return;
        FacesMessage.Severity sev = switch (pendingGrowlSeverity) {
            case SEVERITY_WARN -> FacesMessage.SEVERITY_WARN;
            case SEVERITY_ERROR -> FacesMessage.SEVERITY_ERROR;
            default -> FacesMessage.SEVERITY_INFO;
        };
        FacesContext.getCurrentInstance().addMessage(pendingGrowlTarget,
                new FacesMessage(sev, pendingGrowlSummary, pendingGrowlDetail));
        if (pendingGrowlTarget != null) {
            PrimeFaces.current().ajax().update(pendingGrowlTarget);
        }
        pendingGrowlSeverity = null;
        pendingGrowlTarget = null;
        pendingGrowlSummary = null;
        pendingGrowlDetail = null;
    }
}
