package fr.siamois.infrastructure.dataimport;

import fr.siamois.domain.models.misc.ImportProgress;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.infrastructure.database.initializer.seeder.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static fr.siamois.infrastructure.dataimport.ExcelCellHelper.*;
import static fr.siamois.infrastructure.dataimport.ImportSchema.*;


@Service
@RequiredArgsConstructor
public class OOXMLImportService {

    private static final String HEADER_READ_ERROR_PREFIX = "Erreur lecture en-tête: ";
    public static final String PARENT = "parent";
    public static final String ENFANT = "enfant";

    private final ConceptService conceptService;

    // -------------------------------------------------------------------------
    // Sheet metadata
    // -------------------------------------------------------------------------

    public SheetMetadata readSheetMetadata(Workbook workbook) {
        Sheet metaSheet = workbook.getSheet("_meta");
        SheetMetadata base = metaSheet == null
                ? fallbackSheetMapping(workbook)
                : readMetadataSheet(metaSheet);

        Map<String, List<String>> enriched = new LinkedHashMap<>(base.tableToSheets());
        DEFAULT_SHEET_NAMES.forEach((tableId, defaultName) -> {
            if (!enriched.containsKey(tableId) && workbook.getSheet(defaultName) != null) {
                enriched.put(tableId, List.of(defaultName));
            }
        });

        Map<String, String> sheetToTableId = new HashMap<>();
        enriched.forEach((tableId, names) -> names.forEach(n -> sheetToTableId.put(n, tableId)));

        Map<String, Map<String, String>> enrichedAliases = new LinkedHashMap<>();
        enriched.values().stream().flatMap(List::stream).distinct()
                .forEach(sheetName -> enrichAliasesForSheet(workbook, sheetName, sheetToTableId, base, enrichedAliases));

        return new SheetMetadata(enriched, enrichedAliases);
    }

    private void enrichAliasesForSheet(Workbook workbook, String sheetName, Map<String, String> sheetToTableId,
                                        SheetMetadata base, Map<String, Map<String, String>> enrichedAliases) {
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) return;

        String tableId = sheetToTableId.get(sheetName);
        Set<String> expected = new HashSet<>(EXPECTED_COLUMNS.getOrDefault(tableId, List.of()));
        Map<String, String> explicit = base.columnAliases().getOrDefault(sheetName, Map.of());
        Map<String, String> full = new LinkedHashMap<>(explicit);

        for (String raw : headerCellValues(sheet.getRow(0))) {
            String norm = normalize(raw);
            String canonical = explicit.getOrDefault(norm, norm);
            if (!explicit.containsKey(norm) && expected.contains(canonical)) {
                full.put(norm, canonical);
            }
        }

        if (!full.isEmpty()) enrichedAliases.put(sheetName, full);
    }

    /** Non-blank cell values of a header row, in column order (empty list if the row is missing). */
    private List<String> headerCellValues(Row header) {
        List<String> values = new ArrayList<>();
        if (header == null) return values;
        for (int c = 0; c < header.getLastCellNum(); c++) {
            String raw = getStringCell(header.getCell(c));
            if (raw != null && !raw.isBlank()) values.add(raw);
        }
        return values;
    }

    private SheetMetadata fallbackSheetMapping(Workbook workbook) {
        Map<String, List<String>> tableToSheets = new LinkedHashMap<>();
        DEFAULT_SHEET_NAMES.forEach((tableId, defaultName) -> {
            if (workbook.getSheet(defaultName) != null) {
                tableToSheets.put(tableId, List.of(defaultName));
            }
        });
        return new SheetMetadata(tableToSheets, Map.of());
    }

    private SheetMetadata readMetadataSheet(Sheet metaSheet) {
        Map<String, LinkedHashSet<String>> tableToSheetsTemp = new LinkedHashMap<>();
        Map<String, Map<String, String>> columnAliases = new HashMap<>();

        Row header = metaSheet.getRow(0);
        if (header == null) return new SheetMetadata(Map.of(), columnAliases);

        Map<String, Integer> colIndex = indexColumns(header);
        Integer sheetIdCol      = colIndex.get("sheet_id");
        Integer sheetNameCol    = colIndex.get("sheet_name");
        Integer aliasCol        = colIndex.get("column_alias");
        Integer canonicalCol    = colIndex.get("column_canonical");

        // Mapping of the sheet names if no cols defined
        if (sheetIdCol == null || sheetNameCol == null) return new SheetMetadata(Map.of(), columnAliases);

        for (int r = 1; r <= metaSheet.getLastRowNum(); r++) {
            registerMetaRow(metaSheet.getRow(r), sheetIdCol, sheetNameCol, aliasCol, canonicalCol,
                    tableToSheetsTemp, columnAliases);
        }

        Map<String, List<String>> tableToSheets = new LinkedHashMap<>();
        tableToSheetsTemp.forEach((k, v) -> tableToSheets.put(k, new ArrayList<>(v)));
        return new SheetMetadata(tableToSheets, columnAliases);
    }

    private void registerMetaRow(Row row, int sheetIdCol, int sheetNameCol, Integer aliasCol, Integer canonicalCol,
                                  Map<String, LinkedHashSet<String>> tableToSheetsTemp,
                                  Map<String, Map<String, String>> columnAliases) {
        String id   = row != null ? getStringCell(row, sheetIdCol) : null;
        String name = row != null ? getStringCell(row, sheetNameCol) : null;
        if (id == null || name == null) return;
        id = id.trim();
        name = name.trim();

        tableToSheetsTemp.computeIfAbsent(id, k -> new LinkedHashSet<>()).add(name);

        if (aliasCol == null || canonicalCol == null) return;
        String alias     = getStringCellOrNull(row, aliasCol);
        String canonical = getStringCellOrNull(row, canonicalCol);
        if (alias == null || alias.isBlank() || canonical == null || canonical.isBlank()) return;

        columnAliases.computeIfAbsent(name, k -> new HashMap<>())
                .put(normalize(alias.trim()), normalize(canonical.trim()));
    }

    // -------------------------------------------------------------------------
    // Main entry point
    // -------------------------------------------------------------------------

    public enum ImportScope {
        ALL,
        PROJECT
    }

    public ImportResult importFromExcel(InputStream is, ImportScope scope, ActionUnitDTO actionUnitDTO) throws IOException {
        return importFromExcel(is, scope, actionUnitDTO, new ImportProgress());
    }

    public ImportResult importFromExcel(InputStream is, ImportScope scope, ActionUnitDTO actionUnitDTO, ImportProgress progress) throws IOException {
        progress.start(ImportProgress.Phase.OPENING, 0);
        try (Workbook workbook = WorkbookFactory.create(is)) {

            SheetMetadata meta = readSheetMetadata(workbook);
            List<ImportError> errors = new ArrayList<>();

            List<Sheet> institutionSheets  = scope == ImportScope.ALL ? getSheetsForTable(workbook, meta, INSTITUTION) : List.of();
            List<Sheet> personSheets       = scope == ImportScope.ALL ? getSheetsForTable(workbook, meta, PERSON) : List.of();
            List<Sheet> actionCodeSheets   = scope == ImportScope.ALL ? getSheetsForTable(workbook, meta, "code") : List.of();
            List<Sheet> actionUnitSheets   = scope == ImportScope.ALL ? getSheetsForTable(workbook, meta, "action_unit") : List.of();
            List<Sheet> spatialUnitSheets  = getSheetsForTable(workbook, meta, "spatial_unit");
            List<Sheet> recordingUnitSheets = getSheetsForTable(workbook, meta, "recording_unit");
            List<Sheet> specimenSheets     = getSheetsForTable(workbook, meta, "specimen");
            List<Sheet> phaseSheets        = getSheetsForTable(workbook, meta, "phase");
            List<Sheet> recordingRelSheets = getSheetsForTable(workbook, meta, "recordingRel");
            List<Sheet> stratiRelSheets    = getSheetsForTable(workbook, meta, "stratiRel");
            List<Sheet> spatialUnitRelSheets = getSheetsForTable(workbook, meta, SPATIAL_UNIT_REL);

            int institutionRows = sumDataRows(institutionSheets);
            int personRows = sumDataRows(personSheets);
            int actionCodeRows = sumDataRows(actionCodeSheets);
            int actionUnitRows = sumDataRows(actionUnitSheets);
            int spatialUnitRows = sumDataRows(spatialUnitSheets);
            int recordingUnitRows = sumDataRows(recordingUnitSheets);
            int specimenRows = sumDataRows(specimenSheets);
            int phaseRows = sumDataRows(phaseSheets);
            int recordingRelRows = sumDataRows(recordingRelSheets);
            int stratiRelRows = sumDataRows(stratiRelSheets);
            int spatialUnitRelRows = sumDataRows(spatialUnitRelSheets);
            progress.start(ImportProgress.Phase.PARSING, institutionRows + personRows + actionCodeRows + actionUnitRows
                    + spatialUnitRows + recordingUnitRows + specimenRows + phaseRows + recordingRelRows + stratiRelRows
                    + spatialUnitRelRows);

            List<InstitutionSeeder.InstitutionSpec>             institutions  = new ArrayList<>();
            List<PersonSeeder.PersonSpec>                       persons       = new ArrayList<>();
            List<ActionCodeSeeder.ActionCodeSpec>               actionCodes   = new ArrayList<>();
            List<ActionUnitSeeder.ActionUnitSpecs>              actionUnits   = new ArrayList<>();

            if (scope == ImportScope.ALL) {
                institutions = parseInstitutions(institutionSheets, meta, errors, progress);
                persons      = parsePersons(personSheets, meta, errors, progress);
                actionCodes  = parseActionCodes(actionCodeSheets, meta, errors, progress);
                actionUnits  = parseActionUnits(actionUnitSheets, meta, errors, progress);
            }

            List<SpatialUnitRelSeeder.SpatialUnitRelDTO>                      spatialUnitChildRels = new ArrayList<>();
            List<SpatialUnitSeeder.SpatialUnitSpecs>                          spatialUnits   = parseSpatialUnits(spatialUnitSheets, actionUnitDTO, meta, errors, progress, spatialUnitChildRels);
            List<RecordingUnitSeeder.RecordingUnitSpecs>                      recordingUnits = parseRecordingUnits(recordingUnitSheets, scope, actionUnitDTO, meta, errors, progress);
            List<SpecimenSeeder.SpecimenSpecs>                                specimenSpecs  = parseSpecimens(specimenSheets, actionUnitDTO, meta, errors, progress);
            List<PhaseSeeder.PhaseSpecs>                                      phaseSpecs     = parsePhases(phaseSheets, actionUnitDTO, meta, errors, progress);
            List<RecordingUnitRelSeeder.RecordingUnitRelDTO>                  recordingRels  = parseRecordingRels(recordingRelSheets, meta, errors, progress);
            List<RecordingUnitStratiRelSeeder.RecordingUnitStratiRelDTO>      stratiRels     = parseStratiRels(stratiRelSheets, actionUnitDTO, meta, errors, progress);
            List<SpatialUnitRelSeeder.SpatialUnitRelDTO>                      spatialUnitRels = parseSpatialUnitRels(spatialUnitRelSheets, meta, errors, progress);
            spatialUnitRels.addAll(0, spatialUnitChildRels);

            ImportSpecs specs = new ImportSpecs(institutions, persons, spatialUnits, actionCodes, actionUnits,
                    recordingUnits, specimenSpecs, phaseSpecs, recordingRels, stratiRels, spatialUnitRels);

            Map<String, List<String>> allSheetColumns = collectAllSheetColumns(workbook);

            return new ImportResult(specs, errors, meta, allSheetColumns);
        }
    }

    private int sumDataRows(List<Sheet> sheets) {
        int sum = 0;
        for (Sheet s : sheets) {
            sum += Math.max(0, s.getLastRowNum());
        }
        return sum;
    }

    /**
     * Raw column headers for every sheet except _meta, for display in the mapping UI.
     * Whether a column is "recognized" is decided from meta.columnAliases(), which already
     * combines explicit _meta aliases with auto-matched canonical names filtered by EXPECTED_COLUMNS.
     */
    private Map<String, List<String>> collectAllSheetColumns(Workbook workbook) {
        Map<String, List<String>> allSheetColumns = new LinkedHashMap<>();
        for (int si = 0; si < workbook.getNumberOfSheets(); si++) {
            Sheet sheet = workbook.getSheetAt(si);
            if ("_meta".equalsIgnoreCase(sheet.getSheetName())) continue;
            allSheetColumns.put(sheet.getSheetName(), rawHeaderLabels(sheet.getRow(0)));
        }
        return allSheetColumns;
    }

    private List<String> rawHeaderLabels(Row header) {
        List<String> labels = new ArrayList<>();
        if (header == null) return labels;
        for (int ci = 0; ci <= header.getLastCellNum(); ci++) {
            Cell cell = header.getCell(ci);
            if (cell != null) {
                String v = cell.toString().trim();
                if (!v.isEmpty()) labels.add(v);
            }
        }
        return labels;
    }

    private List<Sheet> getSheetsForTable(Workbook workbook, SheetMetadata meta, String tableId) {
        List<String> names = meta.tableToSheets().get(tableId);
        if (names != null && !names.isEmpty()) {
            return names.stream()
                    .map(workbook::getSheet)
                    .filter(Objects::nonNull)
                    .toList();
        }
        String defaultName = DEFAULT_SHEET_NAMES.get(tableId);
        if (defaultName == null) return List.of();
        Sheet s = workbook.getSheet(defaultName);
        return s != null ? List.of(s) : List.of();
    }

    // -------------------------------------------------------------------------
    // Parse methods
    // -------------------------------------------------------------------------

    public List<InstitutionSeeder.InstitutionSpec> parseInstitutions(Sheet sheet) {
        List<ImportError> errors = new ArrayList<>();
        return parseInstitutions(List.of(sheet), SheetMetadata.empty(), errors, new ImportProgress());
    }

    public List<InstitutionSeeder.InstitutionSpec> parseInstitutions(List<Sheet> sheets, SheetMetadata meta, List<ImportError> errors, ImportProgress progress) {
        List<InstitutionSeeder.InstitutionSpec> result = new ArrayList<>();
        for (Sheet sheet : sheets) {
            try {
                Row header = sheet.getRow(0);
                Map<String, Integer> cols = indexColumns(header, meta.columnAliases().getOrDefault(sheet.getSheetName(), Map.of()));
                forEachDataRow(sheet, errors, progress, row -> {
                    String name = getStringCellOrNull(row, cols, "nom");
                    if (name == null || name.isBlank()) return;
                    String description = getStringCellOrNull(row, cols, DESCRIPTION);
                    String identifier  = getStringCellOrNull(row, cols, IDENTIFIANT);
                    String adminsRaw   = getStringCellOrNull(row, cols, "email admins");
                    String thesaurus   = getStringCellOrNull(row, cols, "thesaurus");
                    List<String> adminEmails = parsePersonList(adminsRaw);
                    String vocabularyId      = extractIdtFromUri(thesaurus).orElse(null);
                    String thesaurusInstance = extractThesaurusDomain(thesaurus);
                    result.add(new InstitutionSeeder.InstitutionSpec(
                            name, description, identifier, adminEmails, thesaurusInstance, vocabularyId));
                });
            } catch (Exception e) {
                errors.add(new ImportError(sheet.getSheetName(), 0, "", HEADER_READ_ERROR_PREFIX + e.getMessage()));
            }
        }
        return result;
    }

    public List<PersonSeeder.PersonSpec> parsePersons(Sheet sheet) {
        List<ImportError> errors = new ArrayList<>();
        return parsePersons(List.of(sheet), SheetMetadata.empty(), errors, new ImportProgress());
    }

    public List<PersonSeeder.PersonSpec> parsePersons(List<Sheet> sheets, SheetMetadata meta, List<ImportError> errors, ImportProgress progress) {
        List<PersonSeeder.PersonSpec> result = new ArrayList<>();
        for (Sheet sheet : sheets) {
            try {
                Row header = sheet.getRow(0);
                Map<String, Integer> cols = indexColumns(header, meta.columnAliases().getOrDefault(sheet.getSheetName(), Map.of()));
                forEachDataRow(sheet, errors, progress, row -> {
                    String email = getStringCellOrNull(row, cols, "email");
                    if (email == null || email.isBlank()) return;
                    result.add(new PersonSeeder.PersonSpec(
                            email,
                            getStringCellOrNull(row, cols, "nom"),
                            getStringCellOrNull(row, cols, "prenom"),
                            getStringCellOrNull(row, cols, IDENTIFIANT)
                    ));
                });
            } catch (Exception e) {
                errors.add(new ImportError(sheet.getSheetName(), 0, "", HEADER_READ_ERROR_PREFIX + e.getMessage()));
            }
        }
        return result;
    }

    public List<SpatialUnitSeeder.SpatialUnitSpecs> parseSpatialUnits(Sheet sheet, ActionUnitDTO actionUnit) {
        List<ImportError> errors = new ArrayList<>();
        return parseSpatialUnits(List.of(sheet), actionUnit, SheetMetadata.empty(), errors, new ImportProgress(), new ArrayList<>());
    }

    public List<SpatialUnitSeeder.SpatialUnitSpecs> parseSpatialUnits(List<Sheet> sheets, ActionUnitDTO actionUnit, SheetMetadata meta,
                                                                       List<ImportError> errors, ImportProgress progress,
                                                                       List<SpatialUnitRelSeeder.SpatialUnitRelDTO> childRelsOut) {
        Map<String, SpatialUnitSeeder.SpatialUnitSpecs> specsByName = new LinkedHashMap<>();

        for (Sheet sheet : sheets) {
            try {
                Map<String, Integer> cols = indexColumns(sheet.getRow(0), meta.columnAliases().getOrDefault(sheet.getSheetName(), Map.of()));
                forEachDataRow(sheet, errors, progress, row -> parseSpatialRow(row, cols, actionUnit, specsByName, childRelsOut));
            } catch (Exception e) {
                errors.add(new ImportError(sheet.getSheetName(), 0, "", HEADER_READ_ERROR_PREFIX + e.getMessage()));
            }
        }
        return new ArrayList<>(specsByName.values());
    }

    private void parseSpatialRow(Row row, Map<String, Integer> cols, ActionUnitDTO actionUnit,
                                  Map<String, SpatialUnitSeeder.SpatialUnitSpecs> specsByName,
                                  List<SpatialUnitRelSeeder.SpatialUnitRelDTO> childRelsOut) {
        String name = getStringCellOrNull(row, cols, "nom");
        if (name == null || name.isBlank()) return;

        String institution = actionUnit != null ? actionUnit.getCreatedByInstitution().getIdentifier() : getStringCellOrNull(row, cols, INSTITUTION);
        Long institutionDbId = actionUnit != null ? actionUnit.getCreatedByInstitution().getId() : null;
        String enfantsRaw  = getStringCellOrNull(row, cols, "enfants");

        ConceptSeeder.ConceptKey typeKey = conceptKeyFromColumnOrLabel(row, cols, "uri type", "type label",
                SpatialUnit.CATEGORY_FIELD_CODE, institutionDbId);

        SpatialUnitSeeder.SpatialUnitSpecs spec = new SpatialUnitSeeder.SpatialUnitSpecs(
                name,
                typeKey != null ? typeKey.vocabularyExtId() : null,
                typeKey != null ? typeKey.conceptExtId() : null,
                SIAMOIS_SYSTEM,
                institution
        );
        specsByName.put(name, spec);

        if (enfantsRaw != null && !enfantsRaw.isBlank()) {
            Arrays.stream(enfantsRaw.split("&&"))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .forEach(child -> childRelsOut.add(new SpatialUnitRelSeeder.SpatialUnitRelDTO(name, child)));
        }
    }

    public List<SpatialUnitRelSeeder.SpatialUnitRelDTO> parseSpatialUnitRels(List<Sheet> sheets, SheetMetadata meta, List<ImportError> errors, ImportProgress progress) {
        List<SpatialUnitRelSeeder.SpatialUnitRelDTO> specs = new ArrayList<>();
        for (Sheet sheet : sheets) {
            try {
                Map<String, Integer> cols = indexRequiredColumns(sheet, meta, PARENT, ENFANT);
                if (cols == null) continue;
                forEachDataRow(sheet, errors, progress, row -> parseRowToSpatialUnitRel(row, cols).ifPresent(specs::add));
            } catch (Exception e) {
                errors.add(new ImportError(sheet.getSheetName(), 0, "", HEADER_READ_ERROR_PREFIX + e.getMessage()));
            }
        }
        return specs;
    }

    private Optional<SpatialUnitRelSeeder.SpatialUnitRelDTO> parseRowToSpatialUnitRel(Row row, Map<String, Integer> cols) {
        String parent = getStringCellOrNull(row, cols, PARENT);
        String child  = getStringCellOrNull(row, cols, ENFANT);
        if (parent == null || parent.isBlank()) return Optional.empty();
        if (child  == null || child.isBlank())  return Optional.empty();
        return Optional.of(new SpatialUnitRelSeeder.SpatialUnitRelDTO(parent, child));
    }

    public List<RecordingUnitRelSeeder.RecordingUnitRelDTO> parseRecordingRels(Sheet sheet) {
        if (sheet == null) return List.of();
        List<ImportError> errors = new ArrayList<>();
        return parseRecordingRels(List.of(sheet), SheetMetadata.empty(), errors, new ImportProgress());
    }

    public List<RecordingUnitRelSeeder.RecordingUnitRelDTO> parseRecordingRels(List<Sheet> sheets, SheetMetadata meta, List<ImportError> errors, ImportProgress progress) {
        List<RecordingUnitRelSeeder.RecordingUnitRelDTO> specs = new ArrayList<>();
        for (Sheet sheet : sheets) {
            try {
                Map<String, Integer> cols = indexRequiredColumns(sheet, meta, PARENT, ENFANT);
                if (cols == null) continue;
                forEachDataRow(sheet, errors, progress, row -> parseRowToRecordingRel(row, cols).ifPresent(specs::add));
            } catch (Exception e) {
                errors.add(new ImportError(sheet.getSheetName(), 0, "", HEADER_READ_ERROR_PREFIX + e.getMessage()));
            }
        }
        return specs;
    }

    private Optional<RecordingUnitRelSeeder.RecordingUnitRelDTO> parseRowToRecordingRel(Row row, Map<String, Integer> cols) {
        String parent = getStringCellOrNull(row, cols, PARENT);
        String child  = getStringCellOrNull(row, cols, ENFANT);
        if (parent == null || parent.isBlank()) return Optional.empty();
        if (child  == null || child.isBlank())  return Optional.empty();
        return Optional.of(new RecordingUnitRelSeeder.RecordingUnitRelDTO(parent, child));
    }

    /**
     * Indexes a sheet's header columns and returns null if the header is missing or any
     * required column isn't present — a single guard for the "sheet not usable" case.
     */
    private Map<String, Integer> indexRequiredColumns(Sheet sheet, SheetMetadata meta, String... requiredColumns) {
        Row header = sheet.getRow(0);
        if (header == null) return null;
        Map<String, Integer> cols = indexColumns(header, meta.columnAliases().getOrDefault(sheet.getSheetName(), Map.of()));
        for (String required : requiredColumns) {
            if (cols.get(required) == null) return null;
        }
        return cols;
    }

    public List<RecordingUnitStratiRelSeeder.RecordingUnitStratiRelDTO> parseStratiRels(Sheet sheet) {
        return parseStratiRels(sheet, null);
    }

    public List<RecordingUnitStratiRelSeeder.RecordingUnitStratiRelDTO> parseStratiRels(Sheet sheet, @Nullable ActionUnitDTO actionUnit) {
        if (sheet == null) return List.of();
        List<ImportError> errors = new ArrayList<>();
        return parseStratiRels(List.of(sheet), actionUnit, SheetMetadata.empty(), errors, new ImportProgress());
    }

    public List<RecordingUnitStratiRelSeeder.RecordingUnitStratiRelDTO> parseStratiRels(List<Sheet> sheets, @Nullable ActionUnitDTO actionUnit,
                                                                                          SheetMetadata meta, List<ImportError> errors, ImportProgress progress) {
        Long institutionDbId = actionUnit != null ? actionUnit.getCreatedByInstitution().getId() : null;
        List<RecordingUnitStratiRelSeeder.RecordingUnitStratiRelDTO> specs = new ArrayList<>();
        for (Sheet sheet : sheets) {
            try {
                Map<String, Integer> cols = indexRequiredColumns(sheet, meta, "us1", "us2");
                if (cols == null) continue;
                forEachDataRow(sheet, errors, progress, row -> parseRowToStratiRel(row, cols, institutionDbId).ifPresent(specs::add));
            } catch (Exception e) {
                errors.add(new ImportError(sheet.getSheetName(), 0, "", HEADER_READ_ERROR_PREFIX + e.getMessage()));
            }
        }
        return specs;
    }

    private Optional<RecordingUnitStratiRelSeeder.RecordingUnitStratiRelDTO> parseRowToStratiRel(Row row, Map<String, Integer> cols, @Nullable Long institutionDbId) {
        String us1 = getStringCellOrNull(row, cols, "us1");
        String us2 = getStringCellOrNull(row, cols, "us2");
        if (us1 == null || us1.isBlank()) return Optional.empty();
        if (us2 == null || us2.isBlank()) return Optional.empty();
        ConceptSeeder.ConceptKey relKey = conceptKeyFromColumnOrLabel(row, cols, "relation", "relation label",
                RecordingUnit.STRATI_FIELD_CODE, institutionDbId);
        String direction  = getStringCellOrNull(row, cols, "direction vocabulaire");
        String asynchrone = getStringCellOrNull(row, cols, "asynchrone");
        String incertain  = getStringCellOrNull(row, cols, "incertain");
        return Optional.of(new RecordingUnitStratiRelSeeder.RecordingUnitStratiRelDTO(
                us1, us2, relKey,
                "True".equals(direction),
                "True".equals(asynchrone),
                "True".equals(incertain)));
    }

    public List<ActionCodeSeeder.ActionCodeSpec> parseActionCodes(Sheet sheet) {
        if (sheet == null) return List.of();
        List<ImportError> errors = new ArrayList<>();
        return parseActionCodes(List.of(sheet), SheetMetadata.empty(), errors, new ImportProgress());
    }

    public List<ActionCodeSeeder.ActionCodeSpec> parseActionCodes(List<Sheet> sheets, SheetMetadata meta, List<ImportError> errors, ImportProgress progress) {
        List<ActionCodeSeeder.ActionCodeSpec> specs = new ArrayList<>();
        for (Sheet sheet : sheets) {
            try {
                Map<String, Integer> cols = indexRequiredColumns(sheet, meta, "code", TYPE_URI);
                if (cols == null) continue;
                forEachDataRow(sheet, errors, progress, row -> parseRowToActionCode(row, cols).ifPresent(specs::add));
            } catch (Exception e) {
                errors.add(new ImportError(sheet.getSheetName(), 0, "", HEADER_READ_ERROR_PREFIX + e.getMessage()));
            }
        }
        return specs;
    }

    private Optional<ActionCodeSeeder.ActionCodeSpec> parseRowToActionCode(Row row, Map<String, Integer> cols) {
        String code = getStringCellOrNull(row, cols, "code");
        if (code == null || code.isBlank()) return Optional.empty();
        String typeUri = getStringCellOrNull(row, cols, TYPE_URI);
        return Optional.of(new ActionCodeSeeder.ActionCodeSpec(
                code,
                extractIdcFromUri(typeUri).orElse(null),
                extractIdtFromUri(typeUri).orElse(null)
        ));
    }

    public List<ActionUnitSeeder.ActionUnitSpecs> parseActionUnits(Sheet sheet) {
        if (sheet == null || sheet.getRow(0) == null) return List.of();
        List<ImportError> errors = new ArrayList<>();
        return parseActionUnits(List.of(sheet), SheetMetadata.empty(), errors, new ImportProgress());
    }

    public List<ActionUnitSeeder.ActionUnitSpecs> parseActionUnits(List<Sheet> sheets, SheetMetadata meta, List<ImportError> errors, ImportProgress progress) {
        List<ActionUnitSeeder.ActionUnitSpecs> result = new ArrayList<>();
        for (Sheet sheet : sheets) {
            try {
                if (sheet.getRow(0) == null) continue;
                Map<String, Integer> cols = indexColumns(sheet.getRow(0), meta.columnAliases().getOrDefault(sheet.getSheetName(), Map.of()));
                forEachDataRow(sheet, errors, progress, row -> parseRowToActionUnit(row, cols).ifPresent(result::add));
            } catch (Exception e) {
                errors.add(new ImportError(sheet.getSheetName(), 0, "", HEADER_READ_ERROR_PREFIX + e.getMessage()));
            }
        }
        return result;
    }

    private Optional<ActionUnitSeeder.ActionUnitSpecs> parseRowToActionUnit(Row row, Map<String, Integer> cols) {
        String name       = getStringCellOrNull(row, cols, "nom");
        String identifier = getStringCellOrNull(row, cols, IDENTIFIANT);

        if ((name == null || name.isBlank()) && (identifier == null || identifier.isBlank())) {
            return Optional.empty();
        }

        String code          = getOptionalCell(row, cols, "code");
        String typeUri       = getStringCellOrNull(row, cols, TYPE_URI);
        String createur      = getStringCellOrNull(row, cols, "createur");
        String institution   = getStringCellOrNull(row, cols, INSTITUTION);
        String contexteRaw   = getStringCellOrNull(row, cols, "contexte spatiale");

        String typeConceptId    = extractIdcFromUri(typeUri).orElse(null);
        String typeVocabularyId = extractIdtFromUri(typeUri).orElse(null);

        OffsetDateTime beginDate = parseOptionalDate(row, cols, "date debut");
        OffsetDateTime endDate   = parseOptionalDate(row, cols, "date fin");
        String mainLoc  = getStringCellOrNull(row, cols, "localisation principale");

        Set<SpatialUnitSeeder.SpatialUnitKey> spatialContextKeys = parseSpatialNames(contexteRaw);

        String fullIdentifier = identifier != null ? identifier : name;

        return Optional.of(new ActionUnitSeeder.ActionUnitSpecs(
                fullIdentifier,
                name,
                identifier,
                code,
                typeVocabularyId,
                typeConceptId,
                createur,
                institution,
                beginDate,
                endDate,
                spatialContextKeys,
                new SpatialUnitSeeder.SpatialUnitKey(mainLoc)
        ));
    }

    private String getOptionalCell(Row row, Map<String, Integer> cols, String key) {
        try {
            String val = getStringCellOrNull(row, cols, key);
            return (val == null || val.isBlank()) ? null : val;
        } catch (Exception e) {
            throw new IllegalStateException("[colonne '" + key + "'] : " + e.getMessage(), e);
        }
    }

    private OffsetDateTime parseOptionalDate(Row row, Map<String, Integer> cols, String key) {
        try {
            Integer col = cols.get(key);
            return col != null ? parseOffsetDateTime(row.getCell(col)) : null;
        } catch (Exception e) {
            throw new IllegalStateException("[colonne '" + key + "'] : " + e.getMessage(), e);
        }
    }

    public List<RecordingUnitSeeder.RecordingUnitSpecs> parseRecordingUnits(Sheet sheet, ImportScope scope, ActionUnitDTO actionUnit) {
        if (sheet == null || sheet.getRow(0) == null) return List.of();
        List<ImportError> errors = new ArrayList<>();
        return parseRecordingUnits(List.of(sheet), scope, actionUnit, SheetMetadata.empty(), errors, new ImportProgress());
    }

    public List<RecordingUnitSeeder.RecordingUnitSpecs> parseRecordingUnits(List<Sheet> sheets, ImportScope scope, ActionUnitDTO actionUnit, SheetMetadata meta, List<ImportError> errors, ImportProgress progress) {
        List<RecordingUnitSeeder.RecordingUnitSpecs> result = new ArrayList<>();
        for (Sheet sheet : sheets) {
            try {
                if (sheet.getRow(0) == null) continue;
                Map<String, Integer> cols = indexColumns(sheet.getRow(0), meta.columnAliases().getOrDefault(sheet.getSheetName(), Map.of()));
                forEachDataRow(sheet, errors, progress, row ->
                        parseRowToRecordingUnit(row, cols, scope == ImportScope.PROJECT ? actionUnit : null).ifPresent(result::add));
            } catch (Exception e) {
                errors.add(new ImportError(sheet.getSheetName(), 0, "", HEADER_READ_ERROR_PREFIX + e.getMessage()));
            }
        }
        return result;
    }

    private Optional<RecordingUnitSeeder.RecordingUnitSpecs> parseRowToRecordingUnit(
            Row row, Map<String, Integer> cols,
            @Nullable ActionUnitDTO actionUnit) {
        String identStr    = getStringCellOrNull(row, cols, IDENTIFIANT);
        String description = getStringCellOrNull(row, cols, DESCRIPTION);

        if ((identStr == null || identStr.isBlank()) && (description == null || description.isBlank())) {
            return Optional.empty();
        }

        Integer identifier = parseIntegerSafe(identStr);

        Long institutionDbId = actionUnit != null ? actionUnit.getCreatedByInstitution().getId() : null;
        ConceptSeeder.ConceptKey type           = conceptKeyFromColumnOrLabel(row, cols, TYPE_URI, "type label",
                RecordingUnit.TYPE_FIELD_CODE, institutionDbId);
        ConceptSeeder.ConceptKey geomorphCycle  = conceptKeyFromColumnOrLabel(row, cols, "cycle uri", "cycle label",
                RecordingUnit.GEOMORPHO_CYCLE_FIELD_CODE, institutionDbId);
        ConceptSeeder.ConceptKey geomorphAgent  = conceptKeyFromColumnOrLabel(row, cols, "agent uri", "agent label",
                RecordingUnit.GEOMORPHO_AGENT_FIELD_CODE, institutionDbId);
        ConceptSeeder.ConceptKey interpretation = conceptKeyFromColumnOrLabel(row, cols, "interpretation uri", "interpretation label",
                RecordingUnit.INTERPRETATION_FIELD_CODE, institutionDbId);

        String authorEmail  = getStringCellOrNull(row, cols, "author email");
        String institutionId = actionUnit != null ? actionUnit.getCreatedByInstitution().getIdentifier() : getStringCellOrNull(row, cols, INSTITUTION);
        List<String> excavators = parsePersonList(getStringCellOrNull(row, cols, "contributeurs email"));

        OffsetDateTime beginDate = parseOptionalDate(row, cols, "date d'ouverture");
        OffsetDateTime endDate   = parseOptionalDate(row, cols, "date de fermeture");

        OffsetDateTime creationTime = OffsetDateTime.now(ZoneOffset.UTC);
        String createdBy = SIAMOIS_SYSTEM;

        SpatialUnitSeeder.SpatialUnitKey spatialKey = parseOptionalSpatialUnit(row, cols, "unite spatiale");
        ActionUnitSeeder.ActionUnitKey actionKey    = actionUnit != null ? new ActionUnitSeeder.ActionUnitKey(
                actionUnit.getFullIdentifier(),
                actionUnit.getCreatedByInstitution().getIdentifier())
                : parseOptionalActionUnit(row, cols, "unite d'action", "");

        String matrixColor   = getStringCellOrNull(row, cols.get("couleur de la matrice"));
        String matrixTexture = getStringCellOrNull(row, cols.get("texture de la matrice"));
        String matrixComp    = getStringCellOrNull(row, cols.get("composition de la matrice"));

        List<String> phaseIdentifiers = parsePersonList(getStringCellOrNull(row, cols, "phases"));

        return Optional.of(new RecordingUnitSeeder.RecordingUnitSpecs(
                identStr,
                identifier,
                type,
                geomorphCycle,
                geomorphAgent,
                interpretation,
                authorEmail,
                institutionId,
                authorEmail,   // author
                createdBy,
                excavators,
                creationTime,
                beginDate,
                endDate,
                spatialKey,
                actionKey,
                description,
                matrixColor,
                matrixComp,
                matrixTexture,
                phaseIdentifiers
        ));
    }

    private SpatialUnitSeeder.SpatialUnitKey parseOptionalSpatialUnit(Row row, Map<String, Integer> cols, String key) {
        try {
            String name = getStringCellOrNull(row, cols, key);
            return (name == null || name.isBlank()) ? null : new SpatialUnitSeeder.SpatialUnitKey(name.trim());
        } catch (Exception e) {
            throw new IllegalStateException("[colonne '" + key + "'] : " + e.getMessage(), e);
        }
    }

    private ActionUnitSeeder.ActionUnitKey parseOptionalActionUnit(Row row, Map<String, Integer> cols, String key, String institutionIdentifier) {
        try {
            String ident = getStringCellOrNull(row, cols, key);
            return (ident == null || ident.isBlank()) ? null : new ActionUnitSeeder.ActionUnitKey(ident.trim(), institutionIdentifier);
        } catch (Exception e) {
            throw new IllegalStateException("[colonne '" + key + "'] : " + e.getMessage(), e);
        }
    }

    private RecordingUnitSeeder.RecordingUnitKey parseRecordingUnitKey(Row row, Map<String, Integer> cols, ActionUnitDTO actionUnit) {
        String ruStr = getStringCellOrNull(row, cols, "unite d'enregistrement");
        if (ruStr == null || ruStr.isBlank()) return null;
        String actionFullId = actionUnit != null ? actionUnit.getFullIdentifier() : "";
        return new RecordingUnitSeeder.RecordingUnitKey(ruStr, actionFullId);
    }

    public List<SpecimenSeeder.SpecimenSpecs> parseSpecimens(Sheet sheet, ActionUnitDTO actionUnit) {
        if (sheet == null) return List.of();
        List<ImportError> errors = new ArrayList<>();
        return parseSpecimens(List.of(sheet), actionUnit, SheetMetadata.empty(), errors, new ImportProgress());
    }

    public List<SpecimenSeeder.SpecimenSpecs> parseSpecimens(List<Sheet> sheets, ActionUnitDTO actionUnit, SheetMetadata meta, List<ImportError> errors, ImportProgress progress) {
        List<SpecimenSeeder.SpecimenSpecs> result = new ArrayList<>();
        for (Sheet sheet : sheets) {
            try {
                Row header = sheet.getRow(0);
                if (header == null) continue;
                Map<String, Integer> cols = indexColumns(header, meta.columnAliases().getOrDefault(sheet.getSheetName(), Map.of()));
                forEachDataRow(sheet, errors, progress, row -> parseRowToSpecimen(row, cols, actionUnit).ifPresent(result::add));
            } catch (Exception e) {
                errors.add(new ImportError(sheet.getSheetName(), 0, "", HEADER_READ_ERROR_PREFIX + e.getMessage()));
            }
        }
        return result;
    }

    private Optional<SpecimenSeeder.SpecimenSpecs> parseRowToSpecimen(Row row, Map<String, Integer> cols, ActionUnitDTO actionUnit) {
        String identStr = getStringCellOrNull(row, cols, IDENTIFIANT);
        if (identStr == null || identStr.isBlank()) return Optional.empty();

        String institutionId = actionUnit != null ? actionUnit.getCreatedByInstitution().getIdentifier() : getStringCellOrNull(row, cols, INSTITUTION);
        String auteurFiche   = getStringCellOrNull(row, cols, "auteur fiche email");
        List<String> authors = (auteurFiche == null || auteurFiche.isBlank()) ? List.of() : List.of(auteurFiche.trim());

        return Optional.of(new SpecimenSeeder.SpecimenSpecs(
                identStr,
                parseIntegerSafe(identStr),
                conceptKeyFromColumn(row, cols, "matiere"),
                conceptKeyFromColumn(row, cols, "categorie"),
                conceptKeyFromColumn(row, cols, "designation"),
                SIAMOIS_SYSTEM,
                institutionId,
                authors,
                parsePersonList(getStringCellOrNull(row, cols, "collecteurs emails")),
                OffsetDateTime.now(ZoneOffset.UTC),
                parseRecordingUnitKey(row, cols, actionUnit)
        ));
    }

    public List<PhaseSeeder.PhaseSpecs> parsePhases(Sheet sheet, ActionUnitDTO actionUnit) {
        if (sheet == null) return List.of();
        List<ImportError> errors = new ArrayList<>();
        return parsePhases(List.of(sheet), actionUnit, SheetMetadata.empty(), errors, new ImportProgress());
    }

    public List<PhaseSeeder.PhaseSpecs> parsePhases(List<Sheet> sheets, ActionUnitDTO actionUnit, SheetMetadata meta, List<ImportError> errors, ImportProgress progress) {
        List<PhaseSeeder.PhaseSpecs> result = new ArrayList<>();
        for (Sheet sheet : sheets) {
            try {
                Row header = sheet.getRow(0);
                if (header == null) continue;
                Map<String, Integer> cols = indexColumns(header, meta.columnAliases().getOrDefault(sheet.getSheetName(), Map.of()));

                forEachDataRow(sheet, errors, progress, row -> {
                    String identifier = getStringCellOrNull(row, cols, IDENTIFIANT);
                    if (identifier == null || identifier.isBlank()) return;

                    String title       = getStringCellOrNull(row, cols, "titre");
                    ConceptSeeder.ConceptKey type = conceptKeyFromColumn(row, cols, TYPE_URI);
                    String description  = getStringCellOrNull(row, cols, DESCRIPTION);
                    Integer orderNumber = getIntegerCellOrNull(row, cols, "ordre");
                    Integer lowerBound  = getIntegerCellOrNull(row, cols, "borne inferieure");
                    Integer upperBound  = getIntegerCellOrNull(row, cols, "borne superieure");
                    String authorEmail  = getStringCellOrNull(row, cols, "auteur");

                    ActionUnitSeeder.ActionUnitKey actionKey = actionUnit != null
                            ? new ActionUnitSeeder.ActionUnitKey(
                                    actionUnit.getFullIdentifier(),
                                    actionUnit.getCreatedByInstitution().getIdentifier())
                            : new ActionUnitSeeder.ActionUnitKey(
                                    getStringCellOrNull(row, cols, "projet"),
                                    getStringCellOrNull(row, cols, INSTITUTION));

                    result.add(new PhaseSeeder.PhaseSpecs(
                            identifier, title, type, description, orderNumber, lowerBound, upperBound, authorEmail, actionKey));
                });
            } catch (Exception e) {
                errors.add(new ImportError(sheet.getSheetName(), 0, "", HEADER_READ_ERROR_PREFIX + e.getMessage()));
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // URI helpers
    // -------------------------------------------------------------------------

    public String extractThesaurusDomain(String thesaurus) {
        if (thesaurus == null) return null;
        int idx = thesaurus.indexOf("?");
        if (idx < 0) {
            return thesaurus;
        }
        if (idx == 0) {
            return null;
        }
        return thesaurus.substring(0, idx - 1);
    }

    public Optional<String> extractIdtFromUri(String uri) {
        return extractQueryParam(uri, "idt=");
    }

    public Optional<String> extractIdcFromUri(String uri) {
        return extractQueryParam(uri, "idc=");
    }

    private Optional<String> extractQueryParam(String uri, String paramKey) {
        if (uri == null) return Optional.empty();
        int idx = uri.indexOf(paramKey);
        if (idx < 0) throw new IllegalStateException("URL de concept " + uri + " invalide");
        String sub = uri.substring(idx + paramKey.length());
        int amp = sub.indexOf('&');
        if (amp >= 0) sub = sub.substring(0, amp);
        return Optional.of(URLDecoder.decode(sub, StandardCharsets.UTF_8));
    }

    public ConceptSeeder.ConceptKey conceptKeyFromUri(String uri) {
        if (uri == null || uri.isBlank()) {
            return null;
        }
        String conceptId = extractIdcFromUri(uri).orElse(null);
        String vocabId   = extractIdtFromUri(uri).orElse(null);
        if (conceptId == null && vocabId == null) {
            return null;
        }
        return new ConceptSeeder.ConceptKey(vocabId, conceptId);
    }

    /**
     * Reads a column's cell and parses it as a concept URI, tagging any failure — cell read
     * or URI parsing — with the column name so it surfaces correctly in the validation UI.
     */
    private ConceptSeeder.ConceptKey conceptKeyFromColumn(Row row, Map<String, Integer> cols, String key) {
        String raw = getStringCellOrNull(row, cols, key);
        try {
            return conceptKeyFromUri(raw);
        } catch (Exception e) {
            throw new IllegalStateException("[colonne '" + key + "'] : " + e.getMessage(), e);
        }
    }

    /**
     * Resolves a concept from either a URI column or, if the URI is blank, a label column matched
     * against the institution's configured thesaurus for the given field code. The URI column
     * always wins when present — the label is only consulted as a fallback.
     */
    private ConceptSeeder.ConceptKey conceptKeyFromColumnOrLabel(Row row, Map<String, Integer> cols, String uriKey, String labelKey,
                                                                  String fieldCode, Long institutionId) {
        String uri = getStringCellOrNull(row, cols, uriKey);
        if (uri != null && !uri.isBlank()) {
            return conceptKeyFromColumn(row, cols, uriKey);
        }
        String label = getStringCellOrNull(row, cols, labelKey);
        if (label == null || label.isBlank()) return null;
        if (institutionId == null) {
            throw new IllegalStateException("[colonne '" + labelKey + "'] : résolution par label indisponible hors contexte projet");
        }
        try {
            Concept concept = conceptService.resolveConceptByLabel(institutionId, fieldCode, label);
            return new ConceptSeeder.ConceptKey(concept.getVocabulary().getExternalVocabularyId(), concept.getExternalId());
        } catch (Exception e) {
            throw new IllegalStateException("[colonne '" + labelKey + "'] : " + e.getMessage(), e);
        }
    }

}
