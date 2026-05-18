package fr.siamois.ui.api.openapi.v1;

/**
 * Libellés de tags OpenAPI (ordre défini dans {@link fr.siamois.ui.config.api.OpenApiConfig}).
 */
public final class OpenApiTags {

    public static final String AUTH = "Authentification";
    public static final String ORGANISATION = "Organisations";
    public static final String PROJECT = "Projets";
    public static final String RECORDING_UNIT = "Unités d'enregistrement";
    public static final String MOBILIER = "Mobiliers";
    public static final String DOCUMENT = "Documents";
    public static final String VOCABULARY = "Vocabulaires";
    public static final String SPATIAL_UNIT = "Lieux (autocomplétion)";
    public static final String USER = "Utilisateurs";

    private OpenApiTags() {
    }
}
