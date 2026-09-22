package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.actionunit.CustomFieldSelectOneActionUnit;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDateTime;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfield.person.CustomFieldSelectMultiplePerson;
import fr.siamois.domain.models.form.customfield.person.CustomFieldSelectOnePerson;
import fr.siamois.domain.models.form.customfield.phase.CustomFieldSelectMultiplePhase;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldMeasurement;
import fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectOneSpatialUnit;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.services.vocabulary.ConceptLabelBatchResolver;
import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.MeasurementAnswerDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.SpatialUnitSummaryDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.ui.api.openapi.v1.resource.form.MeasurementRef;
import fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef;
import fr.siamois.ui.form.fieldsource.PanelFieldSource;
import fr.siamois.ui.table.definitions.RecordingUnitTableColumnDefaults;
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
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Projette les champs de formulaire d'une unité d'enregistrement en une carte {@code answers}
 * plate, pour une page entière — le pendant, pour la liste UE, de {@link ProjectAnswersProjector}.
 *
 * <p><strong>Pourquoi les valeurs sont brutes, pas l'enveloppe {@link fr.siamois.ui.api.openapi.v1.resource.form.FieldAnswer}
 * que sert le détail.</strong> Chaque colonne de {@code RecordingUnitTableDefinitionFactory} est un
 * champ système ({@code systemField(ConfigurableTable.UE, binding)}) dont le {@code valueBinding}
 * pointe une propriété réelle de {@link RecordingUnitDTO} — la même lecture réflexive que le
 * projecteur projet suffit donc, et cette classe ne touche jamais
 * {@code RecordingUnitOpenApiService#buildFieldsWithFallback}, qui appelle {@code initOrReuseResponse}
 * par entité et fait tout le coût de l'endpoint de détail.</p>
 *
 * <p><strong>Indexation sur le formulaire système, pas sur le formulaire effectif par type.</strong>
 * Un projet peut avoir plusieurs types d'UE actifs à la fois dans une même liste ; les ids de
 * {@link RecordingUnit#DETAILS_FORM} sont stables et indépendants du type, contrairement au
 * formulaire effectif (par-type, avec champs désactivés) que sert le catalogue de colonnes
 * ({@code GET /api/v1/projects/{id}/recording-unit-types}). C'est ce dernier qui décide de ce que
 * le sélecteur de colonnes PROPOSE ; un id demandé absent d'ici projette simplement {@code null},
 * même politique d'ignorance silencieuse que {@link #resolveRequestedFieldIds}.</p>
 *
 * <p><strong>{@code isPartOf}/{@code contains} ({@code parents}/{@code children}) ne sont pas
 * projetés ici.</strong> Sur cette liste ({@code includeFullRelations=false}), ces collections ne
 * sont jamais chargées sur le DTO — seuls {@code parentsCount}/{@code childrenCount} le sont. React
 * les sert depuis {@code _counts}, en colonnes épinglées, pas depuis {@code answers} ; leur entrée
 * dans le catalogue de colonnes reste togglable côté JSF (qui, lui, charge les collections
 * complètes) mais ne produira aucune valeur dans cette projection.</p>
 */
@Slf4j
@Component
public class RecordingUnitAnswersProjector {

    public static final String FIELDS_ALL = "all";
    public static final String FIELDS_DEFAULT = "default";

    private static final String CONCEPTS = "concepts";
    private static final String PERSONS = "persons";
    private static final String ACTION_UNITS = "action-units";
    private static final String SPATIAL_UNITS = "spatial-units";
    private static final String PHASES = "phases";

    private static final Map<String, CustomField> FIELDS_BY_ID = indexDetailsFormFields();

    public static CustomField fieldById(String fieldId) {
        return FIELDS_BY_ID.get(fieldId);
    }

    private static Map<String, CustomField> indexDetailsFormFields() {
        Map<String, CustomField> out = new LinkedHashMap<>();
        for (CustomField field : new PanelFieldSource(RecordingUnit.DETAILS_FORM).getAllFields()) {
            if (field != null && field.getId() != null) {
                out.put(String.valueOf(field.getId()), field);
            }
        }
        return Map.copyOf(out);
    }

    /**
     * Résout le paramètre {@code fields} en un ensemble ordonné d'ids de champs — même contrat que
     * {@link ProjectAnswersProjector#resolveRequestedFieldIds}.
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
            return RecordingUnitTableColumnDefaults.defaultVisibleFieldIds();
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
     * Concepts référencés par les champs demandés sur toute la page — à résoudre en un lot par
     * l'appelant avant d'appeler {@link #project}.
     */
    public List<ConceptDTO> collectConcepts(Collection<RecordingUnitDTO> rows, Set<String> requestedFieldIds) {
        List<CustomField> fields = fieldsOf(requestedFieldIds);
        if (rows == null || fields.isEmpty()) {
            return List.of();
        }
        List<ConceptDTO> concepts = new ArrayList<>();
        for (RecordingUnitDTO row : rows) {
            if (row == null) continue;
            for (CustomField field : fields) {
                Object raw = readBinding(row, field);
                if (raw instanceof ConceptDTO c) {
                    concepts.add(c);
                }
            }
        }
        return concepts;
    }

    /**
     * Projette les champs demandés pour toute une page.
     *
     * @param resolvedLabels libellés de concepts déjà résolus par lot (voir {@link #collectConcepts})
     * @return {@code answers} par id d'UE ; carte vide si rien n'est demandé
     */
    public Map<Long, Map<String, Object>> project(Collection<RecordingUnitDTO> rows,
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
        for (RecordingUnitDTO row : rows) {
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
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Lit la propriété du DTO pointée par {@code valueBinding}. {@code parents}/{@code children} ne
     * sont volontairement jamais lus ici (voir la javadoc de la classe) : sur cette liste, ils ne
     * sont jamais chargés ({@code includeFullRelations=false}), donc toujours {@code null} — pas la
     * peine d'un accès réflexif pour ça.
     */
    private static Object readBinding(RecordingUnitDTO row, CustomField field) {
        String binding = field.getValueBinding();
        if (binding == null || binding.isBlank() || "parents".equals(binding) || "children".equals(binding)) {
            return null;
        }
        PropertyDescriptor descriptor = BeanUtils.getPropertyDescriptor(RecordingUnitDTO.class, binding);
        Method getter = descriptor == null ? null : descriptor.getReadMethod();
        if (getter == null) {
            log.warn("valueBinding '{}' du champ {} sans propriété correspondante sur RecordingUnitDTO",
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
        if (field instanceof CustomFieldDateTime) {
            return raw;
        }
        if (field instanceof CustomFieldSelectOneFromFieldCode) {
            return raw instanceof ConceptDTO c ? conceptRef(c, labels) : null;
        }
        if (field instanceof CustomFieldSelectOnePerson) {
            return raw instanceof PersonDTO p ? personRef(p) : null;
        }
        if (field instanceof CustomFieldSelectMultiplePerson) {
            return refList(raw, item -> item instanceof PersonDTO p ? personRef(p) : null);
        }
        if (field instanceof CustomFieldSelectOneActionUnit) {
            return raw instanceof ActionUnitSummaryDTO au ? actionUnitRef(au) : null;
        }
        if (field instanceof CustomFieldSelectOneSpatialUnit) {
            return raw instanceof SpatialUnitSummaryDTO s ? spatialUnitRef(s) : null;
        }
        if (field instanceof CustomFieldSelectMultiplePhase) {
            return refList(raw, item -> item instanceof PhaseDTO p ? phaseRef(p) : null);
        }
        if (field instanceof CustomFieldMeasurement) {
            return raw instanceof MeasurementAnswerDTO m ? measurementRef(m) : null;
        }
        log.debug("Type de champ non projeté pour la liste UE: {}", field.getClass().getSimpleName());
        return null;
    }

    private static List<ResourceRef> refList(Object raw, Function<Object, ResourceRef> mapper) {
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

    private static ResourceRef personRef(PersonDTO person) {
        return new ResourceRef(String.valueOf(person.getId()), PERSONS, person.displayName());
    }

    private static ResourceRef actionUnitRef(ActionUnitSummaryDTO actionUnit) {
        return new ResourceRef(String.valueOf(actionUnit.getId()), ACTION_UNITS,
                actionUnit.getFullIdentifier() != null ? actionUnit.getFullIdentifier() : actionUnit.getName());
    }

    private static ResourceRef spatialUnitRef(SpatialUnitSummaryDTO su) {
        return new ResourceRef(String.valueOf(su.getId()), SPATIAL_UNITS, su.getName());
    }

    private static ResourceRef phaseRef(PhaseDTO phase) {
        String label = phase.getTitle() != null && !phase.getTitle().isBlank() ? phase.getTitle() : phase.getIdentifier();
        return new ResourceRef(String.valueOf(phase.getId()), PHASES, label);
    }

    private static MeasurementRef measurementRef(MeasurementAnswerDTO measurement) {
        String symbol = measurement.getUnit() != null ? measurement.getUnit().getSymbol() : null;
        return new MeasurementRef(measurement.getNumericValue(), symbol, measurement.getNormalizedValue(), measurement.getComment());
    }
}
