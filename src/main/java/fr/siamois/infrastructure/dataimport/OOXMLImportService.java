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
    public static final String SIAMOIS_SYSTEM = "siamois system";

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

    public enum ImportScope {
        ALL,
        PROJECT
    }

    public ImportSpecs importFromExcel(InputStream is, ImportScope scope, ActionUnitDTO actionUnitDTO) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(is)) {

            Map<String, String> sheetIdToName = readSheetMetadata(workbook);

            List<InstitutionSeeder.InstitutionSpec> institutions = new ArrayList<>();
            List<PersonSeeder.PersonSpec> persons =  new ArrayList<>();
            List<SpatialUnitSeeder.SpatialUnitSpecs> spatialUnits =  new ArrayList<>();
            List<ActionCodeSeeder.ActionCodeSpec> actionCodes =  new ArrayList<>();
            List<ActionUnitSeeder.ActionUnitSpecs> actionUnits =  new ArrayList<>();
            List<RecordingUnitSeeder.RecordingUnitSpecs> recordingUnits =  new ArrayList<>();
            List<SpecimenSeeder.SpecimenSpecs> specimenSpecs =  new ArrayList<>();
            List<PhaseSeeder.PhaseSpecs> phaseSpecs = new ArrayList<>();
            List<RecordingUnitRelSeeder.RecordingUnitRelDTO> recordingUnitDTOS =  new ArrayList<>();
            List<RecordingUnitStratiRelSeeder.RecordingUnitStratiRelDTO> stratiDTOS =  new ArrayList<>();
            Sheet spatialSheet = workbook.getSheet(sheetIdToName.getOrDefault("spatial_unit", "Unité spatiale"));

            if(scope == ImportScope.ALL) {
                Sheet institutionSheet = workbook.getSheet(sheetIdToName.getOrDefault(INSTITUTION, "Institution"));
                Sheet personSheet      = workbook.getSheet(sheetIdToName.getOrDefault(PERSON, "Personne"));
                Sheet codeSheet        = workbook.getSheet(sheetIdToName.getOrDefault("code", "Code"));
                Sheet actionUnitSheet  = workbook.getSheet(sheetIdToName.getOrDefault("action_unit", "Unite action"));
                institutions = parseInstitutions(institutionSheet);
                persons = parsePersons(personSheet);

                actionCodes = parseActionCodes(codeSheet);
                actionUnits = parseActionUnits(actionUnitSheet);
            }

            Sheet recordingUnitSheet  = workbook.getSheet(sheetIdToName.getOrDefault("recording_unit", "UE"));
            Sheet specimenSheet  = workbook.getSheet(sheetIdToName.getOrDefault("specimen", "Prelev"));
            Sheet recordingRelSheet  = workbook.getSheet(sheetIdToName.getOrDefault("recordingRel", "UE_rel"));
            Sheet stratiSheet  = workbook.getSheet(sheetIdToName.getOrDefault("stratiRel", "Strati_Rel"));

            Sheet phaseSheet = workbook.getSheet(sheetIdToName.getOrDefault("phase", "Phase"));

            spatialUnits = parseSpatialUnits(spatialSheet, scope, actionUnitDTO);
            recordingUnits = parseRecordingUnits(recordingUnitSheet, scope, actionUnitDTO);
            specimenSpecs = parseSpecimens(specimenSheet, scope, actionUnitDTO);
            phaseSpecs = parsePhases(phaseSheet, scope, actionUnitDTO);
            recordingUnitDTOS = parseRecordingRels(recordingRelSheet);
            stratiDTOS = parseStratiRels(stratiSheet);

            return new ImportSpecs(institutions, persons, spatialUnits, actionCodes, actionUnits,
                    recordingUnits, specimenSpecs, phaseSpecs, recordingUnitDTOS, stratiDTOS);
        }
    }

    public List<InstitutionSeeder.InstitutionSpec> parseInstitutions(Sheet sheet) {
        try {
            Row header = sheet.getRow(0);
            Map<String, Integer> cols = indexColumns(header);
            List<InstitutionSeeder.InstitutionSpec> result = new ArrayList<>();

            forEachDataRow(sheet, row -> {
                String name = getStringCellOrNull(row, cols, "nom");
                if (name == null || name.isBlank()) return;

                String description = getStringCellOrNull(row, cols, "description");
                String identifier  = getStringCellOrNull(row, cols, IDENTIFIANT);
                String adminsRaw   = getStringCellOrNull(row, cols, "email admins");
                String thesaurus   = getStringCellOrNull(row, cols, "thesaurus");
                List<String> adminEmails = parsePersonList(adminsRaw);
                String vocabularyId      = extractIdtFromUri(thesaurus).orElse(null);
                String thesaurusInstance = extractThesaurusDomain(thesaurus);

                result.add(new InstitutionSeeder.InstitutionSpec(
                        name, description, identifier, adminEmails, thesaurusInstance, vocabularyId));
            });
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("[Feuille '" + sheet.getSheetName() + "'] : " + e.getMessage(), e);
        }
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
        try {
            Row header = sheet.getRow(0);
            Map<String, Integer> cols = indexColumns(header);
            List<PersonSeeder.PersonSpec> result = new ArrayList<>();

            forEachDataRow(sheet, row -> {
                String email = getStringCellOrNull(row, cols, "email");
                if (email == null || email.isBlank()) return;

                result.add(new PersonSeeder.PersonSpec(
                        email,
                        getStringCellOrNull(row, cols, "nom"),
                        getStringCellOrNull(row, cols, "prenom"),
                        getStringCellOrNull(row, cols, IDENTIFIANT)
                ));
            });

            return result;
        } catch (Exception e) {
            throw new IllegalStateException("[Feuille '" + sheet.getSheetName() + "'] : " + e.getMessage(), e);
        }
    }

    public List<SpatialUnitSeeder.SpatialUnitSpecs> parseSpatialUnits(Sheet sheet,
                                                                      ImportScope scope,
                                                                      ActionUnitDTO actionUnit) {
        try {
        Row header = sheet.getRow(0);
        Map<String, Integer> cols = indexColumns(header);

        // Première passe : créer les specs SANS enfants, et indexer par nom
        Map<String, SpatialUnitSeeder.SpatialUnitSpecs> specsByName = new LinkedHashMap<>();
        Map<String, String> childrenStringByName = new HashMap<>();

        forEachDataRow(sheet, row -> {
            String name = getStringCellOrNull(row, cols, "nom");
            if (name == null || name.isBlank()) return;

            String uriType      = getStringCellOrNull(row, cols, "uri type");
            String creatorEmail = SIAMOIS_SYSTEM;
            String institution = actionUnit != null ? actionUnit.getCreatedByInstitution().getIdentifier() : getStringCellOrNull(row, cols, INSTITUTION);
            String enfantsRaw   = getStringCellOrNull(row, cols, "enfants");

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
        } catch (Exception e) {
            throw new IllegalStateException("[Feuille '" + sheet.getSheetName() + "'] : " + e.getMessage(), e);
        }
    }

    public List<RecordingUnitRelSeeder.RecordingUnitRelDTO> parseRecordingRels(Sheet sheet) {
        if (sheet == null) return List.of();
        try {
            Row header = sheet.getRow(0);
            if (header == null) return List.of();

            Map<String, Integer> cols = indexColumns(header);
            Integer parentNum = cols.get("parent");
            Integer childNum  = cols.get("enfant");
            if (parentNum == null || childNum == null) return List.of();

            List<RecordingUnitRelSeeder.RecordingUnitRelDTO> specs = new ArrayList<>();
            forEachDataRow(sheet, row -> {
                String parent = getStringCell(row, parentNum);
                String child  = getStringCell(row, childNum);
                if (parent == null || parent.isBlank()) return;
                if (child  == null || child.isBlank())  return;
                specs.add(new RecordingUnitRelSeeder.RecordingUnitRelDTO(parent, child));
            });
            return specs;
        } catch (Exception e) {
            throw new IllegalStateException("[Feuille '" + sheet.getSheetName() + "'] : " + e.getMessage(), e);
        }
    }

    public List<RecordingUnitStratiRelSeeder.RecordingUnitStratiRelDTO> parseStratiRels(Sheet sheet) {
        if (sheet == null) return List.of();
        try {
            Row header = sheet.getRow(0);
            if (header == null) return List.of();

            Map<String, Integer> cols = indexColumns(header);
            if (cols.get("us1") == null || cols.get("us2") == null) return List.of();

            List<RecordingUnitStratiRelSeeder.RecordingUnitStratiRelDTO> specs = new ArrayList<>();
            forEachDataRow(sheet, row -> {
                String us1 = getStringCellOrNull(row, cols, "us1");
                String us2 = getStringCellOrNull(row, cols, "us2");
                if (us1 == null || us1.isBlank()) return;
                if (us2 == null || us2.isBlank()) return;
                ConceptSeeder.ConceptKey relKey = conceptKeyFromUri(getStringCellOrNull(row, cols, "relation"));
                String direction  = getStringCellOrNull(row, cols, "direction vocabulaire");
                String asynchrone = getStringCellOrNull(row, cols, "asynchrone");
                String incertain  = getStringCellOrNull(row, cols, "incertain");
                specs.add(new RecordingUnitStratiRelSeeder.RecordingUnitStratiRelDTO(
                        us1, us2, relKey,
                        "True".equals(direction),
                        "True".equals(asynchrone),
                        "True".equals(incertain)));
            });
            return specs;
        } catch (Exception e) {
            throw new IllegalStateException("[Feuille '" + sheet.getSheetName() + "'] : " + e.getMessage(), e);
        }
    }


    public List<ActionCodeSeeder.ActionCodeSpec> parseActionCodes(Sheet sheet) {
        if (sheet == null) return List.of();
        try {
            Row header = sheet.getRow(0);
            if (header == null) return List.of();

            Map<String, Integer> cols = indexColumns(header);
            if (cols.get("code") == null || cols.get(TYPE_URI) == null) return List.of();

            List<ActionCodeSeeder.ActionCodeSpec> specs = new ArrayList<>();
            forEachDataRow(sheet, row -> {
                String code = getStringCellOrNull(row, cols, "code");
                if (code == null || code.isBlank()) return;
                String typeUri = getStringCellOrNull(row, cols, TYPE_URI);
                specs.add(new ActionCodeSeeder.ActionCodeSpec(
                        code,
                        extractIdcFromUri(typeUri).orElse(null),
                        extractIdtFromUri(typeUri).orElse(null)
                ));
            });
            return specs;
        } catch (Exception e) {
            throw new IllegalStateException("[Feuille '" + sheet.getSheetName() + "'] : " + e.getMessage(), e);
        }
    }

    public List<ActionUnitSeeder.ActionUnitSpecs> parseActionUnits(Sheet sheet) {
        if (sheet == null || sheet.getRow(0) == null) return List.of();
        try {
            Map<String, Integer> cols = indexColumns(sheet.getRow(0));
            List<ActionUnitSeeder.ActionUnitSpecs> result = new ArrayList<>();
            forEachDataRow(sheet, row -> parseRowToActionUnit(row, cols).ifPresent(result::add));
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("[Feuille '" + sheet.getSheetName() + "'] : " + e.getMessage(), e);
        }
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


    public List<RecordingUnitSeeder.RecordingUnitSpecs> parseRecordingUnits(Sheet sheet,
        ImportScope scope, ActionUnitDTO actionUnit) {
        if (sheet == null || sheet.getRow(0) == null) return List.of();
        try {
            Map<String, Integer> cols = indexColumns(sheet.getRow(0));
            List<RecordingUnitSeeder.RecordingUnitSpecs> result = new ArrayList<>();
            forEachDataRow(sheet, row -> parseRowToRecordingUnit(row, cols, scope == ImportScope.PROJECT ? actionUnit : null).ifPresent(result::add));
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("[Feuille '" + sheet.getSheetName() + "'] : " + e.getMessage(), e);
        }
    }

    private Optional<RecordingUnitSeeder.RecordingUnitSpecs> parseRowToRecordingUnit(
            Row row, Map<String, Integer> cols,
            @Nullable ActionUnitDTO actionUnit) {
        String identStr    = getStringCellOrNull(row, cols, IDENTIFIANT);
        String description = getStringCellOrNull(row, cols, "description");

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

        OffsetDateTime creationTime = OffsetDateTime.now();
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

// -------------------- Helpers --------------------

    private Integer parseIntegerSafe(String str) {
        if (str == null || str.isBlank()) return null;
        try {
            return Integer.valueOf(str.trim());
        } catch (NumberFormatException e) {
            return null;
        }
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


    public List<SpecimenSeeder.SpecimenSpecs> parseSpecimens(Sheet sheet, ImportScope scope, ActionUnitDTO actionUnit) {
        if (sheet == null) return List.of();
        try {
        Row header = sheet.getRow(0);
        if (header == null) return List.of();

        Map<String, Integer> cols = indexColumns(header);
        List<SpecimenSeeder.SpecimenSpecs> result = new ArrayList<>();

        forEachDataRow(sheet, row -> {
            String identStr = getStringCellOrNull(row, cols, IDENTIFIANT);
            if (identStr == null || identStr.isBlank()) return;

            Integer identifier = parseIntegerSafe(identStr);

            ConceptSeeder.ConceptKey typeKey        = conceptKeyFromUri(getStringCellOrNull(row, cols, "matiere"));
            ConceptSeeder.ConceptKey categoryKey    = conceptKeyFromUri(getStringCellOrNull(row, cols, "categorie"));
            ConceptSeeder.ConceptKey designationKey = conceptKeyFromUri(getStringCellOrNull(row, cols, "designation"));

            String institutionId = actionUnit != null ? actionUnit.getCreatedByInstitution().getIdentifier() : getStringCellOrNull(row, cols, INSTITUTION);

            String auteurFiche = getStringCellOrNull(row, cols, "auteur fiche email");
            List<String> authors = (auteurFiche == null || auteurFiche.isBlank()) ? List.of() : List.of(auteurFiche.trim());

            List<String> collectors = parsePersonList(getStringCellOrNull(row, cols, "collecteurs emails"));

            String ruStr = getStringCellOrNull(row, cols, "unite d'enregistrement");
            RecordingUnitSeeder.RecordingUnitKey recordingUnitKey =
                    (ruStr == null || ruStr.isBlank()) ? null : new RecordingUnitSeeder.RecordingUnitKey(ruStr,
                            actionUnit.getFullIdentifier());

            result.add(new SpecimenSeeder.SpecimenSpecs(
                    identStr,
                    identifier,
                    typeKey,
                    categoryKey,
                    designationKey,
                    SIAMOIS_SYSTEM,
                    institutionId,
                    authors,
                    collectors,
                    OffsetDateTime.now(),
                    recordingUnitKey
            ));
        });

        return result;
        } catch (Exception e) {
            throw new IllegalStateException("[Feuille '" + sheet.getSheetName() + "'] : " + e.getMessage(), e);
        }
    }



    public List<PhaseSeeder.PhaseSpecs> parsePhases(Sheet sheet, ImportScope scope, ActionUnitDTO actionUnit) {
        if (sheet == null) return List.of();
        try {
            Row header = sheet.getRow(0);
            if (header == null) return List.of();

            Map<String, Integer> cols = indexColumns(header);
            List<PhaseSeeder.PhaseSpecs> result = new ArrayList<>();

            forEachDataRow(sheet, row -> {
                String identifier = getStringCellOrNull(row, cols, IDENTIFIANT);
                if (identifier == null || identifier.isBlank()) return;

                String title       = getStringCellOrNull(row, cols, "titre");
                ConceptSeeder.ConceptKey type = conceptKeyFromUri(getStringCellOrNull(row, cols, TYPE_URI));
                String description  = getStringCellOrNull(row, cols, "description");
                Integer orderNumber = getIntegerCellOrNull(row, cols, "ordre");
                Integer lowerBound  = getIntegerCellOrNull(row, cols, "borne inferieure");
                Integer upperBound  = getIntegerCellOrNull(row, cols, "borne superieure");

                String authorEmail = getStringCellOrNull(row, cols, "auteur");

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

            return result;
        } catch (Exception e) {
            throw new IllegalStateException("[Feuille '" + sheet.getSheetName() + "'] : " + e.getMessage(), e);
        }
    }

    public Optional<String> extractIdtFromUri(String uri) {
        if (uri == null) return Optional.empty();
        int idx = uri.indexOf("idt=");
        if (idx < 0) throw new IllegalStateException("URL de concept "+uri+"invalide");
        String sub = uri.substring(idx + 4);
        int amp = sub.indexOf('&');
        if (amp >= 0) sub = sub.substring(0, amp);
        return Optional.of(URLDecoder.decode(sub, StandardCharsets.UTF_8));
    }

    public Optional<String> extractIdcFromUri(String uri) {
        if (uri == null) return Optional.empty();
        int idx = uri.indexOf("idc=");
        if (idx < 0) throw new IllegalStateException("URL de concept "+uri+" invalide");
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

    public String getStringCellOrNull(Row row, Map<String, Integer> cols, String key) {
        try {
            Integer index = cols.get(key);
            return index != null ? getStringCell(row, index) : null;
        } catch (Exception e) {
            throw new IllegalStateException("[colonne '" + key + "'] : " + e.getMessage(), e);
        }
    }

    public Integer getIntegerCellOrNull(Row row, Map<String, Integer> cols, String key) {
        Integer colIndex = cols.get(key);
        if (colIndex == null) return null;
        Cell cell = row.getCell(colIndex);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) return (int) cell.getNumericCellValue();
        String s = getStringCell(cell);
        return parseIntegerSafe(s);
    }

    public String getStringCell(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            double v = cell.getNumericCellValue();
            return v == Math.floor(v) && !Double.isInfinite(v)
                    ? String.valueOf((long) v)
                    : String.valueOf(v);
        }
        String val = cell.getStringCellValue();
        return val != null ? val.trim() : null;
    }

    public List<String> parsePersonList(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split("[;,]"))
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
                try {
                    consumer.accept(row);
                } catch (Exception e) {
                    throw new IllegalStateException("[ligne " + (r + 1) + "] : " + e.getMessage(), e);
                }
            }
        }
    }



}
