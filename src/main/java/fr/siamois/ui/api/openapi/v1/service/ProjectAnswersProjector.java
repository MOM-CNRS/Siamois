package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDateTime;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDecimal;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectMultipleSpatialUnitTree;
import fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectOneSpatialUnit;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectMultipleFromFieldCode;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.services.vocabulary.ConceptLabelBatchResolver;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.SpatialUnitSummaryDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef;
import fr.siamois.ui.form.fieldsource.PanelFieldSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Projette les champs de formulaire d'un projet en une carte {@code answers} plate, pour une page entière.
 *
 * <p><strong>Pourquoi ce n'est pas le moteur de réponses.</strong> Les 33 champs de
 * {@code ActionUnitForm} sont tous des champs système ({@code isSystemField=true}) dont le
 * {@code valueBinding} pointe une propriété réelle de {@link ActionUnitDTO} : il n'existe aucune ligne
 * {@code CustomFieldAnswer} pour un projet ({@code FormService.loadAdditionalAnswers} ne traite que les
 * unités d'enregistrement). La projection est donc une simple lecture réflexive du DTO déjà chargé —
 * aucune requête par ligne, contrairement à {@code RecordingUnitOpenApiService.buildFieldsWithFallback}
 * qui appelle {@code initOrReuseResponse} par entité.</p>
 *
 * <p><strong>Forme du fil.</strong> Valeurs brutes, pas d'enveloppe {@code FieldAnswer} : celle-ci
 * embarque un {@code FieldResource} complet par réponse, soit 33 copies des métadonnées par ligne. Le
 * front récupère le catalogue une seule fois via {@code GET /api/v1/organizations/{id}/project-types} et
 * indexe par id de champ. Scalaire pour TEXT/INTEGER/DECIMAL/DATETIME, {@link ResourceRef} pour les
 * {@code SELECT_ONE_*}, liste de {@link ResourceRef} pour les {@code SELECT_MULTIPLE_*}.</p>
 */
@Slf4j
@Component
public class ProjectAnswersProjector {

    public static final String FIELDS_ALL = "all";
    public static final String FIELDS_DEFAULT = "default";

    private static final String CONCEPTS = "concepts";
    private static final String SPATIAL_UNITS = "spatial-units";

    /**
     * Champs du formulaire de détail projet, indexés par id sous forme de chaîne. Le formulaire est une
     * constante statique : l'index est calculé une fois.
     */
    private static final Map<String, CustomField> FIELDS_BY_ID = indexDetailsFormFields();

    /**
     * Champ du formulaire de détail projet par id, ou {@code null} si inconnu — la même source
     * qu'utilisent la lecture ({@link #project}) et l'écriture ({@code ProjectApiService.patchProject}
     * via {@code answers}), pour que les deux ne puissent jamais accepter des ids différents.
     */
    public static CustomField fieldById(String fieldId) {
        return FIELDS_BY_ID.get(fieldId);
    }

    private static Map<String, CustomField> indexDetailsFormFields() {
        Map<String, CustomField> out = new LinkedHashMap<>();
        for (CustomField field : new PanelFieldSource(ActionUnit.DETAILS_FORM).getAllFields()) {
            if (field != null && field.getId() != null) {
                out.put(String.valueOf(field.getId()), field);
            }
        }
        return Map.copyOf(out);
    }

    /**
     * Résout le paramètre {@code fields} en un ensemble ordonné d'ids de champs.
     *
     * @param fieldsParam {@code null}/vide → {@code null} (aucune projection, la réponse n'a pas de clé
     *                    {@code answers}), {@code all} → tout le catalogue, {@code default} → les colonnes
     *                    visibles par défaut ({@code ActionUnitTableColumnDefaults}), sinon une liste d'ids
     *                    séparés par des virgules
     * @return {@code null} si aucune projection n'est demandée ; les ids inconnus sont ignorés en silence
     *         (le catalogue peut évoluer, et une vue enregistrée d'un client ne doit pas devenir un 400)
     */
    public Set<String> resolveRequestedFieldIds(String fieldsParam) {
        if (fieldsParam == null || fieldsParam.isBlank()) {
            return null;
        }
        String value = fieldsParam.trim();
        if (FIELDS_ALL.equalsIgnoreCase(value)) {
            return FIELDS_BY_ID.keySet();
        }
        if (FIELDS_DEFAULT.equalsIgnoreCase(value)) {
            return fr.siamois.ui.table.definitions.ActionUnitTableColumnDefaults.defaultVisibleFieldIds();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String raw : value.split(",")) {
            String id = raw.trim();
            if (!id.isEmpty() && FIELDS_BY_ID.containsKey(id)) {
                out.add(id);
            }
        }
        return out;
    }

    /**
     * Concepts référencés par les champs demandés sur toute la page — à résoudre en un lot par l'appelant
     * ({@code ConceptLabelBatchResolver}) avant d'appeler {@link #project}. Séparé de la projection pour
     * qu'un seul lot couvre aussi les concepts que le mapper résout par ailleurs (le type du projet).
     */
    public List<ConceptDTO> collectConcepts(Collection<ActionUnitDTO> rows, Set<String> requestedFieldIds) {
        List<CustomField> fields = fieldsOf(requestedFieldIds);
        if (rows == null || fields.isEmpty()) {
            return List.of();
        }
        return collectConcepts(rows, fields);
    }

    /**
     * Projette les champs demandés pour toute une page.
     *
     * @param resolvedLabels libellés de concepts déjà résolus par lot (voir {@link #collectConcepts})
     * @return {@code answers} par id de projet ; carte vide si rien n'est demandé
     */
    public Map<Long, Map<String, Object>> project(Collection<ActionUnitDTO> rows,
                                                  Set<String> requestedFieldIds,
                                                  Map<Long, String> resolvedLabels) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        List<CustomField> fields = fieldsOf(requestedFieldIds);
        if (fields.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> labels = resolvedLabels == null ? Map.of() : resolvedLabels;

        Map<Long, Map<String, Object>> out = new LinkedHashMap<>();
        for (ActionUnitDTO row : rows) {
            if (row == null || row.getId() == null) continue;
            Map<String, Object> answers = new LinkedHashMap<>();
            for (CustomField field : fields) {
                answers.put(String.valueOf(field.getId()), toWireValue(field, readBinding(row, field), labels));
            }
            out.put(row.getId(), answers);
        }
        return out;
    }

    private static List<CustomField> fieldsOf(Set<String> requestedFieldIds) {
        if (requestedFieldIds == null || requestedFieldIds.isEmpty()) {
            return List.of();
        }
        return requestedFieldIds.stream()
                .map(FIELDS_BY_ID::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private static List<ConceptDTO> collectConcepts(Collection<ActionUnitDTO> rows, List<CustomField> fields) {
        List<ConceptDTO> concepts = new ArrayList<>();
        for (ActionUnitDTO row : rows) {
            if (row == null) continue;
            for (CustomField field : fields) {
                Object raw = readBinding(row, field);
                if (raw instanceof ConceptDTO c) {
                    concepts.add(c);
                } else if (raw instanceof Collection<?> items) {
                    for (Object item : items) {
                        if (item instanceof ConceptDTO c) concepts.add(c);
                    }
                }
            }
        }
        return concepts;
    }

    /**
     * Lit la propriété du DTO pointée par {@code valueBinding}. Toute erreur de lecture est un défaut de
     * configuration du champ, pas une erreur d'appel : on la journalise et on rend {@code null} plutôt que
     * de faire échouer toute la page.
     */
    private static Object readBinding(ActionUnitDTO row, CustomField field) {
        String binding = field.getValueBinding();
        if (binding == null || binding.isBlank()) {
            return null;
        }
        PropertyDescriptor descriptor = BeanUtils.getPropertyDescriptor(ActionUnitDTO.class, binding);
        Method getter = descriptor == null ? null : descriptor.getReadMethod();
        if (getter == null) {
            log.warn("valueBinding '{}' du champ {} sans propriété correspondante sur ActionUnitDTO",
                    binding, field.getId());
            return null;
        }
        try {
            return getter.invoke(row);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            log.warn("Lecture impossible du binding '{}' (champ {}): {}", binding, field.getId(), ex.toString());
            return null;
        }
    }

    private static Object toWireValue(CustomField field, Object raw, Map<Long, String> labels) {
        if (raw == null) {
            return null;
        }
        if (field instanceof CustomFieldText) {
            return String.valueOf(raw);
        }
        if (field instanceof CustomFieldInteger) {
            return raw instanceof Number n ? n.intValue() : null;
        }
        if (field instanceof CustomFieldDecimal) {
            return raw instanceof Number n ? n.doubleValue() : null;
        }
        if (field instanceof CustomFieldDateTime) {
            return raw;
        }
        if (field instanceof CustomFieldSelectOneFromFieldCode) {
            return raw instanceof ConceptDTO c ? conceptRef(c, labels) : null;
        }
        if (field instanceof CustomFieldSelectMultipleFromFieldCode) {
            return refList(raw, item -> item instanceof ConceptDTO c ? conceptRef(c, labels) : null);
        }
        if (field instanceof CustomFieldSelectOneSpatialUnit) {
            return raw instanceof SpatialUnitSummaryDTO s ? spatialUnitRef(s) : null;
        }
        if (field instanceof CustomFieldSelectMultipleSpatialUnitTree) {
            return refList(raw, item -> item instanceof SpatialUnitSummaryDTO s ? spatialUnitRef(s) : null);
        }
        log.debug("Type de champ non projeté pour la liste projets: {}", field.getClass().getSimpleName());
        return null;
    }

    private static List<ResourceRef> refList(Object raw, java.util.function.Function<Object, ResourceRef> mapper) {
        Collection<?> items = raw instanceof Collection<?> c ? c : List.of(raw);
        List<ResourceRef> out = new ArrayList<>(items.size());
        for (Object item : items) {
            ResourceRef ref = mapper.apply(item);
            if (ref != null) out.add(ref);
        }
        return out;
    }

    private static ResourceRef conceptRef(ConceptDTO concept, Map<Long, String> labels) {
        return new ResourceRef(String.valueOf(concept.getId()), CONCEPTS,
                ConceptLabelBatchResolver.labelOf(concept, labels));
    }

    private static ResourceRef spatialUnitRef(SpatialUnitSummaryDTO su) {
        return new ResourceRef(String.valueOf(su.getId()), SPATIAL_UNITS, su.getName());
    }
}
