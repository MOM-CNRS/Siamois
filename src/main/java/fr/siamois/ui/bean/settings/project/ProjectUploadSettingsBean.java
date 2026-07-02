package fr.siamois.ui.bean.settings.project;


import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.infrastructure.database.initializer.seeder.ProjectDataSeeder;
import fr.siamois.infrastructure.dataimport.ExcelCellHelper;
import fr.siamois.infrastructure.dataimport.ImportError;
import fr.siamois.infrastructure.dataimport.ImportResult;
import fr.siamois.infrastructure.dataimport.OOXMLImportService;
import fr.siamois.infrastructure.dataimport.SheetMetadata;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
@Data
public class ProjectUploadSettingsBean {

    public static final String TEMPLATE_FORM_CC_TEMPLATE_FORM_TEMPLATE_GROWL = "templateFormCC:templateForm:templateGrowl";

    @Value
    public static class SheetMappingView {
        String sheetName;
        String tableId;
        List<ColumnAliasView> columnAliases;
        boolean unmapped;
        public boolean hasAliases() { return !columnAliases.isEmpty(); }
    }

    @Value
    public static class ColumnAliasView {
        String alias;
        String canonical;
        boolean columnUnmapped;
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
    }

    private final OOXMLImportService importService;
    private final ProjectDataSeeder seeder;

    ActionUnitDTO project;
    UploadedFile originalFile;
    StreamedContent templateFile;
    ImportResult importResult;
    List<ImportError> persistenceErrors = new ArrayList<>();
    boolean readyToUpload = false;
    String uploadedFileName = "";
    long uploadedFileSize = 0;

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
                        throw new RuntimeException(e);
                    }
                })
                .build();
    }

    @EventListener(LoginEvent.class)
    public void reset() {
        project = null;
        readyToUpload = false;
        importResult = null;
        originalFile = null;
        persistenceErrors = new ArrayList<>();
        uploadedFileName = "";
        uploadedFileSize = 0;
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
        return sheets + " feuille" + (sheets > 1 ? "s" : "") + " · mapping détecté";
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

        // Build sheetName → tableId reverse map
        Map<String, String> sheetToTable = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : meta.tableToSheets().entrySet()) {
            for (String sheetName : entry.getValue()) {
                sheetToTable.put(sheetName, entry.getKey());
            }
        }

        List<SheetMappingView> result = new ArrayList<>();
        for (Map.Entry<String, List<String>> sheetEntry : importResult.allSheetColumns().entrySet()) {
            String sheetName = sheetEntry.getKey();
            String tableId = sheetToTable.get(sheetName);
            boolean unmapped = (tableId == null);

            List<ColumnAliasView> aliasList = new ArrayList<>();
            if (unmapped) {
                // Sheet not recognized — all its columns are unmapped
                for (String col : sheetEntry.getValue()) {
                    aliasList.add(new ColumnAliasView(col, "", true));
                }
            } else {
                // aliases already combines explicit _meta aliases with auto-matched canonical
                // names (columns whose normalized header equals one of EXPECTED_COLUMNS for
                // this table) — see OOXMLImportService.readSheetMetadata(). Anything not in
                // this map is genuinely unmapped: neither a known default column nor aliased.
                Map<String, String> aliases = meta.columnAliases().getOrDefault(sheetName, Map.of());

                for (String col : sheetEntry.getValue()) {
                    String norm = ExcelCellHelper.normalize(col);
                    if (aliases.containsKey(norm)) {
                        aliasList.add(new ColumnAliasView(col, aliases.get(norm), false));
                    } else {
                        // Column name not among the expected defaults, and not aliased in _meta
                        aliasList.add(new ColumnAliasView(col, "", true));
                    }
                }
            }

            result.add(new SheetMappingView(sheetName, unmapped ? "" : tableId, aliasList, unmapped));
        }
        return result;
    }

    public String getMappingSummary() {
        if (importResult == null) return "";
        int sheets = getSheetMappings().size();
        long tables = importResult.meta().tableToSheets().size();
        return sheets + " feuille" + (sheets > 1 ? "s" : "") + " → " + tables + " table" + (tables > 1 ? "s" : "");
    }

    public boolean isMappingOk() {
        return importResult != null && getSheetMappings().stream().noneMatch(SheetMappingView::isUnmapped);
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
            case "relation stratigraphique", "stratirel",
                 "recording_unit_strati_rel"                     -> "strati";
            case "groupement d'ue", "recordingrel",
                 "recording_unit_rel"                            -> "ue";
            case "specimen", "mobilier"                          -> "mob";
            case "phase"                                         -> "phase";
            default                                              -> tableId;
        };
    }

    private int getSpecCountForKey(String key) {
        if (importResult == null) return 0;
        var specs = importResult.specs();
        return switch (key) {
            case "lieu"   -> specs.spatialUnits().size();
            case "ue"     -> specs.recordingUnits().size();
            case "strati" -> specs.recordingUnitStratiRelSpecs().size();
            case "mob"    -> specs.specimenSpecs().size();
            case "phase"  -> specs.phaseSpecs().size();
            default       -> 0;
        };
    }

    public int getTotalImportRows() {
        if (importResult == null) return 0;
        var s = importResult.specs();
        return s.spatialUnits().size()
             + s.recordingUnits().size()
             + s.recordingUnitStratiRelSpecs().size()
             + s.specimenSpecs().size()
             + s.phaseSpecs().size();
    }

    public String getSummaryText() {
        if (importResult == null) return "";
        int errors = getErrorCount();
        int rows = getTotalImportRows();
        if (errors > 0) return errors + " erreur" + (errors > 1 ? "s" : "") + " à corriger avant import";
        return rows + " ligne" + (rows > 1 ? "s" : "") + " prête" + (rows > 1 ? "s" : "") + " à importer";
    }

    public String getImportButtonLabel() {
        int rows = getTotalImportRows();
        return "Importer " + rows + " ligne" + (rows > 1 ? "s" : "");
    }

    public String getValidationStatusLabel() {
        if (isImportBlocked()) {
            int n = getErrorCount();
            return "⚠ " + n + " erreur" + (n > 1 ? "s" : "");
        }
        return "✓ Aucune erreur";
    }

    public String getValidationStatusStyle() {
        return isImportBlocked()
            ? "margin-left:auto;font-size:12px;border-radius:6px;padding:2px 9px;color:#b5403a;background:#fbeeee;border:1px solid #eccfcf;"
            : "margin-left:auto;font-size:12px;border-radius:6px;padding:2px 9px;color:#3a5a3c;background:#eaf3ea;border:1px solid #cfe3cf;";
    }

    public String getMappingStatusLabel() {
        return isMappingOk() ? "✓ Mapping validé" : "⚠ Colonnes non mappées";
    }

    public String getMappingStatusStyle() {
        return isMappingOk()
            ? "margin-left:auto;font-size:12px;border-radius:6px;padding:2px 9px;color:#3a5a3c;background:#eaf3ea;border:1px solid #cfe3cf;"
            : "margin-left:auto;font-size:12px;border-radius:6px;padding:2px 9px;color:#8a6d1e;background:#fdf6e3;border:1px solid #ecdca0;";
    }

    public String getBlockedMessage() {
        int n = getErrorCount();
        return "Corrigez les " + n + " erreur" + (n > 1 ? "s" : "") + " pour importer";
    }

    // ─── Per-tab accessors (used from hardcoded tabs in XHTML) ───────────────

    public ValidationTabView getLieuTab()   { return buildTabForKey("lieu",   "Lieu"); }
    public ValidationTabView getUeTab()     { return buildTabForKey("ue",     "UE"); }
    public ValidationTabView getStratiTab() { return buildTabForKey("strati", "Stratigraphie"); }
    public ValidationTabView getMobTab()    { return buildTabForKey("mob",    "Mobilier"); }
    public ValidationTabView getPhaseTab()  { return buildTabForKey("phase",  "Phase"); }

    private ValidationTabView buildTabForKey(String key, String label) {
        Map<String, String> sheetToKey = buildSheetToKeyMap();
        Map<String, List<ImportError>> errorsByKey = new LinkedHashMap<>();
        for (ImportError e : getImportErrors()) {
            String k = sheetToKey.getOrDefault(e.sheet(), "other");
            errorsByKey.computeIfAbsent(k, x -> new ArrayList<>()).add(e);
        }
        List<ImportError> errs = errorsByKey.getOrDefault(key, List.of());
        return new ValidationTabView(key, label, errs, getSpecCountForKey(key));
    }

    // ─── Upload / persistence ────────────────────────────────────────────────

    public void uploadSpec() {
        if (importResult == null || isImportBlocked()) return;
        try {
            seeder.seedAll(importResult.specs(), project);
            FacesContext.getCurrentInstance().addMessage(TEMPLATE_FORM_CC_TEMPLATE_FORM_TEMPLATE_GROWL,
                    new FacesMessage(FacesMessage.SEVERITY_INFO, "Données importées avec succès", null));
            PrimeFaces.current().ajax().update(TEMPLATE_FORM_CC_TEMPLATE_FORM_TEMPLATE_GROWL);
            reset();
        } catch (Exception e) {
            persistenceErrors.add(new ImportError("Import", 0, "—", e.getMessage()));
            readyToUpload = false;
            // panels re-render via the button's update attribute
        }
    }

    public void handleFileUpload(FileUploadEvent event) {
        this.originalFile = null;
        persistenceErrors = new ArrayList<>();
        UploadedFile file = event.getFile();

        if (file != null && file.getFileName() != null) {
            uploadedFileName = file.getFileName();
            uploadedFileSize = file.getSize();
            try (InputStream is = file.getInputStream()) {
                this.importResult = importService.importFromExcel(is,
                        OOXMLImportService.ImportScope.PROJECT,
                        project);
                readyToUpload = !importResult.hasErrors();

                if (importResult.hasErrors()) {
                    String msg = importResult.errors().size() + " ligne(s) ignorée(s) lors du chargement";
                    FacesContext.getCurrentInstance().addMessage(TEMPLATE_FORM_CC_TEMPLATE_FORM_TEMPLATE_GROWL,
                            new FacesMessage(FacesMessage.SEVERITY_WARN, "Import partiel", msg));
                    PrimeFaces.current().ajax().update(TEMPLATE_FORM_CC_TEMPLATE_FORM_TEMPLATE_GROWL);
                }
            } catch (Exception e) {
                FacesContext.getCurrentInstance().addMessage(null,
                        new FacesMessage(FacesMessage.SEVERITY_ERROR, "Erreur", "Échec du chargement du fichier : " + e.getMessage()));
            }
        }
    }
}
