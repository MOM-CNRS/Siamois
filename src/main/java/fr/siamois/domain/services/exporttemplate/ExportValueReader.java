package fr.siamois.domain.services.exporttemplate;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.exporttemplate.ExportSubject;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.ColumnField;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.ConceptField;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.FieldRef;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.services.form.CustomFieldAnswerService;
import fr.siamois.domain.services.form.CustomFieldAnswerService.ListOwner;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.BeansException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lit la valeur brute d'un champ sur une ligne d'export : propriété de l'entité pour un champ système
 * (par son {@code valueBinding}), réponse enregistrée pour un champ additionnel. La valeur est
 * renvoyée telle quelle (concept, personne, nombre…) : la mise en forme en cellule est l'affaire du
 * moteur. Une valeur absente donne une liste vide.
 */
@Service
@RequiredArgsConstructor
public class ExportValueReader {

    /** Réponses additionnelles préchargées pour toutes les lignes d'une feuille : propriétaire → champ → réponse. */
    public record AnswerCache(Map<Long, Map<CustomField, CustomFieldAnswerViewModel>> byOwnerId) {
        public static final AnswerCache EMPTY = new AnswerCache(Map.of());
    }

    private final CustomFieldAnswerService customFieldAnswerService;

    /** Charge en une requête les réponses de champs additionnels des entités d'une feuille. */
    @Transactional(readOnly = true)
    public AnswerCache prefetch(ExportSubject subject, Collection<ExportRow> rows, Collection<CustomField> fields) {
        ListOwner owner = ownerOf(subject);
        Set<Long> fieldIds = fields.stream()
                .filter(f -> !isSystem(f) && f.getId() != null)
                .map(CustomField::getId)
                .collect(Collectors.toSet());
        if (owner == null || fieldIds.isEmpty() || rows.isEmpty()) {
            return AnswerCache.EMPTY;
        }
        List<Long> ids = rows.stream().map(r -> idOf(r.entity())).filter(Objects::nonNull).toList();
        return new AnswerCache(customFieldAnswerService.loadAdditionalFieldAnswers(owner, ids, fieldIds));
    }

    /**
     * Valeurs d'un champ sur une ligne. Les valeurs multiples sont aplaties ; pour plusieurs champs
     * candidats (un concept partagé par plusieurs champs additionnels), le premier qui a une valeur gagne.
     */
    public List<Object> read(ExportRow row, FieldRef field, List<CustomField> candidates, AnswerCache cache) {
        if (field instanceof ColumnField column) {
            return flatten(row.values().get(column.name()));
        }
        if (!(field instanceof ConceptField) || row.entity() == null) {
            return List.of();
        }
        for (CustomField candidate : candidates) {
            List<Object> values = flatten(isSystem(candidate)
                    ? systemValue(row.entity(), candidate)
                    : additionalValue(row, candidate, cache));
            if (!values.isEmpty()) {
                return values;
            }
        }
        return List.of();
    }

    @Nullable
    private static Object systemValue(Object entity, CustomField field) {
        String binding = field.getValueBinding();
        if (binding == null || binding.isBlank()) {
            return null;
        }
        try {
            return new BeanWrapperImpl(entity).getPropertyValue(binding);
        } catch (BeansException e) {
            // Le champ n'est pas une propriété lisible de cette entité : pas de valeur.
            return null;
        }
    }

    @Nullable
    private static Object additionalValue(ExportRow row, CustomField field, AnswerCache cache) {
        Long id = idOf(row.entity());
        if (id == null) {
            return null;
        }
        return Optional.ofNullable(cache.byOwnerId().get(id))
                .map(answers -> answers.get(field))
                .map(CustomFieldAnswerViewModel::getValue)
                .orElse(null);
    }

    private static List<Object> flatten(@Nullable Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?> collection) {
            return new ArrayList<>(collection.stream().filter(Objects::nonNull).toList());
        }
        return List.of(value);
    }

    private static boolean isSystem(CustomField field) {
        return Boolean.TRUE.equals(field.getIsSystemField());
    }

    @Nullable
    private static ListOwner ownerOf(ExportSubject subject) {
        return switch (subject) {
            case RECORDING_UNIT -> ListOwner.RECORDING_UNIT;
            case SPECIMEN -> ListOwner.SPECIMEN;
            case PHASE -> ListOwner.PHASE;
            case CONTAINER -> ListOwner.CONTAINER;
            case DOCUMENT -> ListOwner.DOCUMENT;
            case PROJECT, SPATIAL_UNIT -> null;
        };
    }

    @Nullable
    private static Long idOf(@Nullable Object entity) {
        if (entity instanceof RecordingUnit e) return e.getId();
        if (entity instanceof Specimen e) return e.getId();
        if (entity instanceof Phase e) return e.getId();
        if (entity instanceof Container e) return e.getId();
        if (entity instanceof Document e) return e.getId();
        if (entity instanceof ActionUnit e) return e.getId();
        if (entity instanceof SpatialUnit e) return e.getId();
        return null;
    }
}
