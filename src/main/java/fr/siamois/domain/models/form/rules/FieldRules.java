package fr.siamois.domain.models.form.rules;

import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Règles conditionnelles portées par une colonne de formulaire.
 *
 * @param enabledWhen  le champ n'est éditable que si la condition est vraie (sinon grisé, lecture
 *                     seule ; une valeur déjà saisie est conservée et signalée incohérente)
 * @param requiredWhen le champ est requis si la condition est vraie (le {@code isRequired} statique
 *                     de la colonne reste « requis toujours »)
 * @param options      restriction des valeurs proposées selon un autre champ
 * @param constraints  contraintes d'ordre avec d'autres champs
 * @param placeSources sources de suggestions d'un champ lieu (INSEE, GéoPlateforme…) et leurs
 *                     paramètres, alimentés par la valeur d'autres champs lieu
 */
public record FieldRules(@Nullable Condition enabledWhen,
                         @Nullable Condition requiredWhen,
                         @Nullable OptionsFilter options,
                         List<FieldConstraint> constraints,
                         List<PlaceSourceSpec> placeSources) implements Serializable {

    public static final FieldRules NONE = new FieldRules(null, null, null, List.of(), List.of());

    public FieldRules {
        constraints = constraints == null ? List.of() : List.copyOf(constraints);
        placeSources = placeSources == null ? List.of() : List.copyOf(placeSources);
    }

    /** Rules with no place source. */
    public FieldRules(@Nullable Condition enabledWhen, @Nullable Condition requiredWhen,
                      @Nullable OptionsFilter options, List<FieldConstraint> constraints) {
        this(enabledWhen, requiredWhen, options, constraints, List.of());
    }

    public boolean isEmpty() {
        return enabledWhen == null && requiredWhen == null && options == null && constraints.isEmpty()
                && placeSources.isEmpty();
    }

    /** Tous les champs dont dépend l'état de ce champ. */
    public Set<Long> dependencies() {
        Set<Long> out = new LinkedHashSet<>();
        if (enabledWhen != null) out.addAll(enabledWhen.fieldIds());
        if (requiredWhen != null) out.addAll(requiredWhen.fieldIds());
        if (options != null) out.addAll(options.fieldIds());
        constraints.forEach(c -> out.add(c.fieldId()));
        placeSources.forEach(source -> out.addAll(source.fieldIds()));
        return out;
    }

    public FieldRules withEnabledWhen(@Nullable Condition condition) {
        return new FieldRules(condition, requiredWhen, options, constraints, placeSources);
    }

    public FieldRules withRequiredWhen(@Nullable Condition condition) {
        return new FieldRules(enabledWhen, condition, options, constraints, placeSources);
    }

    public FieldRules withOptions(@Nullable OptionsFilter filter) {
        return new FieldRules(enabledWhen, requiredWhen, filter, constraints, placeSources);
    }

    public FieldRules withConstraints(FieldConstraint... more) {
        return new FieldRules(enabledWhen, requiredWhen, options, List.of(more), placeSources);
    }

    public FieldRules withPlaceSources(PlaceSourceSpec... more) {
        return new FieldRules(enabledWhen, requiredWhen, options, constraints, List.of(more));
    }

    public FieldRules withPlaceSources(List<PlaceSourceSpec> sources) {
        return new FieldRules(enabledWhen, requiredWhen, options, constraints, sources);
    }
}
