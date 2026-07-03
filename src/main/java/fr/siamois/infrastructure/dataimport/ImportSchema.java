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
            Map.entry("action_unit",    "Unite action"),
            Map.entry("spatial_unit",   "Unité spatiale"),
            Map.entry("recording_unit", "UE"),
            Map.entry("specimen",       "Prelev"),
            Map.entry("phase",          "Phase"),
            Map.entry("recordingRel",   "UE_rel"),
            Map.entry("stratiRel",      "Strati_Rel")
    );

    /**
     * Maps technical table ID → French label to display in the sheet-mapping UI
     * (e.g. "this sheet maps to table: Lieu"), as opposed to the technical ID itself.
     */
    public static final Map<String, String> TABLE_LABELS = Map.ofEntries(
            Map.entry(INSTITUTION,      "Institution"),
            Map.entry(PERSON,           "Personne"),
            Map.entry("code",           "Code"),
            Map.entry("action_unit",    "Unité d'action"),
            Map.entry("spatial_unit",   "Lieu"),
            Map.entry("recording_unit", "UE"),
            Map.entry("specimen",       "Mobilier"),
            Map.entry("phase",          "Phase"),
            Map.entry("recordingRel",   "Relations UE"),
            Map.entry("stratiRel",      "Stratigraphie")
    );

    /** Maps technical table ID → canonical column names expected for that entity. */
    public static final Map<String, List<String>> EXPECTED_COLUMNS = Map.ofEntries(
            Map.entry(INSTITUTION,      List.of("nom", DESCRIPTION, IDENTIFIANT, "email admins", "thesaurus")),
            Map.entry(PERSON,           List.of("email", "nom", "prenom", IDENTIFIANT)),
            Map.entry("code",           List.of("code", TYPE_URI)),
            Map.entry("action_unit",    List.of("nom", IDENTIFIANT, "code", TYPE_URI, "createur", INSTITUTION,
                                                "contexte spatiale", "date debut", "date fin", "localisation principale")),
            Map.entry("spatial_unit",   List.of("nom", "uri type", INSTITUTION, "enfants")),
            Map.entry("recording_unit", List.of(IDENTIFIANT, DESCRIPTION, TYPE_URI, "cycle uri", "agent uri",
                                                "interpretation uri", "author email", INSTITUTION,
                                                "contributeurs email", "date d'ouverture", "date de fermeture",
                                                "unite spatiale", "unite d'action",
                                                "couleur de la matrice", "texture de la matrice", "composition de la matrice",
                                                "phases")),
            Map.entry("specimen",       List.of(IDENTIFIANT, INSTITUTION, "auteur fiche email",
                                                "matiere", "categorie", "designation",
                                                "collecteurs emails", "unite d'enregistrement")),
            Map.entry("phase",          List.of(IDENTIFIANT, "titre", TYPE_URI, DESCRIPTION,
                                                "ordre", "borne inferieure", "borne superieure",
                                                "auteur", "projet", INSTITUTION)),
            Map.entry("recordingRel",   List.of("parent", "enfant")),
            Map.entry("stratiRel",      List.of("us1", "us2", "relation", "direction vocabulaire", "asynchrone", "incertain"))
    );
}
