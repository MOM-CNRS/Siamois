package fr.siamois.domain.services.exporttemplate;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.exporttemplate.ExportSubject;
import fr.siamois.domain.models.exporttemplate.ExportTechnicalSource;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.specimen.Specimen;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Registre fermé des navigations à cardinalité 1 qu'un chemin de modèle peut suivre (par exemple
 * {@code ["project"]} depuis une unité d'enregistrement). Le JSON ne porte que des noms ; ce que
 * chaque nom lit est défini ici, jamais dans le modèle.
 */
public final class ExportNavigations {

    /** Une navigation : l'entité qu'elle atteint, et comment la lire depuis l'entité de départ. */
    public record Navigation(ExportSubject target, Function<Object, Object> follow) {
    }

    private static final String PROJECT = "project";

    private static final Map<ExportSubject, Map<String, Navigation>> REGISTRY = Map.of(
            ExportSubject.RECORDING_UNIT, Map.of(
                    PROJECT, new Navigation(ExportSubject.PROJECT, e -> ((RecordingUnit) e).getActionUnit())),
            ExportSubject.SPECIMEN, Map.of(
                    PROJECT, new Navigation(ExportSubject.PROJECT, e -> ((Specimen) e).getActionUnit()),
                    "recordingUnit", new Navigation(ExportSubject.RECORDING_UNIT, e -> ((Specimen) e).getRecordingUnit())),
            ExportSubject.PHASE, Map.of(
                    PROJECT, new Navigation(ExportSubject.PROJECT, e -> ((Phase) e).getActionUnit())),
            ExportSubject.DOCUMENT, Map.of(
                    PROJECT, new Navigation(ExportSubject.PROJECT, e -> ((Document) e).getActionUnit())),
            ExportSubject.CONTAINER, Map.of(
                    PROJECT, new Navigation(ExportSubject.PROJECT, e -> ((Container) e).getActionUnit())),
            ExportSubject.PROJECT, Map.of(
                    "mainLocation", new Navigation(ExportSubject.SPATIAL_UNIT, e -> ((ActionUnit) e).getMainLocation()))
    );

    private ExportNavigations() {
        throw new UnsupportedOperationException();
    }

    public static Optional<Navigation> find(ExportSubject from, String name) {
        return Optional.ofNullable(REGISTRY.getOrDefault(from, Map.of()).get(name));
    }

    /** Noms de navigation disponibles depuis une entité (pour l'éditeur). */
    public static Set<String> names(ExportSubject from) {
        return REGISTRY.getOrDefault(from, Map.of()).keySet();
    }

    /**
     * Entité atteinte en suivant {@code path} depuis une entité.
     *
     * @throws IllegalArgumentException si un nom du chemin n'est pas une navigation connue
     */
    public static ExportSubject targetOf(ExportSubject from, Iterable<String> path) {
        ExportSubject current = from;
        for (String step : path) {
            Optional<Navigation> navigation = find(current, step);
            if (navigation.isEmpty()) {
                throw new IllegalArgumentException("Unknown navigation '" + step + "' from " + current);
            }
            current = navigation.get().target();
        }
        return current;
    }

    /**
     * Entité atteinte depuis une ligne d'une table technique : la première étape est une extrémité de la
     * ligne, les suivantes des navigations.
     *
     * @throws IllegalArgumentException si le chemin est vide ou contient un nom inconnu
     */
    public static ExportSubject targetOf(ExportTechnicalSource source, java.util.List<String> path) {
        if (path.isEmpty()) {
            throw new IllegalArgumentException("A path from " + source + " must start with one of " + source.ends().keySet());
        }
        ExportSubject end = source.ends().get(path.get(0));
        if (end == null) {
            throw new IllegalArgumentException("Unknown end '" + path.get(0) + "' of " + source);
        }
        return targetOf(end, path.subList(1, path.size()));
    }

    /**
     * Suit un chemin depuis une ligne. Renvoie vide dès qu'une étape tombe sur une valeur nulle (par
     * exemple un mobilier sans unité d'enregistrement).
     *
     * @throws IllegalArgumentException si un nom du chemin est inconnu depuis la ligne
     */
    public static Optional<ExportRow> follow(ExportRow row, java.util.List<String> path) {
        ExportRow current = row;
        int start = 0;
        if (current.isTechnical()) {
            if (path.isEmpty()) {
                throw new IllegalArgumentException("A path on a technical row must name one of its ends");
            }
            current = current.ends().get(path.get(0));
            if (current == null) {
                throw new IllegalArgumentException("Unknown end '" + path.get(0) + "'");
            }
            start = 1;
        }
        for (int i = start; i < path.size(); i++) {
            String step = path.get(i);
            ExportSubject subject = current.subject();
            Navigation navigation = find(subject, step)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown navigation '" + step + "' from " + subject));
            Object next = navigation.follow().apply(current.entity());
            if (next == null) {
                return Optional.empty();
            }
            current = ExportRow.of(navigation.target(), next);
        }
        return Optional.of(current);
    }
}
