package fr.siamois.domain.services.exporttemplate;

import fr.siamois.domain.models.exporttemplate.ExportSubject;
import org.springframework.lang.Nullable;

import java.util.Map;

/**
 * Une ligne lue pour une feuille : soit une entité (projet, unité d'enregistrement, mobilier…), soit
 * une ligne d'une table technique, qui porte alors des extrémités nommées (elles-mêmes des lignes
 * d'entité) et des valeurs nommées.
 */
public record ExportRow(
        @Nullable ExportSubject subject,
        @Nullable Object entity,
        Map<String, ExportRow> ends,
        Map<String, Object> values) {

    public static ExportRow of(ExportSubject subject, Object entity) {
        return new ExportRow(subject, entity, Map.of(), Map.of());
    }

    public static ExportRow technical(Map<String, ExportRow> ends, Map<String, Object> values) {
        return new ExportRow(null, null, ends, values);
    }

    public boolean isTechnical() {
        return subject == null;
    }
}
