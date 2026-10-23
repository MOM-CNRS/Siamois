package fr.siamois.domain.services.actionunit.template;

/**
 * A starting point offered when creating a project. A template is the "default project" (types and vocabularies
 * read from the SIAMOIS common-core thesaurus), "empty" (the minimal configuration every project always got) or an
 * existing project whose configuration is copied; the id is an opaque
 * {@code kind[:reference]} string so that other kinds (stored templates that are not projects) can be added
 * without changing the API.
 *
 * @param id    stable identifier sent back at creation ({@code default}, {@code empty} or {@code project:<id>})
 * @param label name shown to the user
 * @param kind  {@link Kind} of the template
 */
public record ProjectTemplate(String id, String label, Kind kind) {

    public static final String DEFAULT_ID = "default";
    public static final String EMPTY_ID = "empty";
    public static final String PROJECT_PREFIX = "project:";

    public enum Kind { DEFAULT, EMPTY, PROJECT }

    public static String projectId(long projectId) {
        return PROJECT_PREFIX + projectId;
    }
}
