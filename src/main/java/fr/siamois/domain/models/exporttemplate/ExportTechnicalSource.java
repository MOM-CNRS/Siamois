package fr.siamois.domain.models.exporttemplate;

import org.springframework.lang.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Registre fermé des tables techniques qu'une feuille peut prendre pour source. Un modèle ne désigne
 * qu'une clé : jamais un nom de table ni de colonne. Chaque entrée décrit la forme d'une ligne :
 * ses « extrémités » nommées (des entités, atteignables par un chemin) et ses valeurs nommées
 * (lisibles par {@code ColumnField}).
 */
public enum ExportTechnicalSource {

    /** Relation stratigraphique entre deux unités d'enregistrement du projet. */
    STRATIGRAPHIC_RELATIONSHIP(
            Map.of("unit1", ExportSubject.RECORDING_UNIT, "unit2", ExportSubject.RECORDING_UNIT),
            Set.of("relationType", "asynchronous", "uncertain")),

    /** Lien de hiérarchie entre deux unités d'enregistrement du projet. */
    RECORDING_UNIT_HIERARCHY(
            Map.of("parent", ExportSubject.RECORDING_UNIT, "child", ExportSubject.RECORDING_UNIT),
            Set.of());

    private final Map<String, ExportSubject> ends;
    private final Set<String> values;

    ExportTechnicalSource(Map<String, ExportSubject> ends, Set<String> values) {
        this.ends = ends;
        this.values = values;
    }

    /** Extrémités nommées de la ligne et l'entité qu'elles désignent. */
    public Map<String, ExportSubject> ends() {
        return ends;
    }

    /** Noms des valeurs lisibles par une colonne de la ligne. */
    public Set<String> valueNames() {
        return values;
    }

    /** Clé telle qu'écrite dans le JSON du modèle. */
    public String key() {
        return name();
    }

    public static Optional<ExportTechnicalSource> ofKey(@Nullable String key) {
        for (ExportTechnicalSource s : values()) {
            if (s.name().equals(key)) return Optional.of(s);
        }
        return Optional.empty();
    }
}
