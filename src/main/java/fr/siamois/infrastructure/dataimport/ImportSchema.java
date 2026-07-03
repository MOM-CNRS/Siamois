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

    public static final String ACTION_UNIT = "action_unit";
    public static final String SPATIAL_UNIT = "spatial_unit";
    public static final String RECORDING_UNIT = "recording_unit";
    public static final String SPECIMEN = "specimen";
    public static final String PHASE = "phase";
    public static final String RECORDING_REL = "recordingRel";
    public static final String STRATI_REL = "stratiRel";
    /**
     * Maps technical table ID → default French sheet name for workbooks without a _meta sheet
     * (or for tables not covered by an explicit _meta sheet entry).
     *
     * Keys must match the technical IDs used in importFromExcel()/getSheetsForTable() and
     * EXPECTED_COLUMNS ("spatial_unit", "recording_unit"…) — not the French display names.
     */
    public static final Map<String, String> DEFAULT_SHEET_NAMES = Map.ofEntries(
            Map.entry(INSTITUTION,      "Institution"),
            Map.entry(PERSON,           "Personne"),
            Map.entry("code",           "Code"),
            Map.entry(ACTION_UNIT,    "Unite action"),
            Map.entry(SPATIAL_UNIT,   "Unité spatiale"),
            Map.entry(RECORDING_UNIT, "UE"),
            Map.entry(SPECIMEN,       "Prelev"),
            Map.entry(PHASE,          "Phase"),
            Map.entry(RECORDING_REL,   "UE_rel"),
            Map.entry(STRATI_REL,      "Strati_Rel")
    );

    /**
     * Maps technical table ID → French label to display in the sheet-mapping UI
     * (e.g. "this sheet maps to table: Lieu"), as opposed to the technical ID itself.
     */
    public static final Map<String, String> TABLE_LABELS = Map.ofEntries(
            Map.entry(INSTITUTION,      "Institution"),
            Map.entry(PERSON,           "Personne"),
            Map.entry("code",           "Code"),
            Map.entry(ACTION_UNIT,    "Unité d'action"),
            Map.entry(SPATIAL_UNIT,   "Lieu"),
            Map.entry(RECORDING_UNIT, "UE"),
            Map.entry(SPECIMEN,       "Mobilier"),
            Map.entry(PHASE,          "Phase"),
            Map.entry(RECORDING_REL,   "Relations UE"),
            Map.entry(STRATI_REL,      "Stratigraphie")
    );

    /** Maps technical table ID → canonical column names expected for that entity. */
    public static final Map<String, List<String>> EXPECTED_COLUMNS = Map.ofEntries(
            Map.entry(INSTITUTION,      List.of("nom", DESCRIPTION, IDENTIFIANT, "email admins", "thesaurus")),
            Map.entry(PERSON,           List.of("email", "nom", "prenom", IDENTIFIANT)),
            Map.entry("code",           List.of("code", TYPE_URI)),
            Map.entry(ACTION_UNIT,    List.of("nom", IDENTIFIANT, "code", TYPE_URI, "createur", INSTITUTION,
                                                "contexte spatiale", "date debut", "date fin", "localisation principale")),
            Map.entry(SPATIAL_UNIT,   List.of("nom", "uri type", INSTITUTION, "enfants")),
            Map.entry(RECORDING_UNIT, List.of(IDENTIFIANT, DESCRIPTION, TYPE_URI, "cycle uri", "agent uri",
                                                "interpretation uri", "author email", INSTITUTION,
                                                "contributeurs email", "date d'ouverture", "date de fermeture",
                                                "unite spatiale", "unite d'action",
                                                "couleur de la matrice", "texture de la matrice", "composition de la matrice",
                                                "phases")),
            Map.entry(SPECIMEN,       List.of(IDENTIFIANT, INSTITUTION, "auteur fiche email",
                                                "matiere", "categorie", "designation",
                                                "collecteurs emails", "unite d'enregistrement")),
            Map.entry(PHASE,          List.of(IDENTIFIANT, "titre", TYPE_URI, DESCRIPTION,
                                                "ordre", "borne inferieure", "borne superieure",
                                                "auteur", "projet", INSTITUTION)),
            Map.entry(RECORDING_REL,   List.of("parent", "enfant")),
            Map.entry(STRATI_REL,      List.of("us1", "us2", "relation", "direction vocabulaire", "asynchrone", "incertain"))
    );
}
