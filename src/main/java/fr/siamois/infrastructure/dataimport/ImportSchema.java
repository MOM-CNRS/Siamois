package fr.siamois.infrastructure.dataimport;

import java.util.List;
import java.util.Map;

/**
 * Schema constants for the SIAMOIS Excel import format:
 * canonical column name constants, default sheet names and expected columns per entity.
 */
public final class ImportSchema {

    private ImportSchema() {}

    // ── Shared canonical column name constants ─────────────────────────────
    public static final String IDENTIFIANT    = "identifiant";
    public static final String PERSON         = "person";
    public static final String INSTITUTION    = "institution";
    public static final String TYPE_URI       = "type uri";
    public static final String SIAMOIS_SYSTEM = "siamois system";
    public static final String DESCRIPTION    = "description";

    // Literals used several times below
    private static final String HEADER_INSTITUTION = "Institution";
    private static final String HEADER_RECORDING_UNIT = "Unité d'enregistrement";
    private static final String HEADER_DESCRIPTION = "Description";
    private static final String HEADER_IDENTIFIER = "Identifiant";
    private static final String KEY_COMMENT = "commentaire";
    private static final String KEY_GEOMETRY = "geometrie";
    private static final String KEY_CHRONOLOGY_LABEL = "chronologie label";
    private static final String KEY_PARENT = "parent";
    private static final String KEY_CHILD = "enfant";

    public static final String ACTION_UNIT = "action_unit";
    public static final String SPATIAL_UNIT = "spatial_unit";
    public static final String RECORDING_UNIT = "recording_unit";
    public static final String SPECIMEN = "specimen";
    public static final String PHASE = "phase";
    public static final String RECORDING_REL = "recordingRel";
    public static final String STRATI_REL = "stratiRel";
    public static final String SPATIAL_UNIT_REL = "spatialUnitRel";
    /**
     * Maps technical table ID → default French sheet name for workbooks without a _meta sheet
     * (or for tables not covered by an explicit _meta sheet entry).
     *
     * Keys must match the technical IDs used in importFromExcel()/getSheetsForTable() and
     * EXPECTED_COLUMNS ("spatial_unit", "recording_unit"…) — not the French display names.
     */
    public static final Map<String, String> DEFAULT_SHEET_NAMES = Map.ofEntries(
            Map.entry(INSTITUTION,      HEADER_INSTITUTION),
            Map.entry(PERSON,           "Personne"),
            Map.entry("code",           "Code"),
            Map.entry(ACTION_UNIT,    "Unite action"),
            Map.entry(SPATIAL_UNIT,   "Lieu"),
            Map.entry(RECORDING_UNIT, HEADER_RECORDING_UNIT),
            Map.entry(SPECIMEN,       "Mobilier"),
            Map.entry(PHASE,          "Phase"),
            Map.entry(RECORDING_REL,   "Relations entre UE"),
            Map.entry(STRATI_REL,      "Stratigraphie"),
            Map.entry(SPATIAL_UNIT_REL, "Relations entre lieux")
    );

    /**
     * The sheet names the default ones replaced, still recognized so that workbooks built before the rename keep
     * importing.
     */
    public static final Map<String, List<String>> LEGACY_SHEET_NAMES = Map.of(
            SPATIAL_UNIT,     List.of("Unité spatiale"),
            RECORDING_UNIT,   List.of("UE"),
            SPECIMEN,         List.of("Prelev"),
            RECORDING_REL,    List.of("UE_rel"),
            STRATI_REL,       List.of("Strati_Rel"),
            SPATIAL_UNIT_REL, List.of("Lieu_rel")
    );

    /**
     * Maps technical table ID → French label to display in the sheet-mapping UI
     * (e.g. "this sheet maps to table: Lieu"), as opposed to the technical ID itself.
     */
    public static final Map<String, String> TABLE_LABELS = Map.ofEntries(
            Map.entry(INSTITUTION,      HEADER_INSTITUTION),
            Map.entry(PERSON,           "Personne"),
            Map.entry("code",           "Code"),
            Map.entry(ACTION_UNIT,    "Unité d'action"),
            Map.entry(SPATIAL_UNIT,   "Lieu"),
            Map.entry(RECORDING_UNIT, HEADER_RECORDING_UNIT),
            Map.entry(SPECIMEN,       "Mobilier"),
            Map.entry(PHASE,          "Phase"),
            Map.entry(RECORDING_REL,   "Relations entre UE"),
            Map.entry(STRATI_REL,      "Stratigraphie"),
            Map.entry(SPATIAL_UNIT_REL, "Relations entre lieux")
    );

    public static final String TYPE_LABEL = "type label";
    /** Maps technical table ID → canonical column names expected for that entity. */
    public static final Map<String, List<String>> EXPECTED_COLUMNS = Map.ofEntries(
            Map.entry(INSTITUTION,      List.of("nom", DESCRIPTION, IDENTIFIANT, "email admins", "thesaurus")),
            Map.entry(PERSON,           List.of("email", "nom", "prenom", IDENTIFIANT)),
            Map.entry("code",           List.of("code", TYPE_URI)),
            Map.entry(ACTION_UNIT,    List.of("nom", IDENTIFIANT, "code", TYPE_URI, "createur", INSTITUTION,
                                                "contexte spatiale", "date debut", "date fin", "localisation principale")),
            Map.entry(SPATIAL_UNIT,   List.of("nom", "uri type", TYPE_LABEL, INSTITUTION, "enfants",
                                                "code insee", "lieu-dit", "rue", "code postal", "commune",
                                                "section cadastrale", "numero parcelle", "numero carte shom",
                                                "commentaires")),
            Map.entry(RECORDING_UNIT, List.of(IDENTIFIANT, DESCRIPTION, TYPE_URI, TYPE_LABEL, "nature uri", "nature label",
                                                "cycle uri", "cycle label",
                                                "agent uri", "agent label", "interpretation uri", "interpretation label",
                                                "author email", INSTITUTION,
                                                "contributeurs email", "date d'ouverture", "date de fermeture",
                                                "unite spatiale", "unite d'action",
                                                "couleur de la matrice", "texture de la matrice", "composition de la matrice",
                                                "phases",
                                                KEY_COMMENT, "taq", "tpq", KEY_GEOMETRY,
                                                "erosion forme uri", "erosion forme label",
                                                "erosion orientation uri", "erosion orientation label",
                                                "erosion profil uri", "erosion profil label",
                                                "chronologie uri", KEY_CHRONOLOGY_LABEL)),
            Map.entry(SPECIMEN,       List.of(IDENTIFIANT, INSTITUTION, "auteur fiche email",
                                                "matiere", "matiere uri", "matiere label",
                                                "categorie", "categorie uri", "categorie label",
                                                "designation", "designation uri", "designation label",
                                                "collecteurs emails", "unite d'enregistrement",
                                                DESCRIPTION, KEY_COMMENT, "numero isolat", "taq", "tpq",
                                                "autre identifiant", "chronologie uri", KEY_CHRONOLOGY_LABEL,
                                                "nombre d'elements", "date de collecte",
                                                "methode de collecte uri", "methode de collecte label",
                                                "etat sanitaire uri", "etat sanitaire label",
                                                "classe matiere uri", "classe matiere label")),
            Map.entry(PHASE,          List.of(IDENTIFIANT, "titre", TYPE_URI, TYPE_LABEL, DESCRIPTION,
                                                "ordre", "borne inferieure", "borne superieure",
                                                "auteur", "projet", INSTITUTION,
                                                "periodes uri", "periodes label", "mots cles uri", "mots cles label")),
            Map.entry(RECORDING_REL,   List.of(KEY_PARENT, KEY_CHILD)),
            Map.entry(STRATI_REL,      List.of("us1", "us2", "relation", "relation label", "direction vocabulaire", "asynchrone", "incertain")),
            Map.entry(SPATIAL_UNIT_REL, List.of(KEY_PARENT, KEY_CHILD))
    );

    /** Header of the sheet-mapping sheet ({@link #META_SHEET}): which SIAMOIS table a sheet of the file feeds. */
    public static final String META_TABLE = "Table dans SIAMOIS";
    public static final String META_SHEET_NAME = "Nom de la feuille dans votre fichier";
    public static final String META_COLUMN = "Colonne dans votre fichier";
    public static final String META_FIELD = "Champ dans SIAMOIS";
    public static final String META_SHEET = "_meta";

    /**
     * Technical headers of the sheet-mapping sheet, from before it got readable ones; still read.
     * Order matches {@link #META_TABLE}, {@link #META_SHEET_NAME}, {@link #META_COLUMN}, {@link #META_FIELD}.
     */
    public static final List<String> LEGACY_META_HEADERS = List.of("sheet_id", "sheet_name", "column_alias", "column_canonical");

    /**
     * Per table, the default header of each column read by label, in the order of the model: internal column key →
     * header. The internal keys are what the importer reads; the headers are the names of the fields in a default
     * project, so a user recognizes them. No URI columns here: concepts are looked up by their label.
     */
    public static final Map<String, Map<String, String>> COLUMN_LABELS = Map.of(
            SPATIAL_UNIT, ordered(
                    "nom", "Intitulé", TYPE_LABEL, "Type", INSTITUTION, HEADER_INSTITUTION, "enfants", "Enfants"),
            RECORDING_UNIT, ordered(
                    IDENTIFIANT, HEADER_IDENTIFIER, TYPE_LABEL, "Type", DESCRIPTION, HEADER_DESCRIPTION,
                    "nature label", "Nature", "cycle label", "Cycle géomorphologique",
                    "agent label", "Agent géomorphologique", "interpretation label", "Interprétation normalisée",
                    "author email", "Auteur scientifique/technique", INSTITUTION, HEADER_INSTITUTION,
                    "contributeurs email", "Contributeur(s) scientifique(s)/technique(s)",
                    "date d'ouverture", "Date d'ouverture", "date de fermeture", "Date de fermeture",
                    "unite spatiale", "Lieu(x)", "unite d'action", "Projet", "phases", "Phases",
                    "couleur de la matrice", "Couleur de la matrice", "texture de la matrice", "Texture de la matrice",
                    "composition de la matrice", "Composition de la matrice",
                    KEY_COMMENT, "Informations complémentaires", "taq", "TAQ", "tpq", "TPQ",
                    KEY_GEOMETRY, "Géométrie (WKT)",
                    "erosion forme label", "Forme de l'érosion", "erosion orientation label", "Orientation de l'érosion",
                    "erosion profil label", "Profil de l'érosion", KEY_CHRONOLOGY_LABEL, "Attribution chronologique"),
            SPECIMEN, ordered(
                    IDENTIFIANT, HEADER_IDENTIFIER, "categorie label", "Type", "matiere label", "Matière",
                    "classe matiere label", "Classe", "designation label", "Détermination",
                    INSTITUTION, HEADER_INSTITUTION, "auteur fiche email", "Auteurs", "collecteurs emails", "Collecteurs",
                    "unite d'enregistrement", HEADER_RECORDING_UNIT, DESCRIPTION, HEADER_DESCRIPTION,
                    KEY_COMMENT, "Informations complémentaires", "numero isolat", "Identifiant d'isolation",
                    "taq", "TAQ", "tpq", "TPQ", "autre identifiant", "Autre identifiant",
                    KEY_CHRONOLOGY_LABEL, "Attribution chronologique", "nombre d'elements", "Nombre d'éléments",
                    "date de collecte", "Date de collecte", "methode de collecte label", "Nature du prélèvement",
                    "etat sanitaire label", "État sanitaire"),
            PHASE, ordered(
                    IDENTIFIANT, HEADER_IDENTIFIER, "titre", "Titre", TYPE_LABEL, "Type", DESCRIPTION, HEADER_DESCRIPTION,
                    "ordre", "Numéro d'ordre", "borne inferieure", "Borne inférieure", "borne superieure", "Borne supérieure",
                    "auteur", "Auteur", "projet", "Projet", INSTITUTION, HEADER_INSTITUTION,
                    "periodes label", "Périodes", "mots cles label", "Mots-clés"),
            RECORDING_REL, ordered(KEY_PARENT, "Parent", KEY_CHILD, "Enfant"),
            STRATI_REL, ordered(
                    "us1", "Unité 1", "us2", "Unité 2", "relation label", "Type de relation",
                    "asynchrone", "Asynchrone", "incertain", "Incertain"),
            SPATIAL_UNIT_REL, ordered(KEY_PARENT, "Parent", KEY_CHILD, "Enfant"));

    private static Map<String, String> ordered(String... keysAndLabels) {
        Map<String, String> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < keysAndLabels.length; i += 2) {
            map.put(keysAndLabels[i], keysAndLabels[i + 1]);
        }
        return java.util.Collections.unmodifiableMap(map);
    }

    private static String norm(String s) {
        return ExcelCellHelper.normalize(s);
    }

    /** The table a sheet-mapping cell designates: its technical id, or its name as shown (also the former ones). */
    public static String tableIdOf(String cell) {
        if (cell == null) {
            return null;
        }
        String trimmed = cell.trim();
        if (TABLE_LABELS.containsKey(trimmed)) {
            return trimmed;
        }
        String wanted = norm(trimmed);
        for (Map.Entry<String, String> e : TABLE_LABELS.entrySet()) {
            if (norm(e.getValue()).equals(wanted)) {
                return e.getKey();
            }
        }
        for (Map.Entry<String, String> e : DEFAULT_SHEET_NAMES.entrySet()) {
            if (norm(e.getValue()).equals(wanted)) {
                return e.getKey();
            }
        }
        for (Map.Entry<String, List<String>> e : LEGACY_SHEET_NAMES.entrySet()) {
            if (e.getValue().stream().anyMatch(n -> norm(n).equals(wanted))) {
                return e.getKey();
            }
        }
        return trimmed;
    }

    /** The internal key of a column of a table, from its default header or from the key itself; normalized. */
    public static String columnKey(String tableId, String header) {
        String wanted = norm(header);
        for (Map.Entry<String, String> e : COLUMN_LABELS.getOrDefault(tableId, Map.of()).entrySet()) {
            if (norm(e.getValue()).equals(wanted)) {
                return e.getKey();
            }
        }
        return wanted;
    }

    /** The header a user knows an internal column key by; the key itself when it has no default header. */
    public static String columnLabel(String key) {
        for (Map<String, String> labels : COLUMN_LABELS.values()) {
            String label = labels.get(key);
            if (label != null) {
                return label;
            }
        }
        return key;
    }
}
