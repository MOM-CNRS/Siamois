package fr.siamois.infrastructure.dataimport;

import fr.siamois.infrastructure.database.initializer.DefaultFormsDatasetInitializer;
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
import java.util.stream.Collectors;


@Service
public class OOXMLImportService {

    public static final String IDENTIFIANT = "identifiant";
    public static final String PERSON = "person";

    public OOXMLImportService() {

    }

    private Map<String, String> readSheetMetadata(Workbook workbook) {
        Sheet metaSheet = workbook.getSheet("sheet_metadata");
        Map<String, String> map = new HashMap<>();

        if (metaSheet == null) {
            // fallback simple : noms des feuilles connus
            for (Sheet s : workbook) {
                String nameNorm = normalize(s.getSheetName());
                if (nameNorm.contains("institution")) {
                    map.put("institution", s.getSheetName());
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

        Row header = metaSheet.getRow(0);
        if (header == null) return map;

        Map<String, Integer> colIndex = indexColumns(header);

        Integer sheetIdCol   = colIndex.get("sheet_id");
        Integer sheetNameCol = colIndex.get("sheet_name");
        if (sheetIdCol == null || sheetNameCol == null) return map;

        for (int r = 1; r <= metaSheet.getLastRowNum(); r++) {
            Row row = metaSheet.getRow(r);
            if (row == null) continue;
            String id   = getStringCell(row, sheetIdCol);
            String name = getStringCell(row, sheetNameCol);
            if (id != null && name != null) {
                map.put(id.trim(), name.trim());
            }
        }
        return map;
    }

    public ImportSpecs importFromExcel(InputStream is) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(is)) {

            Map<String, String> sheetIdToName = readSheetMetadata(workbook);

            Sheet institutionSheet = workbook.getSheet(sheetIdToName.getOrDefault("institution", "Institution"));
            Sheet personSheet      = workbook.getSheet(sheetIdToName.getOrDefault(PERSON, "Personne"));
            Sheet spatialSheet     = workbook.getSheet(sheetIdToName.getOrDefault("spatial_unit", "Unité spatiale"));
            Sheet codeSheet        = workbook.getSheet(sheetIdToName.getOrDefault("code", "Code"));
            Sheet actionUnitSheet  = workbook.getSheet(sheetIdToName.getOrDefault("action_unit", "Unite action"));
            Sheet recordingUnitSheet  = workbook.getSheet(sheetIdToName.getOrDefault("recording_unit", "Unite action"));
            Sheet specimenSheet  = workbook.getSheet(sheetIdToName.getOrDefault("specimen", "Prelev."));

            List<InstitutionSeeder.InstitutionSpec> institutions = parseInstitutions(institutionSheet);
            List<PersonSeeder.PersonSpec> persons = parsePersons(personSheet);
            List<SpatialUnitSeeder.SpatialUnitSpecs> spatialUnits = parseSpatialUnits(spatialSheet);
            List<ActionCodeSeeder.ActionCodeSpec> actionCodes = parseActionCodes(codeSheet);
            List<ActionUnitSeeder.ActionUnitSpecs> actionUnits = parseActionUnits(actionUnitSheet);
            List<RecordingUnitSeeder.RecordingUnitSpecs> recordingUnits = parseRecordingUnits(recordingUnitSheet);
            List<SpecimenSeeder.SpecimenSpecs> specimenSpecs = parseSpecimens(specimenSheet);

            return new ImportSpecs(institutions, persons, spatialUnits, actionCodes, actionUnits, recordingUnits, specimenSpecs);
        }
    }

    private List<InstitutionSeeder.InstitutionSpec> parseInstitutions(Sheet sheet) {
        Row header = sheet.getRow(0);
        Map<String, Integer> cols = indexColumns(header);

        Integer colName        = cols.get("nom");
        Integer colDescription = cols.get("description");
        Integer colIdentifier  = cols.get(IDENTIFIANT);
        Integer colAdmins      = cols.get("email admins");
        Integer colThesaurus   = cols.get("thesaurus");

        List<InstitutionSeeder.InstitutionSpec> result = new ArrayList<>();

        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String name        = colName != null ? getStringCell(row, colName) : null;
            if (name == null || name.isBlank()) continue; // ligne vide

            String description = colDescription != null ? getStringCell(row, colDescription) : null;
            String identifier  = colIdentifier != null ? getStringCell(row, colIdentifier) : null;
            String adminsRaw   = colAdmins != null ? getStringCell(row, colAdmins) : null;
            String thesaurus   = colThesaurus != null ? getStringCell(row, colThesaurus) : null;

            List<String> adminEmails = parseEmailList(adminsRaw);

            String vocabularyId = extractIdtFromUri(thesaurus).orElse(null);
            String thesaurusInstance = extractThesaurusDomain(thesaurus);

            InstitutionSeeder.InstitutionSpec spec =
                    new InstitutionSeeder.InstitutionSpec(
                            name,
                            description,
                            identifier,
                            adminEmails,
                            thesaurusInstance,
                            vocabularyId
                    );
            result.add(spec);
        }
        return result;
    }

    private String extractThesaurusDomain(String thesaurus) {
        if (thesaurus == null) return null;
        int idx = thesaurus.indexOf("?");
        if (idx < 0) {
            return thesaurus; // pas de paramètres → on garde tout
        }
        return thesaurus.substring(0, idx-1);
    }

    private List<PersonSeeder.PersonSpec> parsePersons(Sheet sheet) {
        Row header = sheet.getRow(0);
        Map<String, Integer> cols = indexColumns(header);

        Integer colEmail       = cols.get("email");
        Integer colNom         = cols.get("nom");
        Integer colPrenom      = cols.get("prenom");
        Integer colIdentifiant = cols.get(IDENTIFIANT);

        List<PersonSeeder.PersonSpec> result = new ArrayList<>();

        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String email = colEmail != null ? getStringCell(row, colEmail) : null;
            if (email == null || email.isBlank()) continue;

            String nom     = colNom != null ? getStringCell(row, colNom) : null;
            String prenom  = colPrenom != null ? getStringCell(row, colPrenom) : null;
            String ident   = colIdentifiant != null ? getStringCell(row, colIdentifiant) : null;

            // IMPORTANT : on reproduit ton seeder existant :
            // new PersonSpec("anais...", "Anaïs", "Pinhède", "anais.pinhede")
            // => firstName = "Nom" (colonne Excel), lastName = "Prénom"
            PersonSeeder.PersonSpec spec =
                    new PersonSeeder.PersonSpec(email, nom, prenom, ident);

            result.add(spec);
        }

        return result;
    }

    private List<SpatialUnitSeeder.SpatialUnitSpecs> parseSpatialUnits(Sheet sheet) {
        Row header = sheet.getRow(0);
        Map<String, Integer> cols = indexColumns(header);

        Integer colNom         = cols.get("nom");
        Integer colUriType     = cols.get("uri type");
        Integer colCreateur    = cols.get("createur");
        Integer colInstitution = cols.get("institution");
        Integer colEnfants     = cols.get("enfants");

        // Première passe : créer les specs SANS enfants, et indexer par nom
        Map<String, SpatialUnitSeeder.SpatialUnitSpecs> specsByName = new LinkedHashMap<>();
        Map<String, String> childrenStringByName = new HashMap<>();

        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String name = colNom != null ? getStringCell(row, colNom) : null;
            if (name == null || name.isBlank()) continue;

            String uriType      = colUriType != null ? getStringCell(row, colUriType) : null;
            String creatorEmail = colCreateur != null ? getStringCell(row, colCreateur) : null;
            String institution  = colInstitution != null ? getStringCell(row, colInstitution) : null;
            String enfantsRaw   = colEnfants != null ? getStringCell(row, colEnfants) : null;

            // 🔹 idt = vocabularyId, idc = conceptId
            String vocabularyId = extractIdtFromUri(uriType).orElse(null);
            String conceptId    = extractIdcFromUri(uriType).orElse(null);

            // Ici on utilise le nom comme clé logique de l'unité spatiale
            String spatialKey = name;

            SpatialUnitSeeder.SpatialUnitSpecs spec =
                    new SpatialUnitSeeder.SpatialUnitSpecs(
                            spatialKey,     // key technique (ex : "Parcelle DA 154")
                            vocabularyId,   // idt=th230 (vocabulaire)
                            conceptId,      // idc=4287979 (concept)
                            creatorEmail,   // créateur
                            institution,    // institution
                            new HashSet<>()            // enfants remplis en 2e passe
                    );

            specsByName.put(name, spec);

            if (enfantsRaw != null && !enfantsRaw.isBlank()) {
                childrenStringByName.put(name, enfantsRaw);
            }
        }

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
                            // ici tu peux log.warn("Unité spatiale enfant introuvable : {}", childName);
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


    private List<ActionCodeSeeder.ActionCodeSpec> parseActionCodes(Sheet sheet) {
        if (sheet == null) {
            return List.of();
        }

        Row header = sheet.getRow(0);
        if (header == null) {
            return List.of();
        }

        Map<String, Integer> cols = indexColumns(header);

        Integer colCode    = cols.get("code");
        Integer colTypeUri = cols.get("type uri");

        if (colCode == null || colTypeUri == null) {
            // tu peux logger un warning ici
            return List.of();
        }

        List<ActionCodeSeeder.ActionCodeSpec> specs = new ArrayList<>();

        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String code = getStringCell(row, colCode);
            String typeUri = getStringCell(row, colTypeUri);

            if (code == null || code.isBlank()) continue;

            String conceptId    = extractIdcFromUri(typeUri).orElse(null);
            String vocabularyId = extractIdtFromUri(typeUri).orElse(null);

            specs.add(new ActionCodeSeeder.ActionCodeSpec(code, conceptId, vocabularyId));
        }

        return specs;
    }

    private List<ActionUnitSeeder.ActionUnitSpecs> parseActionUnits(Sheet sheet) {
        if (sheet == null) {
            return List.of();
        }

        Row header = sheet.getRow(0);
        if (header == null) {
            return List.of();
        }

        Map<String, Integer> cols = indexColumns(header);

        Integer colNom           = cols.get("nom");
        Integer colIdentifiant   = cols.get(IDENTIFIANT);
        Integer colCode          = cols.get("code");
        Integer colTypeUri       = cols.get("type uri");
        Integer colCreateur      = cols.get("createur");
        Integer colInstitution   = cols.get("institution");
        Integer colDateDebut     = cols.get("date debut");
        Integer colDateFin       = cols.get("date fin");
        Integer colContexteSpat  = cols.get("contexte spatiale");

        List<ActionUnitSeeder.ActionUnitSpecs> result = new ArrayList<>();

        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String name        = colNom != null ? getStringCell(row, colNom) : null;
            String identifier  = colIdentifiant != null ? getStringCell(row, colIdentifiant) : null;
            String code        = colCode != null ? getStringCell(row, colCode) : null;
            String typeUri     = colTypeUri != null ? getStringCell(row, colTypeUri) : null;
            String createur    = colCreateur != null ? getStringCell(row, colCreateur) : null;
            String institution = colInstitution != null ? getStringCell(row, colInstitution) : null;
            String contexteRaw = colContexteSpat != null ? getStringCell(row, colContexteSpat) : null;

            if ((name == null || name.isBlank()) &&
                    (identifier == null || identifier.isBlank())) {
                continue;
            }

            String typeConceptId    = extractIdcFromUri(typeUri).orElse(null);
            String typeVocabularyId = extractIdtFromUri(typeUri).orElse(null);

            OffsetDateTime beginDate = colDateDebut != null ? parseOffsetDateTime(row.getCell(colDateDebut)) : null;
            OffsetDateTime endDate   = colDateFin   != null ? parseOffsetDateTime(row.getCell(colDateFin))   : null;

            // --------------- IMPORTANT ---------------
            // On NE résout PAS les unités spatiales :
            // On stocke simplement leurs noms tels qu’écrits dans Excel.
            // -----------------------------------------
            Set<SpatialUnitSeeder.SpatialUnitKey> spatialContextKeys =
                    parseSpatialNames(contexteRaw);

            String fullIdentifier = identifier != null ? identifier : name;

            ActionUnitSeeder.ActionUnitSpecs spec = new ActionUnitSeeder.ActionUnitSpecs(
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
            );

            result.add(spec);
        }

        return result;
    }

    private List<RecordingUnitSeeder.RecordingUnitSpecs> parseRecordingUnits(Sheet sheet) {
        if (sheet == null) {
            return List.of();
        }

        Row header = sheet.getRow(0);
        if (header == null) {
            return List.of();
        }

        Map<String, Integer> cols = indexColumns(header);

        Integer colIdentifiant       = cols.get(IDENTIFIANT);
        Integer colDescription       = cols.get("description");
        Integer colTypeUri           = cols.get("type uri");
        Integer colCycleUri          = cols.get("cycle uri");
        Integer colMatrixColor          = cols.get("couleur de la matrice");
        Integer colMatrixTexture          = cols.get("texture de la matrice");
        Integer colMatrixComp         = cols.get("composition de la matrice");
        Integer colAgentUri          = cols.get("agent uri");
        Integer colInterpretationUri = cols.get("interpretation uri");
        Integer colAuthorEmail       = cols.get("author email");
        Integer colInstitution       = cols.get("institution");
        Integer colContribEmails     = cols.get("contributeurs email");
        Integer colDateOuverture     = cols.get("date d'ouverture");
        Integer colDateFermeture     = cols.get("date de fermeture");
        Integer colUniteSpatiale     = cols.get("unite spatiale");
        Integer colUniteAction       = cols.get("unite d'action");

        List<RecordingUnitSeeder.RecordingUnitSpecs> result = new ArrayList<>();

        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String identStr = colIdentifiant != null ? getStringCell(row, colIdentifiant) : null;
            String matrixColor = colMatrixColor != null ? getStringCell(row, colMatrixColor) : null;
            String matrixTexture = colMatrixTexture != null ? getStringCell(row, colMatrixTexture) : null;
            String matrixComp = colMatrixComp != null ? getStringCell(row, colMatrixComp) : null;
            String description = colDescription != null ? getStringCell(row, colDescription) : null;

            if ((identStr == null || identStr.isBlank()) &&
                    (description == null || description.isBlank())) {
                continue;
            }

            Integer identifier = null;
            if (identStr != null && !identStr.isBlank()) {
                try {
                    identifier = Integer.valueOf(identStr.trim());
                } catch (NumberFormatException ignored) {}
            }

            // ---- CONCEPT KEYS ----
            ConceptSeeder.ConceptKey type =
                    conceptKeyFromUri(colTypeUri != null ? getStringCell(row, colTypeUri) : null);

            ConceptSeeder.ConceptKey geomorphCycle =
                    conceptKeyFromUri(colCycleUri != null ? getStringCell(row, colCycleUri) : null);

            ConceptSeeder.ConceptKey geomorphAgent =
                    conceptKeyFromUri(colAgentUri != null ? getStringCell(row, colAgentUri) : null);

            ConceptSeeder.ConceptKey interpretation =
                    conceptKeyFromUri(colInterpretationUri != null ? getStringCell(row, colInterpretationUri) : null);


            // ---- EMAILS ----
            String authorEmail = colAuthorEmail != null ? getStringCell(row, colAuthorEmail) : null;
            String institutionId = colInstitution != null ? getStringCell(row, colInstitution) : null;

            List<String> excavators =
                    parseEmailList(colContribEmails != null ? getStringCell(row, colContribEmails) : null);


            // ---- DATES ----
            OffsetDateTime beginDate = colDateOuverture != null
                    ? parseOffsetDateTime(row.getCell(colDateOuverture))
                    : null;

            OffsetDateTime endDate = colDateFermeture != null
                    ? parseOffsetDateTime(row.getCell(colDateFermeture))
                    : null;

            // ---- CREATION TIME (NOuVELLE RÈGLE) ----
            OffsetDateTime creationTime = OffsetDateTime.now();  // système

            // ---- createdBy (NOUVELLE RÈGLE) ----
            String createdBy = "system@siamois.fr";


            // ---- SPATIAL UNIT ----
            String usName = colUniteSpatiale != null ? getStringCell(row, colUniteSpatiale) : null;
            SpatialUnitSeeder.SpatialUnitKey spatialKey =
                    (usName == null || usName.isBlank())
                            ? null
                            : new SpatialUnitSeeder.SpatialUnitKey(usName.trim());

            // ---- ACTION UNIT ----
            String uaIdent = colUniteAction != null ? getStringCell(row, colUniteAction) : null;
            ActionUnitSeeder.ActionUnitKey actionKey =
                    (uaIdent == null || uaIdent.isBlank())
                            ? null
                            : new ActionUnitSeeder.ActionUnitKey(uaIdent.trim());


            String fullIdentifier = identStr;

            RecordingUnitSeeder.RecordingUnitSpecs spec = new RecordingUnitSeeder.RecordingUnitSpecs(
                    fullIdentifier,
                    identifier,
                    type,
                    geomorphCycle,
                    geomorphAgent,
                    interpretation,
                    authorEmail,
                    institutionId,
                    authorEmail,     // author = authorEmail
                    createdBy,       // <- FIX : createdBy = systeme@siamois.fr
                    excavators,
                    creationTime,    // <- FIX : maintenant
                    beginDate,
                    endDate,
                    spatialKey,
                    actionKey,
                    description,
                    matrixColor,
                    matrixComp,
                    matrixTexture
            );

            result.add(spec);
        }

        return result;
    }

    private List<SpecimenSeeder.SpecimenSpecs> parseSpecimens(Sheet sheet) {
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
        Integer colInstitution      = cols.get("institution");
        Integer colAuteurFicheEmail = cols.get("auteur fiche email");
        Integer colCollecteurs      = cols.get("collecteurs emails");
        Integer colUniteEnreg       = cols.get("unite d'enregistrement");

        List<SpecimenSeeder.SpecimenSpecs> result = new ArrayList<>();

        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String identStr = colIdentifiant != null ? getStringCell(row, colIdentifiant) : null;
            if (identStr == null || identStr.isBlank()) {
                // On considère la ligne comme vide si pas d’identifiant
                continue;
            }

            // Identifiant numérique
            Integer identifier = null;
            try {
                identifier = Integer.valueOf(identStr.trim());
            } catch (NumberFormatException ignored) {
                // ignored
            }

            String fullIdentifier = identStr;

            // URIs -> ConceptKey
            String matiereUri   = colMatiere   != null ? getStringCell(row, colMatiere)   : null;
            String categorieUri = colCategorie != null ? getStringCell(row, colCategorie) : null;
            String designationUri = colDesignation != null ? getStringCell(row, colDesignation) : null;

            ConceptSeeder.ConceptKey typeKey     = conceptKeyFromUri(matiereUri);   // Matiere -> type
            ConceptSeeder.ConceptKey categoryKey = conceptKeyFromUri(categorieUri); // Catégorie -> category
            ConceptSeeder.ConceptKey designationKey = conceptKeyFromUri(designationUri);

            // Institution
            String institutionId = colInstitution != null ? getStringCell(row, colInstitution) : null;

            // Auteur fiche email -> liste d'auteurs
            String auteurFiche = colAuteurFicheEmail != null ? getStringCell(row, colAuteurFicheEmail) : null;
            List<String> authors = (auteurFiche == null || auteurFiche.isBlank())
                    ? List.of()
                    : List.of(auteurFiche.trim());

            // Collecteurs emails -> liste
            String collectorsRaw = colCollecteurs != null ? getStringCell(row, colCollecteurs) : null;
            List<String> collectors = parseEmailList(collectorsRaw);

            // Recording unit key
            String ruStr = colUniteEnreg != null ? getStringCell(row, colUniteEnreg) : null;
            RecordingUnitSeeder.RecordingUnitKey recordingUnitKey =
                    (ruStr == null || ruStr.isBlank())
                            ? null
                            : new RecordingUnitSeeder.RecordingUnitKey(ruStr);

            // Champs système
            String authorEmail = "system@siamois.fr";
            OffsetDateTime creationTime = OffsetDateTime.now();

            SpecimenSeeder.SpecimenSpecs spec = new SpecimenSeeder.SpecimenSpecs(
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
            );

            result.add(spec);
        }

        return result;
    }





    private Optional<String> extractIdtFromUri(String uri) {
        if (uri == null) return Optional.empty();
        int idx = uri.indexOf("idt=");
        if (idx < 0) return Optional.empty();
        String sub = uri.substring(idx + 4);
        int amp = sub.indexOf('&');
        if (amp >= 0) sub = sub.substring(0, amp);
        return Optional.of(URLDecoder.decode(sub, StandardCharsets.UTF_8));
    }

    private Optional<String> extractIdcFromUri(String uri) {
        if (uri == null) return Optional.empty();
        int idx = uri.indexOf("idc=");
        if (idx < 0) return Optional.empty();
        String sub = uri.substring(idx + 4);
        int amp = sub.indexOf('&');
        if (amp >= 0) sub = sub.substring(0, amp);
        return Optional.of(URLDecoder.decode(sub, StandardCharsets.UTF_8));
    }

    private ConceptSeeder.ConceptKey conceptKeyFromUri(String uri) {
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
    private Map<String, Integer> indexColumns(Row header) {
        Map<String, Integer> map = new HashMap<>();
        if (header == null) return map;

        for (int c = 0; c < header.getLastCellNum(); c++) {
            Cell cell = header.getCell(c);
            if (cell == null) continue;
            String raw = getStringCell(cell);
            if (raw == null) continue;
            String norm = normalize(raw);
            map.put(norm, c);
        }
        return map;
    }

    private String getStringCell(Row row, int colIndex) {
        Cell cell = row.getCell(colIndex);
        return getStringCell(cell);
    }

    private String getStringCell(Cell cell) {
        if (cell == null) return null;
        cell.setCellType(CellType.STRING);
        String val = cell.getStringCellValue();
        return val != null ? val.trim() : null;
    }

    private List<String> parseEmailList(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        String[] parts = raw.split("[;,]");
        return Arrays.stream(parts)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }

    // Normalisation pour matcher les noms de colonnes malgré les accents / espaces
    private String normalize(String s) {
        if (s == null) return "";
        String tmp = s.trim().toLowerCase(Locale.ROOT);
        tmp = Normalizer.normalize(tmp, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", ""); // enlève les accents
        tmp = tmp.replaceAll("\\s+", " "); // normalise les espaces
        return tmp;
    }

    private OffsetDateTime parseOffsetDateTime(Cell cell) {
        if (cell == null) return null;

        try {
            // 1) Cas "vraie" date Excel (numérique + format date)
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                Date d = cell.getDateCellValue();
                // adapte le fuseau si tu veux Europe/Paris
                return d.toInstant().atOffset(ZoneOffset.UTC);
            }

            // 2) Cas texte "yyyy-MM-dd"
            cell.setCellType(CellType.STRING);
            String raw = cell.getStringCellValue();
            if (raw == null || raw.isBlank()) return null;

            raw = raw.trim();

            // ton format aaaa-mm-jj = yyyy-MM-dd
            LocalDate ld = LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE);
            return ld.atStartOfDay().atOffset(ZoneOffset.UTC); // ou ZoneOffset.ofHours(1/2) si tu veux

        } catch (Exception e) {
            // log.warn("Impossible de parser la date '{}'", cell, e);
            return null;
        }
    }

    private Set<SpatialUnitSeeder.SpatialUnitKey> parseSpatialNames(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }

        return Arrays.stream(raw.split("&&"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(SpatialUnitSeeder.SpatialUnitKey::new) // 🔥 nom tel quel
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

}
