package fr.siamois.infrastructure.dataimport;

import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.infrastructure.database.initializer.seeder.*;
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
import java.util.stream.Collectors;

import static fr.siamois.infrastructure.dataimport.ExcelCellHelper.*;
import static fr.siamois.infrastructure.dataimport.ImportSchema.*;


@Service
public class OOXMLImportService {

    private static final String HEADER_READ_ERROR_PREFIX = "Erreur lecture en-tête: ";

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
        try (Workbook workbook = WorkbookFactory.create(is)) {

            SheetMetadata meta = readSheetMetadata(workbook);
            List<ImportError> errors = new ArrayList<>();

            List<InstitutionSeeder.InstitutionSpec>             institutions  = new ArrayList<>();
            List<PersonSeeder.PersonSpec>                       persons       = new ArrayList<>();
            List<ActionCodeSeeder.ActionCodeSpec>               actionCodes   = new ArrayList<>();
            List<ActionUnitSeeder.ActionUnitSpecs>              actionUnits   = new ArrayList<>();

            if (scope == ImportScope.ALL) {
                institutions = parseInstitutions(getSheetsForTable(workbook, meta, INSTITUTION), meta, errors);
                persons      = parsePersons(getSheetsForTable(workbook, meta, PERSON), meta, errors);
                actionCodes  = parseActionCodes(getSheetsForTable(workbook, meta, "code"), meta, errors);
                actionUnits  = parseActionUnits(getSheetsForTable(workbook, meta, "action_unit"), meta, errors);
            }

            List<SpatialUnitSeeder.SpatialUnitSpecs>                          spatialUnits   = parseSpatialUnits(getSheetsForTable(workbook, meta, "spatial_unit"), actionUnitDTO, meta, errors);
            List<RecordingUnitSeeder.RecordingUnitSpecs>                      recordingUnits = parseRecordingUnits(getSheetsForTable(workbook, meta, "recording_unit"), scope, actionUnitDTO, meta, errors);
            List<SpecimenSeeder.SpecimenSpecs>                                specimenSpecs  = parseSpecimens(getSheetsForTable(workbook, meta, "specimen"), actionUnitDTO, meta, errors);
            List<PhaseSeeder.PhaseSpecs>                                      phaseSpecs     = parsePhases(getSheetsForTable(workbook, meta, "phase"), actionUnitDTO, meta, errors);
            List<RecordingUnitRelSeeder.RecordingUnitRelDTO>                  recordingRels  = parseRecordingRels(getSheetsForTable(workbook, meta, "recordingRel"), meta, errors);
            List<RecordingUnitStratiRelSeeder.RecordingUnitStratiRelDTO>      stratiRels     = parseStratiRels(getSheetsForTable(workbook, meta, "stratiRel"), meta, errors);

            ImportSpecs specs = new ImportSpecs(institutions, persons, spatialUnits, actionCodes, actionUnits,
                    recordingUnits, specimenSpecs, phaseSpecs, recordingRels, stratiRels);

            Map<String, List<String>> allSheetColumns = collectAllSheetColumns(workbook);

            return new ImportResult(specs, errors, meta, allSheetColumns);
        }
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
        return parseInstitutions(List.of(sheet), SheetMetadata.empty(), errors);
    }

    public List<InstitutionSeeder.InstitutionSpec> parseInstitutions(List<Sheet> sheets, SheetMetadata meta, List<ImportError> errors) {
        List<InstitutionSeeder.InstitutionSpec> result = new ArrayList<>();
        for (Sheet sheet : sheets) {
            try {
                Row header = sheet.getRow(0);
                Map<String, Integer> cols = indexColumns(header, meta.columnAliases().getOrDefault(sheet.getSheetName(), Map.of()));
                forEachDataRow(sheet, errors, row -> {
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
        return parsePersons(List.of(sheet), SheetMetadata.empty(), errors);
    }

    public List<PersonSeeder.PersonSpec> parsePersons(List<Sheet> sheets, SheetMetadata meta, List<ImportError> errors) {
        List<PersonSeeder.PersonSpec> result = new ArrayList<>();
        for (Sheet sheet : sheets) {
            try {
                Row header = sheet.getRow(0);
                Map<String, Integer> cols = indexColumns(header, meta.columnAliases().getOrDefault(sheet.getSheetName(), Map.of()));
                forEachDataRow(sheet, errors, row -> {
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
        return parseSpatialUnits(List.of(sheet), actionUnit, SheetMetadata.empty(), errors);
    }

    public List<SpatialUnitSeeder.SpatialUnitSpecs> parseSpatialUnits(List<Sheet> sheets, ActionUnitDTO actionUnit, SheetMetadata meta, List<ImportError> errors) {
        Map<String, SpatialUnitSeeder.SpatialUnitSpecs> specsByName = new LinkedHashMap<>();
        Map<String, String> childrenStringByName = new HashMap<>();

        for (Sheet sheet : sheets) {
            try {
                Map<String, Integer> cols = indexColumns(sheet.getRow(0), meta.columnAliases().getOrDefault(sheet.getSheetName(), Map.of()));
                forEachDataRow(sheet, errors, row -> parseSpatialRow(row, cols, actionUnit, specsByName, childrenStringByName));
            } catch (Exception e) {
                errors.add(new ImportError(sheet.getSheetName(), 0, "", HEADER_READ_ERROR_PREFIX + e.getMessage()));
            }
        }
        resolveChildrenKeys(specsByName, childrenStringByName);
        return new ArrayList<>(specsByName.values());
    }

    private void parseSpatialRow(Row row, Map<String, Integer> cols, ActionUnitDTO actionUnit,
                                  Map<String, SpatialUnitSeeder.SpatialUnitSpecs> specsByName,
                                  Map<String, String> childrenStringByName) {
        String name = getStringCellOrNull(row, cols, "nom");
        if (name == null || name.isBlank()) return;

        String uriType     = getStringCellOrNull(row, cols, "uri type");
        String institution = actionUnit != null ? actionUnit.getCreatedByInstitution().getIdentifier() : getStringCellOrNull(row, cols, INSTITUTION);
        String enfantsRaw  = getStringCellOrNull(row, cols, "enfants");

        SpatialUnitSeeder.SpatialUnitSpecs spec = new SpatialUnitSeeder.SpatialUnitSpecs(
                name,
                extractIdtFromUri(uriType).orElse(null),
                extractIdcFromUri(uriType).orElse(null),
                SIAMOIS_SYSTEM,
                institution,
                new HashSet<>()
        );
        specsByName.put(name, spec);

        if (enfantsRaw != null && !enfantsRaw.isBlank()) {
            childrenStringByName.put(name, enfantsRaw);
        }
    }

    private void resolveChildrenKeys(Map<String, SpatialUnitSeeder.SpatialUnitSpecs> specsByName,
                                      Map<String, String> childrenStringByName) {
        for (Map.Entry<String, String> entry : childrenStringByName.entrySet()) {
            Set<SpatialUnitSeeder.SpatialUnitKey> childrenKeys = Arrays.stream(entry.getValue().split("&&"))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .map(specsByName::get)
                    .filter(Objects::nonNull)
                    .map(child -> new SpatialUnitSeeder.SpatialUnitKey(child.name()))
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            SpatialUnitSeeder.SpatialUnitSpecs parentSpec = specsByName.get(entry.getKey());
            if (parentSpec != null && !childrenKeys.isEmpty()) {
                parentSpec.childrenKey().addAll(childrenKeys);
            }
        }
    }

    public List<RecordingUnitRelSeeder.RecordingUnitRelDTO> parseRecordingRels(Sheet sheet) {
        if (sheet == null) return List.of();
        List<ImportError> errors = new ArrayList<>();
        return parseRecordingRels(List.of(sheet), SheetMetadata.empty(), errors);
    }

    public List<RecordingUnitRelSeeder.RecordingUnitRelDTO> parseRecordingRels(List<Sheet> sheets, SheetMetadata meta, List<ImportError> errors) {
        List<RecordingUnitRelSeeder.RecordingUnitRelDTO> specs = new ArrayList<>();
        for (Sheet sheet : sheets) {
            try {
                Map<String, Integer> cols = indexRequiredColumns(sheet, meta, "parent", "enfant");
                if (cols == null) continue;
                Integer parentNum = cols.get("parent");
                Integer childNum  = cols.get("enfant");
                forEachDataRow(sheet, errors, row -> parseRowToRecordingRel(row, parentNum, childNum).ifPresent(specs::add));
            } catch (Exception e) {
                errors.add(new ImportError(sheet.getSheetName(), 0, "", HEADER_READ_ERROR_PREFIX + e.getMessage()));
            }
        }
        return specs;
    }

    private Optional<RecordingUnitRelSeeder.RecordingUnitRelDTO> parseRowToRecordingRel(Row row, int parentCol, int childCol) {
        String parent = getStringCell(row, parentCol);
        String child  = getStringCell(row, childCol);
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
        if (header == null) return new HashMap<>();
        Map<String, Integer> cols = indexColumns(header, meta.columnAliases().getOrDefault(sheet.getSheetName(), Map.of()));
        for (String required : requiredColumns) {
            if (cols.get(required) == null) return new HashMap<>();
        }
        return cols;
    }

    public List<RecordingUnitStratiRelSeeder.RecordingUnitStratiRelDTO> parseStratiRels(Sheet sheet) {
        if (sheet == null) return List.of();
        List<ImportError> errors = new ArrayList<>();
        return parseStratiRels(List.of(sheet), SheetMetadata.empty(), errors);
    }

    public List<RecordingUnitStratiRelSeeder.RecordingUnitStratiRelDTO> parseStratiRels(List<Sheet> sheets, SheetMetadata meta, List<ImportError> errors) {
        List<RecordingUnitStratiRelSeeder.RecordingUnitStratiRelDTO> specs = new ArrayList<>();
        for (Sheet sheet : sheets) {
            try {
                Map<String, Integer> cols = indexRequiredColumns(sheet, meta, "us1", "us2");
                if (cols == null) continue;
                forEachDataRow(sheet, errors, row -> parseRowToStratiRel(row, cols).ifPresent(specs::add));
            } catch (Exception e) {
                errors.add(new ImportError(sheet.getSheetName(), 0, "", HEADER_READ_ERROR_PREFIX + e.getMessage()));
            }
        }
        return specs;
    }

    private Optional<RecordingUnitStratiRelSeeder.RecordingUnitStratiRelDTO> parseRowToStratiRel(Row row, Map<String, Integer> cols) {
        String us1 = getStringCellOrNull(row, cols, "us1");
        String us2 = getStringCellOrNull(row, cols, "us2");
        if (us1 == null || us1.isBlank()) return Optional.empty();
        if (us2 == null || us2.isBlank()) return Optional.empty();
        ConceptSeeder.ConceptKey relKey = conceptKeyFromUri(getStringCellOrNull(row, cols, "relation"));
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
        return parseActionCodes(List.of(sheet), SheetMetadata.empty(), errors);
    }

    public List<ActionCodeSeeder.ActionCodeSpec> parseActionCodes(List<Sheet> sheets, SheetMetadata meta, List<ImportError> errors) {
        List<ActionCodeSeeder.ActionCodeSpec> specs = new ArrayList<>();
        for (Sheet sheet : sheets) {
            try {
                Map<String, Integer> cols = indexRequiredColumns(sheet, meta, "code", TYPE_URI);
                if (cols == null) continue;
                forEachDataRow(sheet, errors, row -> parseRowToActionCode(row, cols).ifPresent(specs::add));
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
        return parseActionUnits(List.of(sheet), SheetMetadata.empty(), errors);
    }

    public List<ActionUnitSeeder.ActionUnitSpecs> parseActionUnits(List<Sheet> sheets, SheetMetadata meta, List<ImportError> errors) {
        List<ActionUnitSeeder.ActionUnitSpecs> result = new ArrayList<>();
        for (Sheet sheet : sheets) {
            try {
                if (sheet.getRow(0) == null) continue;
                Map<String, Integer> cols = indexColumns(sheet.getRow(0), meta.columnAliases().getOrDefault(sheet.getSheetName(), Map.of()));
                forEachDataRow(sheet, errors, row -> parseRowToActionUnit(row, cols).ifPresent(result::add));
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
        return parseRecordingUnits(List.of(sheet), scope, actionUnit, SheetMetadata.empty(), errors);
    }

    public List<RecordingUnitSeeder.RecordingUnitSpecs> parseRecordingUnits(List<Sheet> sheets, ImportScope scope, ActionUnitDTO actionUnit, SheetMetadata meta, List<ImportError> errors) {
        List<RecordingUnitSeeder.RecordingUnitSpecs> result = new ArrayList<>();
        for (Sheet sheet : sheets) {
            try {
                if (sheet.getRow(0) == null) continue;
                Map<String, Integer> cols = indexColumns(sheet.getRow(0), meta.columnAliases().getOrDefault(sheet.getSheetName(), Map.of()));
                forEachDataRow(sheet, errors, row ->
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

        ConceptSeeder.ConceptKey type           = conceptKeyFromUri(getStringCellOrNull(row, cols, TYPE_URI));
        ConceptSeeder.ConceptKey geomorphCycle  = conceptKeyFromUri(getStringCellOrNull(row, cols, "cycle uri"));
        ConceptSeeder.ConceptKey geomorphAgent  = conceptKeyFromUri(getStringCellOrNull(row, cols, "agent uri"));
        ConceptSeeder.ConceptKey interpretation = conceptKeyFromUri(getOptionalCell(row, cols, "interpretation uri"));

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
        return parseSpecimens(List.of(sheet), actionUnit, SheetMetadata.empty(), errors);
    }

    public List<SpecimenSeeder.SpecimenSpecs> parseSpecimens(List<Sheet> sheets, ActionUnitDTO actionUnit, SheetMetadata meta, List<ImportError> errors) {
        List<SpecimenSeeder.SpecimenSpecs> result = new ArrayList<>();
        for (Sheet sheet : sheets) {
            try {
                Row header = sheet.getRow(0);
                if (header == null) continue;
                Map<String, Integer> cols = indexColumns(header, meta.columnAliases().getOrDefault(sheet.getSheetName(), Map.of()));
                forEachDataRow(sheet, errors, row -> parseRowToSpecimen(row, cols, actionUnit).ifPresent(result::add));
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
                conceptKeyFromUri(getStringCellOrNull(row, cols, "matiere")),
                conceptKeyFromUri(getStringCellOrNull(row, cols, "categorie")),
                conceptKeyFromUri(getStringCellOrNull(row, cols, "designation")),
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
        return parsePhases(List.of(sheet), actionUnit, SheetMetadata.empty(), errors);
    }

    public List<PhaseSeeder.PhaseSpecs> parsePhases(List<Sheet> sheets, ActionUnitDTO actionUnit, SheetMetadata meta, List<ImportError> errors) {
        List<PhaseSeeder.PhaseSpecs> result = new ArrayList<>();
        for (Sheet sheet : sheets) {
            try {
                Row header = sheet.getRow(0);
                if (header == null) continue;
                Map<String, Integer> cols = indexColumns(header, meta.columnAliases().getOrDefault(sheet.getSheetName(), Map.of()));

                forEachDataRow(sheet, errors, row -> {
                    String identifier = getStringCellOrNull(row, cols, IDENTIFIANT);
                    if (identifier == null || identifier.isBlank()) return;

                    String title       = getStringCellOrNull(row, cols, "titre");
                    ConceptSeeder.ConceptKey type = conceptKeyFromUri(getStringCellOrNull(row, cols, TYPE_URI));
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

}
