package fr.siamois.infrastructure.dataimport;

import fr.siamois.infrastructure.database.initializer.seeder.*;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;


@Service
public class OOXMLImportService {

    public static final String IDENTIFIANT = "identifiant";
    public static final String PERSON = "person";
    public static final String INSTITUTION = "institution";
    public static final String TYPE_URI = "type uri";

    public Map<String, String> readSheetMetadata(Workbook workbook) {
        Sheet metaSheet = workbook.getSheet("sheet_metadata");
        if (metaSheet == null) {
            return fallbackSheetMapping(workbook);
        }
        return readMetadataSheet(metaSheet);
    }

    private Map<String, String> fallbackSheetMapping(Workbook workbook) {
        Map<String, String> map = new HashMap<>();
        for (Sheet s : workbook) {
            String nameNorm = normalize(s.getSheetName());
            if (nameNorm.contains(INSTITUTION)) {
                map.put(INSTITUTION, s.getSheetName());
            } else if (nameNorm.contains(PERSON)) {
                map.put(PERSON, s.getSheetName());
            } else if (nameNorm.contains("unite") && nameNorm.contains("spatiale")) {
                map.put("spatial_unit", s.getSheetName());
            } else if (nameNorm.contains("unite") && nameNorm.contains("action")) {
                map.put("action_unit", s.getSheetName());
            }
        }
        return map;
    }

    private Map<String, String> readMetadataSheet(Sheet metaSheet) {
        Map<String, String> map = new HashMap<>();
        Row header = metaSheet.getRow(0);
        if (header == null) return map;

        Map<String, Integer> colIndex = indexColumns(header);
        Integer sheetIdCol = colIndex.get("sheet_id");
        Integer sheetNameCol = colIndex.get("sheet_name");
        if (sheetIdCol == null || sheetNameCol == null) return map;

        for (int r = 1; r <= metaSheet.getLastRowNum(); r++) {
            addRowToMap(metaSheet.getRow(r), sheetIdCol, sheetNameCol, map);
        }
        return map;
    }

    private void addRowToMap(Row row, int sheetIdCol, int sheetNameCol, Map<String, String> map) {
        if (row == null) return;
        String id = getStringCell(row, sheetIdCol);
        String name = getStringCell(row, sheetNameCol);
        if (id != null && name != null) {
            map.put(id.trim(), name.trim());
        }
    }


    public ImportSpecs importFromExcel(InputStream is) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(is)) {

            Map<String, String> sheetIdToName = readSheetMetadata(workbook);

            Sheet institutionSheet = workbook.getSheet(sheetIdToName.getOrDefault(INSTITUTION, "Institution"));
            Sheet personSheet      = workbook.getSheet(sheetIdToName.getOrDefault(PERSON, "Personne"));
            Sheet spatialSheet     = workbook.getSheet(sheetIdToName.getOrDefault("spatial_unit", "Unité spatiale"));
            Sheet codeSheet        = workbook.getSheet(sheetIdToName.getOrDefault("code", "Code"));
            Sheet actionUnitSheet  = workbook.getSheet(sheetIdToName.getOrDefault("action_unit", "Unite action"));
            Sheet recordingUnitSheet  = workbook.getSheet(sheetIdToName.getOrDefault("recording_unit", "Unite action"));
            Sheet specimenSheet  = workbook.getSheet(sheetIdToName.getOrDefault("specimen", "Prelev."));
            Sheet recordingRelSheet  = workbook.getSheet(sheetIdToName.getOrDefault("recordingRel", "UE_rel"));
            Sheet stratiSheet  = workbook.getSheet(sheetIdToName.getOrDefault("stratiRel", "Strati_Rel"));

            List<InstitutionSeeder.InstitutionSpec> institutions = parseInstitutions(institutionSheet);
            List<PersonSeeder.PersonSpec> persons = parsePersons(personSheet);
            List<SpatialUnitSeeder.SpatialUnitSpecs> spatialUnits = parseSpatialUnits(spatialSheet);
            List<ActionCodeSeeder.ActionCodeSpec> actionCodes = parseActionCodes(codeSheet);
            List<ActionUnitSeeder.ActionUnitSpecs> actionUnits = parseActionUnits(actionUnitSheet);
            List<RecordingUnitSeeder.RecordingUnitSpecs> recordingUnits = parseRecordingUnits(recordingUnitSheet);
            List<SpecimenSeeder.SpecimenSpecs> specimenSpecs = parseSpecimens(specimenSheet);
            List<RecordingUnitRelSeeder.RecordingUnitDTO> recordingUnitDTOS = parseRecordingRels(recordingRelSheet);
            List<RecordingUnitStratiRelSeeder.RecordingUnitStratiRelDTO> stratiDTOS = parseStratiRels(stratiSheet);

            return new ImportSpecs(institutions, persons, spatialUnits, actionCodes, actionUnits,
                    recordingUnits, specimenSpecs, recordingUnitDTOS, stratiDTOS);
        }
    }

    public List<InstitutionSeeder.InstitutionSpec> parseInstitutions(Sheet sheet) {
        Row header = sheet.getRow(0);
        Map<String, Integer> cols = indexColumns(header);

        Integer colName        = cols.get("nom");
        Integer colDescription = cols.get("description");
        Integer colIdentifier  = cols.get(IDENTIFIANT);
        Integer colAdmins      = cols.get("email admins");
        Integer colThesaurus   = cols.get("thesaurus");

        List<InstitutionSeeder.InstitutionSpec> result = new ArrayList<>();

        forEachDataRow(sheet, row -> {
            String name = colName != null ? getStringCell(row, colName) : null;
            if (name == null || name.isBlank()) return; // ligne vide

            String description = getStringCellOrNull(row, colDescription);
            String identifier  = getStringCellOrNull(row, colIdentifier);
            String adminsRaw   = getStringCellOrNull(row, colAdmins);
            String thesaurus   = getStringCellOrNull(row, colThesaurus);

            List<String> adminEmails = parseEmailList(adminsRaw);

            String vocabularyId       = extractIdtFromUri(thesaurus).orElse(null);
            String thesaurusInstance  = extractThesaurusDomain(thesaurus);

            result.add(new InstitutionSeeder.InstitutionSpec(
                    name,
                    description,
                    identifier,
                    adminEmails,
                    thesaurusInstance,
                    vocabularyId
            ));
        });
        return result;
    }

    public String extractThesaurusDomain(String thesaurus) {
        if (thesaurus == null) return null;
        int idx = thesaurus.indexOf("?");
        if (idx < 0) {
            return thesaurus;
        }

        if(idx==0) {
            return null;
        }

        return thesaurus.substring(0, idx-1);
    }

    public List<PersonSeeder.PersonSpec> parsePersons(Sheet sheet) {
        Row header = sheet.getRow(0);
        Map<String, Integer> cols = indexColumns(header);

        Integer colEmail       = cols.get("email");
        Integer colNom         = cols.get("nom");
        Integer colPrenom      = cols.get("prenom");
        Integer colIdentifiant = cols.get(IDENTIFIANT);

        List<PersonSeeder.PersonSpec> result = new ArrayList<>();

        forEachDataRow(sheet, row -> {
            String email =  getStringCellOrNull(row, colEmail);
            if (email == null || email.isBlank()) return;

            String nom    = getStringCellOrNull(row, colNom);
            String prenom = getStringCellOrNull(row, colPrenom);
            String ident  = getStringCellOrNull(row, colIdentifiant);

            result.add(new PersonSeeder.PersonSpec(
                    email,
                    nom,
                    prenom,
                    ident
            ));
        });

        return result;
    }

    public List<SpatialUnitSeeder.SpatialUnitSpecs> parseSpatialUnits(Sheet sheet) {
        Row header = sheet.getRow(0);
        Map<String, Integer> cols = indexColumns(header);

        Integer colNom         = cols.get("nom");
        Integer colUriType     = cols.get("uri type");
        Integer colCreateur    = cols.get("createur");
        Integer colInstitution = cols.get(INSTITUTION);
        Integer colEnfants     = cols.get("enfants");

        // Première passe : créer les specs SANS enfants, et indexer par nom
        Map<String, SpatialUnitSeeder.SpatialUnitSpecs> specsByName = new LinkedHashMap<>();
        Map<String, String> childrenStringByName = new HashMap<>();

        forEachDataRow(sheet, row -> {
            String name = colNom != null ? getStringCell(row, colNom) : null; getStringCellOrNull(row, colNom);
            if (name == null || name.isBlank()) return;

            String uriType      = getStringCellOrNull(row, colUriType);
            String creatorEmail = getStringCellOrNull(row, colCreateur);
            String institution  = getStringCellOrNull(row, colInstitution);
            String enfantsRaw   = getStringCellOrNull(row, colEnfants);

            // 🔹 idt = vocabularyId, idc = conceptId
            String vocabularyId = extractIdtFromUri(uriType).orElse(null);
            String conceptId    = extractIdcFromUri(uriType).orElse(null);

            // clé logique = nom Excel
            SpatialUnitSeeder.SpatialUnitSpecs spec =
                    new SpatialUnitSeeder.SpatialUnitSpecs(
                            name,
                            vocabularyId,
                            conceptId,
                            creatorEmail,
                            institution,
                            new HashSet<>()
                    );

            specsByName.put(name, spec);

            if (enfantsRaw != null && !enfantsRaw.isBlank()) {
                childrenStringByName.put(name, enfantsRaw);
            }
        });


        // Deuxième passe : résoudre les enfants via les noms
        for (Map.Entry<String, String> entry : childrenStringByName.entrySet()) {
            String parentName  = entry.getKey();
            String rawChildren = entry.getValue();

            Set<SpatialUnitSeeder.SpatialUnitKey> childrenKeys = Arrays.stream(rawChildren.split("&&"))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .map(childName -> {
                        SpatialUnitSeeder.SpatialUnitSpecs childSpec = specsByName.get(childName);
                        if (childSpec == null) {
                            return null;
                        }
                        // on suppose que la clé = key() ou name() de la spec
                        return new SpatialUnitSeeder.SpatialUnitKey(childSpec.name());
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            SpatialUnitSeeder.SpatialUnitSpecs parentSpec = specsByName.get(parentName);
            if (parentSpec != null && childrenKeys != null && !childrenKeys.isEmpty()) {
                // childrenKey() semble renvoyer un Set modifiable
                parentSpec.childrenKey().addAll(childrenKeys);
            }
        }

        return new ArrayList<>(specsByName.values());
    }

    public List<RecordingUnitRelSeeder.RecordingUnitDTO> parseRecordingRels(Sheet sheet) {
        if (sheet == null) {
            return List.of();
        }

        Row header = sheet.getRow(0);
        if (header == null) {
            return List.of();
        }

        Map<String, Integer> cols = indexColumns(header);

        Integer parentNum    = cols.get("parent");
        Integer childNum = cols.get("enfant");

        if (parentNum == null || childNum == null) {
            // tu peux logger un warning ici
            return List.of();
        }

        List<RecordingUnitRelSeeder.RecordingUnitDTO> specs = new ArrayList<>();

        forEachDataRow(sheet, row -> {
            String parent = getStringCell(row, parentNum);
            String child  = getStringCell(row, childNum);

            if (parent == null || parent.isBlank()) return;
            if (child  == null || child.isBlank())  return;

            specs.add(new RecordingUnitRelSeeder.RecordingUnitDTO(parent, child));
        });

        return specs;
    }

    public List<RecordingUnitStratiRelSeeder.RecordingUnitStratiRelDTO> parseStratiRels(Sheet sheet) {
        if (sheet == null) {
            return List.of();
        }

        Row header = sheet.getRow(0);
        if (header == null) {
            return List.of();
        }

        Map<String, Integer> cols = indexColumns(header);

        Integer us1Num    = cols.get("us1");
        Integer us2Num = cols.get("us2");
        Integer relationNum    = cols.get("relation");
        Integer directionNum = cols.get("direction vocabulaire");
        Integer asynchroneNum    = cols.get("asynchrone");
        Integer incertainNum    = cols.get("incertain");

        if (us1Num == null || us2Num == null) {
            // tu peux logger un warning ici
            return List.of();
        }

        List<RecordingUnitStratiRelSeeder.RecordingUnitStratiRelDTO> specs = new ArrayList<>();

        forEachDataRow(sheet, row -> {
            String us1 = getStringCell(row, us1Num);
            String us2  = getStringCell(row, us2Num);
            ConceptSeeder.ConceptKey relKey = conceptKeyFromUri(getStringCellOrNull(row,relationNum));
            String direction  = getStringCell(row, directionNum);
            boolean directionBool = direction.equals("True");
            String asynchrone  = getStringCell(row, asynchroneNum);
            boolean asynchroneBool = asynchrone.equals("True");
            String incertain  = getStringCell(row, incertainNum);
            boolean incertainBool = incertain.equals("True");

            if (us1 == null || us1.isBlank()) return;
            if (us2  == null || us2.isBlank())  return;

            specs.add(new RecordingUnitStratiRelSeeder.RecordingUnitStratiRelDTO(us1, us2,
                    relKey, directionBool, asynchroneBool, incertainBool
                    ));
        });

        return specs;
    }


    public List<ActionCodeSeeder.ActionCodeSpec> parseActionCodes(Sheet sheet) {
        if (sheet == null) {
            return List.of();
        }

        Row header = sheet.getRow(0);
        if (header == null) {
            return List.of();
        }

        Map<String, Integer> cols = indexColumns(header);

        Integer colCode    = cols.get("code");
        Integer colTypeUri = cols.get(TYPE_URI);

        if (colCode == null || colTypeUri == null) {
            // tu peux logger un warning ici
            return List.of();
        }

        List<ActionCodeSeeder.ActionCodeSpec> specs = new ArrayList<>();

        forEachDataRow(sheet, row -> {
            String code    = getStringCell(row, colCode);
            if (code == null || code.isBlank()) return;

            String typeUri = getStringCell(row, colTypeUri);

            String conceptId    = extractIdcFromUri(typeUri).orElse(null);
            String vocabularyId = extractIdtFromUri(typeUri).orElse(null);

            specs.add(new ActionCodeSeeder.ActionCodeSpec(
                    code,
                    conceptId,
                    vocabularyId
            ));
        });


        return specs;
    }

    public List<ActionUnitSeeder.ActionUnitSpecs> parseActionUnits(Sheet sheet) {
        if (sheet == null || sheet.getRow(0) == null) {
            return List.of();
        }

        Map<String, Integer> cols = indexColumns(sheet.getRow(0));

        List<ActionUnitSeeder.ActionUnitSpecs> result = new ArrayList<>();
        forEachDataRow(sheet, row ->
            parseRowToActionUnit(row, cols).ifPresent(result::add)
        );

        return result;
    }

    private Optional<ActionUnitSeeder.ActionUnitSpecs> parseRowToActionUnit(Row row, Map<String, Integer> cols) {
        String name       = getStringCellOrNull(row, cols.get("nom"));
        String identifier = getStringCellOrNull(row, cols.get(IDENTIFIANT));

        if ((name == null || name.isBlank()) && (identifier == null || identifier.isBlank())) {
            return Optional.empty();
        }

        String code          = getOptionalCell(row, cols.get("code"));
        String typeUri       = getStringCellOrNull(row, cols.get(TYPE_URI));
        String createur      = getStringCellOrNull(row, cols.get("createur"));
        String institution   = getStringCellOrNull(row, cols.get(INSTITUTION));
        String contexteRaw   = getStringCellOrNull(row, cols.get("contexte spatiale"));

        String typeConceptId    = extractIdcFromUri(typeUri).orElse(null);
        String typeVocabularyId = extractIdtFromUri(typeUri).orElse(null);

        OffsetDateTime beginDate = parseOptionalDate(row, cols.get("date debut"));
        OffsetDateTime endDate   = parseOptionalDate(row, cols.get("date fin"));

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
                spatialContextKeys
        ));
    }

    private String getOptionalCell(Row row, Integer col) {
        if (col == null) return null;
        String val = getStringCell(row, col);
        return (val == null || val.isBlank()) ? null : val;
    }

    private OffsetDateTime parseOptionalDate(Row row, Integer col) {
        return (col != null) ? parseOffsetDateTime(row.getCell(col)) : null;
    }


    public List<RecordingUnitSeeder.RecordingUnitSpecs> parseRecordingUnits(Sheet sheet) {
        if (sheet == null || sheet.getRow(0) == null) {
            return List.of();
        }

        Map<String, Integer> cols = indexColumns(sheet.getRow(0));

        List<RecordingUnitSeeder.RecordingUnitSpecs> result = new ArrayList<>();
        forEachDataRow(sheet, row -> parseRowToRecordingUnit(row, cols).ifPresent(result::add));
        return result;
    }

    private Optional<RecordingUnitSeeder.RecordingUnitSpecs> parseRowToRecordingUnit(Row row, Map<String, Integer> cols) {
        String identStr     = getStringCellOrNull(row, cols.get(IDENTIFIANT));
        String description  = getStringCellOrNull(row, cols.get("description"));

        if ((identStr == null || identStr.isBlank()) && (description == null || description.isBlank())) {
            return Optional.empty();
        }

        Integer identifier = parseIntegerSafe(identStr);

        ConceptSeeder.ConceptKey type          = conceptKeyFromUri(getStringCellOrNull(row, cols.get(TYPE_URI)));
        ConceptSeeder.ConceptKey geomorphCycle = conceptKeyFromUri(getStringCellOrNull(row, cols.get("cycle uri")));
        ConceptSeeder.ConceptKey geomorphAgent = conceptKeyFromUri(getStringCellOrNull(row, cols.get("agent uri")));
        ConceptSeeder.ConceptKey interpretation = conceptKeyFromUri(getOptionalCell(row, cols.get("interpretation uri")));

        String authorEmail     = getStringCellOrNull(row, cols.get("author email"));
        String institutionId   = getStringCellOrNull(row, cols.get(INSTITUTION));
        List<String> excavators = parseEmailList(getStringCellOrNull(row, cols.get("contributeurs email")));

        OffsetDateTime beginDate = parseOptionalDate(row, cols.get("date d'ouverture"));
        OffsetDateTime endDate   = parseOptionalDate(row, cols.get("date de fermeture"));

        OffsetDateTime creationTime = OffsetDateTime.now();
        String createdBy = "system@siamois.fr";

        SpatialUnitSeeder.SpatialUnitKey spatialKey = parseOptionalSpatialUnit(row, cols.get("unite spatiale"));
        ActionUnitSeeder.ActionUnitKey actionKey   = parseOptionalActionUnit(row, cols.get("unite d'action"));

        String matrixColor   = getStringCellOrNull(row, cols.get("couleur de la matrice"));
        String matrixTexture = getStringCellOrNull(row, cols.get("texture de la matrice"));
        String matrixComp    = getStringCellOrNull(row, cols.get("composition de la matrice"));

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
                matrixTexture
        ));
    }

// -------------------- Helpers --------------------

    private Integer parseIntegerSafe(String str) {
        if (str == null || str.isBlank()) return null;
        try {
            return Integer.valueOf(str.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private SpatialUnitSeeder.SpatialUnitKey parseOptionalSpatialUnit(Row row, Integer col) {
        String name = getStringCellOrNull(row, col);
        return (name == null || name.isBlank()) ? null : new SpatialUnitSeeder.SpatialUnitKey(name.trim());
    }

    private ActionUnitSeeder.ActionUnitKey parseOptionalActionUnit(Row row, Integer col) {
        String ident = getStringCellOrNull(row, col);
        return (ident == null || ident.isBlank()) ? null : new ActionUnitSeeder.ActionUnitKey(ident.trim());
    }


    public List<SpecimenSeeder.SpecimenSpecs> parseSpecimens(Sheet sheet) {
        if (sheet == null) {
            return List.of();
        }

        Row header = sheet.getRow(0);
        if (header == null) {
            return List.of();
        }

        Map<String, Integer> cols = indexColumns(header);

        Integer colIdentifiant      = cols.get(IDENTIFIANT);
        Integer colCategorie        = cols.get("categorie");
        Integer colMatiere          = cols.get("matiere");
        Integer colDesignation      = cols.get("designation");           // non utilisée pour l’instant
        Integer colInstitution      = cols.get(INSTITUTION);
        Integer colAuteurFicheEmail = cols.get("auteur fiche email");
        Integer colCollecteurs      = cols.get("collecteurs emails");
        Integer colUniteEnreg       = cols.get("unite d'enregistrement");

        List<SpecimenSeeder.SpecimenSpecs> result = new ArrayList<>();

        forEachDataRow(sheet, row -> {
            String identStr = getStringCellOrNull(row, colIdentifiant);
            if (identStr == null || identStr.isBlank()) {
                // ligne vide si pas d’identifiant
                return;
            }

            Integer identifier = null;
            try {
                identifier = Integer.valueOf(identStr.trim());
            } catch (NumberFormatException ignored) {
                // no op
            }

            String fullIdentifier = identStr;

            // ---- URIs → ConceptKey ----
            String matiereUri     = getStringCellOrNull(row, colMatiere);
            String categorieUri   =  getStringCellOrNull(row, colCategorie);
            String designationUri=   getStringCellOrNull(row, colDesignation);

            ConceptSeeder.ConceptKey typeKey        = conceptKeyFromUri(matiereUri);
            ConceptSeeder.ConceptKey categoryKey    = conceptKeyFromUri(categorieUri);
            ConceptSeeder.ConceptKey designationKey= conceptKeyFromUri(designationUri);

            // ---- Institution ----
            String institutionId = getStringCellOrNull(row, colInstitution);

            // ---- Auteurs ----
            String auteurFiche = getStringCellOrNull(row, colAuteurFicheEmail);


            List<String> authors =
                    (auteurFiche == null || auteurFiche.isBlank())
                            ? List.of()
                            : List.of(auteurFiche.trim());

            // ---- Collecteurs ----
            String collectorsRaw = getStringCellOrNull(row, colCollecteurs);
            List<String> collectors = parseEmailList(collectorsRaw);

            // ---- Recording unit ----
            String ruStr = getStringCellOrNull(row, colUniteEnreg);
            RecordingUnitSeeder.RecordingUnitKey recordingUnitKey =
                    (ruStr == null || ruStr.isBlank())
                            ? null
                            : new RecordingUnitSeeder.RecordingUnitKey(ruStr);

            // ---- Champs système ----
            String authorEmail = "system@siamois.fr";
            OffsetDateTime creationTime = OffsetDateTime.now();

            result.add(new SpecimenSeeder.SpecimenSpecs(
                    fullIdentifier,
                    identifier,
                    typeKey,
                    categoryKey,
                    designationKey,
                    authorEmail,
                    institutionId,
                    authors,
                    collectors,
                    creationTime,
                    recordingUnitKey
            ));
        });


        return result;
    }





    public Optional<String> extractIdtFromUri(String uri) {
        if (uri == null) return Optional.empty();
        int idx = uri.indexOf("idt=");
        if (idx < 0) return Optional.empty();
        String sub = uri.substring(idx + 4);
        int amp = sub.indexOf('&');
        if (amp >= 0) sub = sub.substring(0, amp);
        return Optional.of(URLDecoder.decode(sub, StandardCharsets.UTF_8));
    }

    public Optional<String> extractIdcFromUri(String uri) {
        if (uri == null) return Optional.empty();
        int idx = uri.indexOf("idc=");
        if (idx < 0) return Optional.empty();
        String sub = uri.substring(idx + 4);
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
            return null; // pas une URI valide
        }

        return new ConceptSeeder.ConceptKey(vocabId, conceptId);
    }


    // -------------------------------------------------------------------------
    // Helpers Excel
    // -------------------------------------------------------------------------
    public Map<String, Integer> indexColumns(Row header) {
        Map<String, Integer> map = new HashMap<>();
        if (header == null) return map;

        for (int c = 0; c < header.getLastCellNum(); c++) {
            Cell cell = header.getCell(c);
            if (cell != null) {
                String raw = getStringCell(cell);
                if (raw != null) {
                    String norm = normalize(raw);
                    map.put(norm, c);
                }
            }
        }

        return map;
    }

    public String getStringCell(Row row, int colIndex) {
        Cell cell = row.getCell(colIndex);
        return getStringCell(cell);
    }

    public String getStringCellOrNull(Row row, Integer colIndex) {

        return colIndex != null ? getStringCell(row, colIndex) : null;

    }

    public String getStringCell(Cell cell) {
        if (cell == null) return null;
        String val = cell.getStringCellValue();
        return val != null ? val.trim() : null;
    }

    public List<String> parseEmailList(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        String[] parts = raw.split("[;,]");
        return Arrays.stream(parts)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    // Normalisation pour matcher les noms de colonnes malgré les accents / espaces
    public String normalize(String s) {
        if (s == null) return "";
        String tmp = s.trim().toLowerCase(Locale.ROOT);
        tmp = Normalizer.normalize(tmp, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", ""); // enlève les accents
        tmp = tmp.replaceAll("\\s+", " "); // normalise les espaces
        return tmp;
    }

    public OffsetDateTime parseOffsetDateTime(Cell cell) {
        if (cell == null) return null;

        try {
            // 1) Cas "vraie" date Excel (numérique + format date)
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                Date d = cell.getDateCellValue();
                return d.toInstant().atOffset(ZoneOffset.UTC);
            }

            // 2) Cas texte "yyyy-MM-dd"
            String raw = cell.getStringCellValue();
            if (raw == null || raw.isBlank()) return null;

            raw = raw.trim();

            // format aaaa-mm-jj = yyyy-MM-dd
            LocalDate ld = LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE);
            return ld.atStartOfDay().atOffset(ZoneOffset.UTC); // ou ZoneOffset.ofHours(1/2)

        } catch (Exception e) {
            return null;
        }
    }

    public Set<SpatialUnitSeeder.SpatialUnitKey> parseSpatialNames(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }

        return Arrays.stream(raw.split("&&"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(SpatialUnitSeeder.SpatialUnitKey::new) // 🔥 nom tel quel
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void forEachDataRow(Sheet sheet, Consumer<Row> consumer) {
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row != null) {
                consumer.accept(row);
            }
        }
    }



}
