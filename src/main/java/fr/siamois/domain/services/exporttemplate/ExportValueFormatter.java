package fr.siamois.domain.services.exporttemplate;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.OutputType;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.form.measurement.MeasurementAnswer;
import fr.siamois.domain.models.vocabulary.Concept;
import org.locationtech.jts.geom.Geometry;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

/**
 * Met en forme une valeur brute lue sur une entité (concept, personne, nombre, date, entité liée…) en
 * texte de cellule. Les libellés de concepts sont fournis d'avance, résolus en une requête pour tout le
 * classeur. Un type de valeur inconnu donne {@link Optional#empty()} : jamais de {@code toString()} d'une
 * entité (il toucherait des relations paresseuses). Un instant sans fuseau est écrit en UTC, pour que le
 * fichier ne dépende pas du fuseau du serveur.
 */
final class ExportValueFormatter {

    private final Map<Long, String> conceptLabels;

    ExportValueFormatter(Map<Long, String> conceptLabels) {
        this.conceptLabels = conceptLabels;
    }

    /** @return le texte de la valeur, ou vide si son type n'est pas pris en charge */
    Optional<String> format(@Nullable Object value, OutputType output) {
        if (value == null) {
            return Optional.of("");
        }
        if (value instanceof String s) return Optional.of(s);
        if (value instanceof Boolean b) return Optional.of(b.toString());
        if (value instanceof Number n) return Optional.of(plain(n));
        if (value instanceof MeasurementAnswer m) return Optional.of(measurement(m));
        if (value instanceof Concept c) return Optional.of(conceptLabel(c));
        if (value instanceof Person p) return Optional.of(p.displayName());
        if (value instanceof Geometry g) return Optional.of(g.toText());
        Optional<String> entity = entityName(value);
        return entity.isPresent() ? entity : temporal(value, output);
    }

    /** Valeur telle que saisie : elle va avec l'unité saisie (propriété « unit »). */
    private static String measurement(MeasurementAnswer m) {
        Double v = m.getNumericValue() != null ? m.getNumericValue() : m.getNormalizedValue();
        return v == null ? "" : plain(v);
    }

    /** Entité liée : son identifiant complet, son nom ou son titre selon le type ; vide si le type est inconnu. */
    private static Optional<String> entityName(Object value) {
        if (value instanceof ActionUnit a) return Optional.ofNullable(a.getFullIdentifier()).or(() -> Optional.ofNullable(a.getName()));
        if (value instanceof SpatialUnit s) return Optional.ofNullable(s.getName());
        if (value instanceof RecordingUnit r) return Optional.ofNullable(r.getFullIdentifier());
        if (value instanceof Specimen sp) return Optional.ofNullable(sp.getFullIdentifier());
        if (value instanceof Phase ph) return Optional.ofNullable(ph.getTitle());
        if (value instanceof Document d) return Optional.ofNullable(d.getTitle());
        return Optional.empty();
    }

    private String conceptLabel(Concept concept) {
        String label = concept.getId() == null ? null : conceptLabels.get(concept.getId());
        if (label != null && !label.isBlank()) return label;
        return concept.getExternalId() == null ? "" : concept.getExternalId();
    }

    private static String plain(Number n) {
        if (n instanceof BigDecimal d) return d.stripTrailingZeros().toPlainString();
        if (n instanceof Double || n instanceof Float) {
            return new BigDecimal(n.toString()).stripTrailingZeros().toPlainString();
        }
        return n.toString();
    }

    private static Optional<String> temporal(Object value, OutputType output) {
        boolean dateOnly = output == OutputType.DATE;
        if (value instanceof LocalDate d) return Optional.of(d.format(DateTimeFormatter.ISO_LOCAL_DATE));
        if (value instanceof OffsetDateTime d) {
            return Optional.of(dateOnly ? d.toLocalDate().toString() : d.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
        if (value instanceof ZonedDateTime d) {
            return Optional.of(dateOnly ? d.toLocalDate().toString() : d.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
        if (value instanceof LocalDateTime d) {
            return Optional.of(dateOnly ? d.toLocalDate().toString() : d.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        if (value instanceof Instant i) {
            return temporal(i.atZone(ZoneOffset.UTC), output);
        }
        return Optional.empty();
    }
}
