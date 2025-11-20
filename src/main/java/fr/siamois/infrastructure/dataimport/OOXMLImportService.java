package fr.siamois.infrastructure.dataimport;

import fr.siamois.infrastructure.database.initializer.seeder.*;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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

    private Map<String, String> readSheetMetadata(Workbook workbook) {
        Sheet metaSheet = workbook.getSheet("sheet_metadata");
        Map<String, String> map = new HashMap<>();

        if (metaSheet == null) {
            // fallback simple : noms des feuilles connus
            for (Sheet s : workbook) {
                String nameNorm = normalize(s.getSheetName());
                if (nameNorm.contains("institution")) {
                    map.put("institution", s.getSheetName());
                } else if (nameNorm.contains("person")) {
                    map.put("person", s.getSheetName());
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
            Sheet personSheet      = workbook.getSheet(sheetIdToName.getOrDefault("person", "Personne"));
            Sheet spatialSheet     = workbook.getSheet(sheetIdToName.getOrDefault("spatial_unit", "Unité spatiale"));
            Sheet codeSheet        = workbook.getSheet(sheetIdToName.getOrDefault("code", "Code"));
            Sheet actionUnitSheet  = workbook.getSheet(sheetIdToName.getOrDefault("action_unit", "Unite action"));

            List<InstitutionSeeder.InstitutionSpec> institutions = parseInstitutions(institutionSheet);
            List<PersonSeeder.PersonSpec> persons = parsePersons(personSheet);
            List<SpatialUnitSeeder.SpatialUnitSpecs> spatialUnits = parseSpatialUnits(spatialSheet);
            List<ActionCodeSeeder.ActionCodeSpec> actionCodes = parseActionCodes(codeSheet);
            List<ActionUnitSeeder.ActionUnitSpecs> actionUnits = parseActionUnits(actionUnitSheet);

            return new ImportSpecs(institutions, persons, spatialUnits, actionCodes, actionUnits);
        }
    }

    private List<InstitutionSeeder.InstitutionSpec> parseInstitutions(Sheet sheet) {
        Row header = sheet.getRow(0);
        Map<String, Integer> cols = indexColumns(header);

        Integer colName        = cols.get("nom");
        Integer colDescription = cols.get("description");
        Integer colIdentifier  = cols.get("identifiant");
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
        Integer colNom         = cols.get("nom");       // → firstName dans ton seeder
        Integer colPrenom      = cols.get("prénom");    // → lastName dans ton seeder (oui inversé par rapport au FR)
        Integer colIdentifiant = cols.get("identifiant");

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
        Integer colIdentifiant   = cols.get("identifiant");
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
