package fr.siamois.domain.models.identifier;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectIdentifierConstraintsTest {

    private static SQLException duplicateKey(String constraint) {
        return new SQLException("ERROR: duplicate key value violates unique constraint \"" + constraint + "\"", "23505");
    }

    @Test
    void theHibernateConstraintNameIsRecognised_whateverItsCase() {
        Throwable error = new DataIntegrityViolationException("x", new ConstraintViolationException("x",
                duplicateKey("other"), ProjectIdentifierConstraints.PHASE.toUpperCase()));

        assertThat(ProjectIdentifierConstraints.isViolatedBy(error)).isTrue();
    }

    @Test
    void aBareSqlUniqueViolationNamingTheConstraintIsRecognised() {
        Throwable error = new RuntimeException("commit failed", duplicateKey(ProjectIdentifierConstraints.DOCUMENT));

        assertThat(ProjectIdentifierConstraints.isViolatedBy(error)).isTrue();
    }

    @Test
    void anotherConstraintIsNotAnIdentifierConflict() {
        Throwable error = new DataIntegrityViolationException("x", new ConstraintViolationException("x",
                duplicateKey("uk_document_import_item_client_key"), "uk_document_import_item_client_key"));

        assertThat(ProjectIdentifierConstraints.isViolatedBy(error)).isFalse();
    }

    @Test
    void anotherSqlStateIsNotAnIdentifierConflict() {
        Throwable error = new SQLException(
                "null value in column \"" + ProjectIdentifierConstraints.STUDY + "\"", "23502");

        assertThat(ProjectIdentifierConstraints.isViolatedBy(error)).isFalse();
    }

    @Test
    void noErrorIsNoConflict() {
        assertThat(ProjectIdentifierConstraints.isViolatedBy(null)).isFalse();
        assertThat(ProjectIdentifierConstraints.isViolatedBy(new IllegalStateException())).isFalse();
    }
}
