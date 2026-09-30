package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.ui.table.definitions.SystemFieldCatalog;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.infrastructure.database.repositories.relation.RelationField;
import fr.siamois.ui.api.openapi.v1.resource.form.MultiValue;
import fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef;
import fr.siamois.ui.api.openapi.v1.resource.form.SelectManyFieldAnswer;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.ui.form.fieldsource.PanelFieldSource;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Gives every multi-valued answer of a response its API shape: at most {@code valuesLimit} values,
 * the total, whether that is all of them, and — when it isn't — where the rest is
 * ({@code /api/v1/{collection}/{id}/fields/{fieldId}/values}).
 * <p>
 * Two sources of values meet here. Most multi-valued answers arrive whole from their projector (a
 * list of {@link ResourceRef}s, or a {@link SelectManyFieldAnswer} envelope) and are simply cut.
 * A {@link RelationField} never arrives at all — no projector loads those collections — and is read
 * here, one query per relation for the whole page ({@link RelationFieldService#previews}).
 * <p>
 * Every list and detail that serves {@code answers} goes through this, so a multi-valued answer
 * has the same shape wherever it is read: a bare {@link MultiValue} where answers are raw values,
 * the same four properties on the {@link SelectManyFieldAnswer} envelope where they are wrapped.
 */
@Component
@RequiredArgsConstructor
public class MultiValueAnswers {

    private record Owner(String collection, java.util.Collection<CustomField> fields) {
    }

    private static final Map<Class<?>, Owner> OWNERS = Map.of(
            RecordingUnit.class, new Owner("recording-units", SystemFieldCatalog.sharedFieldsOf(ConfigurableTable.UE)),
            Specimen.class, new Owner("finds", SystemFieldCatalog.sharedFieldsOf(ConfigurableTable.MOBILIER)),
            Phase.class, new Owner("phases", SystemFieldCatalog.sharedFieldsOf(ConfigurableTable.PHASE)),
            Container.class, new Owner("containers", SystemFieldCatalog.sharedFieldsOf(ConfigurableTable.CONTENANT)),
            ActionUnit.class, new Owner("projects", new PanelFieldSource(ActionUnit.DETAILS_FORM).getAllFields()));

    /** Each owner's relation fields, by field id. */
    private static final Map<Class<?>, Map<String, RelationField>> RELATION_FIELDS = indexRelationFields();

    /** Each owner's system fields, by field id. */
    private static final Map<Class<?>, Map<String, CustomField>> SYSTEM_FIELDS = indexSystemFields();

    private final RelationFieldService relationFieldService;

    private static Map<Class<?>, Map<String, RelationField>> indexRelationFields() {
        Map<Class<?>, Map<String, RelationField>> out = new LinkedHashMap<>();
        OWNERS.forEach((type, owner) -> {
            Map<String, RelationField> byId = new LinkedHashMap<>();
            for (CustomField field : owner.fields()) {
                if (field == null || field.getId() == null) continue;
                RelationField.of(type, field.getValueBinding())
                        .ifPresent(relation -> byId.put(String.valueOf(field.getId()), relation));
            }
            out.put(type, Map.copyOf(byId));
        });
        return Map.copyOf(out);
    }

    private static Map<Class<?>, Map<String, CustomField>> indexSystemFields() {
        Map<Class<?>, Map<String, CustomField>> out = new LinkedHashMap<>();
        OWNERS.forEach((type, owner) -> {
            Map<String, CustomField> byId = new LinkedHashMap<>();
            for (CustomField field : owner.fields()) {
                if (field != null && field.getId() != null) byId.put(String.valueOf(field.getId()), field);
            }
            out.put(type, Map.copyOf(byId));
        });
        return Map.copyOf(out);
    }

    /** The system field {@code fieldId} is on {@code ownerType}'s details form, if it is one. */
    public static Optional<CustomField> systemFieldOf(Class<?> ownerType, String fieldId) {
        return Optional.ofNullable(SYSTEM_FIELDS.getOrDefault(ownerType, Map.of()).get(fieldId));
    }

    /** The relation field {@code fieldId} is on {@code ownerType}, if it is one. */
    public static Optional<RelationField> relationFieldOf(Class<?> ownerType, String fieldId) {
        return Optional.ofNullable(RELATION_FIELDS.getOrDefault(ownerType, Map.of()).get(fieldId));
    }

    /** The API collection {@code ownerType}'s resources are served under. */
    public static String collectionOf(Class<?> ownerType) {
        Owner owner = OWNERS.get(ownerType);
        if (owner == null) throw new IllegalArgumentException("No API collection for " + ownerType.getName());
        return owner.collection();
    }

    /** Where the full list of one answer is. */
    public static String valuesHref(Class<?> ownerType, Object ownerId, String fieldId) {
        return "/api/v1/" + collectionOf(ownerType) + "/" + ownerId + "/fields/" + fieldId + "/values";
    }

    /**
     * Shapes the answers of a page of {@code ownerType}s: every multi-valued answer cut to
     * {@code limit}, and every relation field among {@code fieldIds} read in.
     *
     * @param answersByOwner the page's answers, by owner id — left untouched
     * @param fieldIds       the fields the page projects (only those relation fields are read)
     * @return the shaped answers, by owner id, in the same order
     */
    public Map<Long, Map<String, Object>> shape(Class<?> ownerType, Map<Long, Map<String, Object>> answersByOwner,
                                                @Nullable Collection<String> fieldIds, int limit, String lang) {
        if (answersByOwner == null || answersByOwner.isEmpty()) return answersByOwner;
        Map<Long, Map<String, Object>> out = new LinkedHashMap<>();
        answersByOwner.forEach((ownerId, answers) -> out.put(ownerId, cut(ownerType, ownerId, answers, limit)));
        if (fieldIds == null) return out;

        Set<Long> ownerIds = answersByOwner.keySet();
        for (Map.Entry<String, RelationField> e : RELATION_FIELDS.getOrDefault(ownerType, Map.of()).entrySet()) {
            String fieldId = e.getKey();
            if (!fieldIds.contains(fieldId)) continue;
            Map<Long, MultiValue> previews = relationFieldService.previews(e.getValue(), ownerIds, limit, lang,
                    ownerId -> valuesHref(ownerType, ownerId, fieldId));
            previews.forEach((ownerId, preview) -> {
                Map<String, Object> answers = out.get(ownerId);
                if (answers != null) answers.put(fieldId, withValue(answers.get(fieldId), preview));
            });
        }
        return out;
    }

    /** {@link #shape} for a single owner — a detail. */
    public Map<String, Object> shapeOne(Class<?> ownerType, long ownerId, Map<String, Object> answers,
                                        @Nullable Collection<String> fieldIds, int limit, String lang) {
        Map<Long, Map<String, Object>> shaped = shape(ownerType, Map.of(ownerId, answers), fieldIds, limit, lang);
        return shaped.get(ownerId);
    }

    private static Map<String, Object> cut(Class<?> ownerType, Long ownerId, @Nullable Map<String, Object> answers, int limit) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (answers == null) return out;
        answers.forEach((fieldId, value) -> out.put(fieldId, cutOne(value, limit, valuesHref(ownerType, ownerId, fieldId))));
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Object cutOne(@Nullable Object value, int limit, String href) {
        if (value instanceof SelectManyFieldAnswer envelope) {
            List<ResourceRef> values = envelope.values() == null ? List.of() : envelope.values();
            return envelope.with(MultiValue.of(values, Math.max(envelope.total(), values.size()), limit, href));
        }
        if (value instanceof List<?> list && list.stream().allMatch(ResourceRef.class::isInstance)) {
            List<ResourceRef> values = (List<ResourceRef>) list;
            return MultiValue.of(values, values.size(), limit, href);
        }
        return value;
    }

    /** A relation's preview, in the shape the answer it replaces was in. */
    private static Object withValue(@Nullable Object current, MultiValue preview) {
        return current instanceof SelectManyFieldAnswer envelope ? envelope.with(preview) : preview;
    }
}
