package fr.siamois.domain.models.identifier;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Set;

/**
 * The database constraints that keep an entity's identifier unique within its project, one per table: a
 * {@code (fk_action_unit_id, identifier)} pair. The application checks before writing; these constraints are
 * the guarantee when two writes race, and {@link #isViolatedBy(Throwable)} tells such a failure apart from
 * other integrity errors, so that it can be answered as a conflict.
 */
public final class ProjectIdentifierConstraints {

    public static final String RECORDING_UNIT = "uk_recording_unit_project_identifier";
    public static final String SPECIMEN = "uk_specimen_project_identifier";
    public static final String CONTAINER = "uk_container_project_identifier";
    public static final String PHASE = "uk_phase_project_identifier";
    public static final String STUDY = "uk_study_project_identifier";
    public static final String DOCUMENT = "uk_document_project_identifier";

    private static final Set<String> ALL = Set.of(RECORDING_UNIT, SPECIMEN, CONTAINER, PHASE, STUDY, DOCUMENT);

    /** PostgreSQL's SQLState for a unique violation. */
    private static final String UNIQUE_VIOLATION = "23505";

    private ProjectIdentifierConstraints() {
    }

    /**
     * @return {@code true} when {@code error}, or one of its causes, is the violation of one of these
     * constraints — however it was wrapped on the way (Spring's {@code DataIntegrityViolationException},
     * a service's save exception, a commit failure).
     */
    public static boolean isViolatedBy(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof org.hibernate.exception.ConstraintViolationException cve
                    && cve.getConstraintName() != null
                    && ALL.contains(cve.getConstraintName().toLowerCase(Locale.ROOT))) {
                return true;
            }
            if (t instanceof SQLException sql && UNIQUE_VIOLATION.equals(sql.getSQLState())
                    && sql.getMessage() != null && mentionsOne(sql.getMessage())) {
                return true;
            }
        }
        return false;
    }

    private static boolean mentionsOne(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        return ALL.stream().anyMatch(name -> lower.contains("\"" + name + "\""));
    }
}
